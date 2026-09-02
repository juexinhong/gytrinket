package com.gytrinket.gytrinket.core.explosion;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 弹射物次级爆炸处理器
 * <p>
 * 机制分两步：
 * <ol>
 *   <li><b>伤害标记</b>：归属玩家的弹射物（{@link Projectile}）对实体造成伤害时，以持久化 NBT
 *       标记该弹射物并记录伤害值；同一弹射物多次造成伤害时只计入最高的一次</li>
 *   <li><b>移除爆炸</b>：弹射物从世界移除时（{@link EntityLeaveLevelEvent}，命中消失/消亡/
 *       被拾取等），若携带标记，则在记录的命中点产生模拟爆炸</li>
 * </ol>
 * <p>
 * 基础爆炸参数：
 * <ul>
 *   <li>爆心：命中点（最高伤害那次命中时受击者的位置）。枪械等模组的自定义弹道弹射物
 *       实体位置不随弹道前进（移除时可能仍停在发射点），因此不可用弹射物位置作爆心</li>
 *   <li>爆炸伤害：弹射物已记录的最高伤害 × 伤害比例（config 可配置，默认 15%）</li>
 *   <li>爆炸半径：基础值 + 爆炸伤害 × 每点伤害半径增量（config 可配置，默认 2 格 + 0.5 格/点）</li>
 *   <li>无击退效果（击退倍率覆盖为 0）</li>
 * </ul>
 * 以上为基础爆炸参数，由 {@link SimulatedExplosion} 在 owner 非 null 时
 * 自动应用爆炸伤害（explosion_damage）与爆炸半径（explosion_radius）属性组再增幅。
 * <p>
 * 标记判定条件（全部满足才标记弹射物）：
 * <ul>
 *   <li>伤害类型不是爆炸（{@link DamageTypeTags#IS_EXPLOSION}）：爆炸伤害不属于弹射物本体伤害，
 *       且防止爆炸伤害再次标记造成连锁</li>
 *   <li>直接伤害来源是弹射物（{@link Projectile}）</li>
 *   <li>不是护盾系统转发的最终伤害（FINAL_DAMAGE）：转发伤害非弹射物本体伤害，避免重复标记</li>
 *   <li>伤害归属服务端玩家：{@link DamageSource#getEntity()} 为服务端玩家</li>
 * </ul>
 * <p>
 * 标记使用 {@link EventPriority#LOWEST} 最后执行，确保
 * {@link com.gytrinket.gytrinket.event.IndirectDamageHandler}（默认优先级）先完成
 * 伤害属性增幅后，记录增幅后的最终弹射物伤害。
 * <p>
 * 生效门槛（数据驱动）：弹射物归属玩家需在光点核心或饰品栏装备声明了
 * {@code projectile_explosion_items} 特殊机制集合的物品
 * （默认为爆炸半径模块：special_mechanics/explosion_radius_module.json）；
 * 玩家离线或未装备指定物品时不产生爆炸。
 * <p>
 * 爆炸伤害经 mergeType="secondary_explosion" 接入次级伤害合并系统，
 * 同一实体在时间窗口内受到的多次爆炸伤害会累积合并后施加。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class SecondaryExplosionHandler {

    /** 次级爆炸伤害合并类型 */
    private static final String MERGE_TYPE = "secondary_explosion";
    /** NBT 键：弹射物已记录的最高伤害 */
    private static final String NBT_KEY_DAMAGE = "gytrinket:projectile_explosion_damage";
    /** NBT 键：弹射物归属玩家 UUID */
    private static final String NBT_KEY_OWNER = "gytrinket:projectile_explosion_owner";
    /** NBT 键：命中点 X（最高伤害那次命中时受击者位置，作为爆心） */
    private static final String NBT_KEY_HIT_X = "gytrinket:projectile_explosion_hit_x";
    /** NBT 键：命中点 Y */
    private static final String NBT_KEY_HIT_Y = "gytrinket:projectile_explosion_hit_y";
    /** NBT 键：命中点 Z */
    private static final String NBT_KEY_HIT_Z = "gytrinket:projectile_explosion_hit_z";

    /**
     * 伤害标记：归属玩家的弹射物造成伤害时，NBT 标记弹射物并记录最高伤害
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();

        // 爆炸伤害不属于弹射物本体伤害，且防止连锁标记
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        // 仅处理弹射物造成的伤害
        if (!(source.getDirectEntity() instanceof Projectile projectile)) {
            return;
        }

        // 护盾系统转发的最终伤害非弹射物本体伤害，避免重复标记
        if (source.typeHolder().unwrapKey().orElse(null) == ModDamageTypes.FINAL_DAMAGE) {
            return;
        }

        // 仅处理归属服务端玩家的弹射物
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag data = projectile.getPersistentData();

        // 记录归属玩家（首次标记时固定）
        if (!data.hasUUID(NBT_KEY_OWNER)) {
            data.putUUID(NBT_KEY_OWNER, player.getUUID());
        }

        // 多次造成伤害只计入最高的一次（同时记录该次命中点 = 受击者位置：
        // 枪械等模组的自定义弹道弹射物实体位置不随弹道前进，不可用作爆心）
        float amount = event.getAmount();
        if (amount > data.getFloat(NBT_KEY_DAMAGE)) {
            data.putFloat(NBT_KEY_DAMAGE, amount);
            data.putDouble(NBT_KEY_HIT_X, event.getEntity().getX());
            data.putDouble(NBT_KEY_HIT_Y, event.getEntity().getY() + event.getEntity().getBbHeight() / 2.0);
            data.putDouble(NBT_KEY_HIT_Z, event.getEntity().getZ());
        }
    }

    /**
     * 移除爆炸：携带标记的弹射物从世界移除时，在记录的命中点产生模拟爆炸
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        // 仅服务端处理
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 仅处理弹射物
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        CompoundTag data = projectile.getPersistentData();

        // 未携带标记（未造成过归属玩家的伤害）则不爆炸
        if (!data.hasUUID(NBT_KEY_OWNER)) {
            return;
        }

        float recordedDamage = data.getFloat(NBT_KEY_DAMAGE);
        if (recordedDamage <= 0) {
            return;
        }

        // 解析归属玩家
        ServerPlayer resolvedOwner = null;
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            resolvedOwner = serverLevel.getServer().getPlayerList().getPlayer(data.getUUID(NBT_KEY_OWNER));
        }
        final ServerPlayer owner = resolvedOwner;

        // 生效门槛：归属玩家离线（无法验证物品）或未装备指定物品时不爆炸
        if (owner == null || !hasRequiredItem(owner)) {
            return;
        }

        // 基础爆炸参数（config secondary_explosion 节可配置）；之后由 SimulatedExplosion 应用属性组增幅
        float explosionDamage = (float) (recordedDamage * Config.SECONDARY_EXPLOSION_DAMAGE_FRACTION.get());
        double explosionRadius = Config.SECONDARY_EXPLOSION_RADIUS_BASE.get()
                + explosionDamage * Config.SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION.get();

        // 爆心 = 记录的命中点（最高伤害那次命中时受击者的位置），而非弹射物移除位置
        Vec3 explosionPos = new Vec3(
                data.getDouble(NBT_KEY_HIT_X),
                data.getDouble(NBT_KEY_HIT_Y),
                data.getDouble(NBT_KEY_HIT_Z));

        // owner 非 null 时 SimulatedExplosion 自动应用爆炸伤害/爆炸半径属性组增幅；击退倍率 0 = 无击退
        SimulatedExplosion.execute(
                event.getLevel(),
                explosionPos,
                explosionRadius,
                explosionDamage,
                event.getLevel().damageSources().explosion(null, owner),
                e -> e != owner && e.isAlive(),
                true,
                owner,
                0.0,
                MERGE_TYPE
        );
    }

    /**
     * 物品门槛判定：玩家已装备物品（光点核心存储 + Curios 饰品栏）中，
     * 有通过 special_mechanics 数据驱动声明 {@code projectile_explosion_items}
     * 特殊机制集合的物品才生效（含运行时覆盖与物品禁用检查）
     */
    private static boolean hasRequiredItem(ServerPlayer owner) {
        return DefsManager.playerHasEquippedMechanic(owner.getServer(), owner.getUUID(), "projectile_explosion_items");
    }
}
