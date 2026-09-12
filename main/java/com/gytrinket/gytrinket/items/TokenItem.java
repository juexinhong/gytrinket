package com.gytrinket.gytrinket.items;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 点点光辉（代币）物品。
 *
 * 掉落物特殊机制：
 * <ul>
 *   <li>销毁免疫：掉落物仅可被"无视无敌"类伤害销毁（虚空跌落、/kill），
 *       火焰、岩浆、仙人掌、爆炸（原版伤害体系）等一切常规销毁途径全部免疫</li>
 *   <li>破土上浮：头顶任意高度（不限短距离）只要存在非空气、非流体的方块，
 *       便垂直上浮，穿透途中一切方块；速度按百分比增长——初始 0.02 格/tick，每 0.5 秒提升 30%，
 *       增长区间内线性插值平滑过渡；直到物品上缘之上全为空气才停止并恢复原版掉落物物理</li>
 *   <li>漂浮可拾取：上浮穿透期间主动吸附身边玩家（掉落 2 秒内不吸附，防止丢出即吸回）</li>
 *   <li>延长寿命：掉落物存在时间拉到引擎上限（≈27 分钟），远超原版 5 分钟</li>
 * </ul>
 */
public class TokenItem extends Item {

    /** 加速间隔：每 10 tick（0.5 秒）速度升一档 */
    private static final long ACCEL_INTERVAL_TICKS = 10L;
    /** 初始上浮速度：0.02 格/tick */
    private static final double INITIAL_FLOAT_SPEED = 0.02D;
    /** 每档增速系数：每 10 tick（0.5 秒）速度 ×1.3（即提升 30%） */
    private static final double GROWTH_PER_STEP = 1.3D;
    /** 丢弃保护：掉落后 40 tick 内不吸附，防止玩家 Q 丢出当 tick 吸回 */
    private static final long PICKUP_GRACE_TICKS = 40L;
    /** 吸附半径 */
    private static final double ATTRACT_RADIUS = 1.5D;
    /**
     * 掉落时刻记录（仅服务端 tick 线程访问）：按掉落物实体弱引用暂存，绝不写入 ItemStack NBT——
     * 带自定义 NBT 的 token 一旦进背包便无法与其他 token 堆叠，且上浮途中引擎拾取不走我们的清理。
     * 实体被拾取/销毁后条目由弱引用自动回收；跨存档重载后实体重建，仅重新计 2 秒保护期，无害。
     */
    private static final Map<ItemEntity, Long> DROP_TICKS = new WeakHashMap<>();
    /**
     * 开始上浮的 game time（双端各自记录，按实体弱引用回收）：当前速度直接由经过时间推导，
     * 无条目即未在上浮中，被埋后重新开始加速时从初始速度起步。
     */
    private static final Map<ItemEntity, Long> FLOAT_START_TICKS = new WeakHashMap<>();

    public TokenItem(Properties properties) {
        super(properties);
    }

    /**
     * 掉落物伤害免疫判定（ItemEntity#hurt 每次受伤都会询问物品，返回 false 即免疫）。
     * 仅保留"无视无敌"伤害（虚空 out_of_world、/kill generic_kill）可以销毁，
     * 火焰/岩浆（IS_FIRE）、仙人掌、爆炸、摔落等全部免疫。
     */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * 掉落物寿命（ItemEntity 构造时调用一次）：拉到引擎上限。
     * 原版 age 是 short（32767 溢出会变负导致隐式永生），引擎允许的最大安全值即
     * Short.MAX_VALUE - 1 = 32766 tick ≈ 27.3 分钟（过期段自身也 clamp 到这个值）。
     * 注意：上浮接管期间跳过原版 tick，age 暂停计数——被埋的物品不计时，露面后才开始走。
     */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Short.MAX_VALUE - 1;
    }

    /**
     * 掉落物自定义 tick（ItemEntity#tick 第一行调用，返回 true 跳过全部原版逻辑）。
     * 头顶有方块遮挡时接管移动：吸附附近玩家并缓慢上浮（穿透方块），
     * 无遮挡则交还原版物理（重力、碰撞、合并、拾取、过期）。
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (!hasBlockAbove(level, entity)) {
            // 头顶露空，上浮结束：清除起点，下次被埋时从 0 重新加速，交还原版物理
            FLOAT_START_TICKS.remove(entity);
            return false;
        }
        if (!level.isClientSide) {
            attractNearbyPlayers(entity, level);
        }
        // 百分比增速 + 线性插值：初速 0.02 格/tick，每 10 tick（0.5 秒）×1.3（+30%），
        // 档位目标速度 = 初速 × 1.3^n，实际速度在档位区间内按经过进度线性插值，全程无跳变；
        // 由"开始上浮时刻"直接推导，双端独立计算结果天然一致；
        // 停止判定仍每 tick 进行，避免高速时 0.5 秒的检查间隔冲出方块顶过远
        long now = level.getGameTime();
        long startTick = FLOAT_START_TICKS.computeIfAbsent(entity, k -> now);
        long elapsed = now - startTick;
        long steps = elapsed / ACCEL_INTERVAL_TICKS;
        double frac = (elapsed % ACCEL_INTERVAL_TICKS) / (double) ACCEL_INTERVAL_TICKS;
        double speed = INITIAL_FLOAT_SPEED * Math.pow(GROWTH_PER_STEP, steps)
                * (1.0D + (GROWTH_PER_STEP - 1.0D) * frac);
        entity.setDeltaMovement(Vec3.ZERO);
        // 手动推进旧坐标，保证客户端渲染插值平滑（跳过原版 tick 后无人更新 xo/yo/zo）
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        // 直接 setPosition：不经过 move() 的碰撞检测，实现穿透方块上浮
        entity.setPos(entity.getX(), entity.getY() + speed, entity.getZ());
        return true;
    }

    /**
     * 吸附拾取（仅服务端）：上浮穿透期间物品碰撞箱在方块内，玩家正常走近碰不到，
     * 主动把附近玩家拉入 playerTouch（内部自检背包容量，满了留在地上）。
     */
    private static void attractNearbyPlayers(ItemEntity entity, Level level) {
        long dropTick = DROP_TICKS.computeIfAbsent(entity, k -> level.getGameTime());
        if (level.getGameTime() - dropTick < PICKUP_GRACE_TICKS) {
            return; // 刚丢出 2 秒内不吸附
        }
        // 上浮接管跳过了原版 tick 的 pickupDelay 递减（且无公开 getter 可手动递减），吸附前直接清零
        if (entity.hasPickUpDelay()) {
            entity.setPickUpDelay(0);
        }
        List<Player> players = level.getEntitiesOfClass(Player.class,
                entity.getBoundingBox().inflate(ATTRACT_RADIUS), p -> p.isAlive() && !p.isSpectator());
        for (Player player : players) {
            entity.playerTouch(player);
        }
    }

    /**
     * 检测物品上缘之上、直到世界最高处是否存在非空气且非流体的方块。
     * 用 WORLD_SURFACE 高度图做粗筛：高度图值之上必然全为空气，
     * 因此只需逐格扫描 [物品上缘, 高度图) 的狭窄区间，而不是整个世界高度。
     */
    private static boolean hasBlockAbove(Level level, ItemEntity entity) {
        int x = entity.getBlockX();
        int z = entity.getBlockZ();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int fromY = Mth.floor(entity.getBoundingBox().maxY);
        if (fromY >= surfaceY) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int y = fromY; y < surfaceY; y++) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            // 流体（水/岩浆）不算遮挡：物品在液体中交还原版物理，由液体浮力自行上浮
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
