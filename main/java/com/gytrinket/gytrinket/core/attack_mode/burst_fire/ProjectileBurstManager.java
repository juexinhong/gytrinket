package com.gytrinket.gytrinket.core.attack_mode.burst_fire;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.projectile.ProjectileBlacklist;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹射物点射管理器
 * <p>
 * 核心流程：
 * 1. 归属玩家的弹射物加入世界（手动使用触发；排除区块加载与复制体）
 * 2. 记录弹射物模板（完整 NBT 快照 + 初始速度）
 * 3. 触发时对冷却物品一次性挂满冷却（连击复制期 + 攻击冷却全程禁用玩家使用该物品）
 * 4. 每 1 刻按玩家当前视线复制同样的弹射物加入世界，直到连击段数耗尽
 * 5. 连击结束进入攻击冷却（由物品冷却剩余时长自然倒计时，冷却时长按触发时有效攻速计算：
 *    与近战点射同一攻速逻辑，自动叠加玩家身上所有攻速修饰符）
 * 6. 复制弹射物命中时重置目标无敌时间（避免原弹射物赋予的无敌帧吞掉复制体的伤害）
 * 7. 复制弹射物产生一次碰撞后 1 刻移除（避免滞留世界被拾取进背包）
 * <p>
 * 启用条件：玩家的 combo 属性 > 0
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ProjectileBurstManager {

    // 延迟时间：1刻 = 0.05秒
    private static final int BURST_DELAY_TICKS = 1;

    // 复制体标记键：防止复制体加入世界时再次触发点射
    private static final String TAG_BURST_COPY = "ProjectileBurstCopy";

    // 存储玩家弹射物复制状态：UUID -> 复制状态（模板NBT/剩余复制数/初始速度）
    private static final Map<UUID, BurstCopyState> ACTIVE = new ConcurrentHashMap<>();

    // 存储玩家复制延迟计时器：UUID -> 剩余延迟刻数
    private static final Map<UUID, Integer> COPY_DELAY = new ConcurrentHashMap<>();

    // 存储玩家弹射物点射的冷却物品：UUID -> 冷却物品
    // （连击复制期与攻击冷却全程挂在该物品上，记录物品用于跨系统冷却状态查询，如充能启动拦截）
    private static final Map<UUID, Item> COOLDOWN_ITEMS = new ConcurrentHashMap<>();

    /**
     * 弹射物复制状态
     * <p>
     * fieldSnapshot：弹射物实际类自定义字段快照。
     * 部分模组弹射物（如 TACZ 动能子弹）实体类型声明 noSave 且未实现 NBT 存取，
     * 弹道参数只存在于内存字段，仅靠 NBT 快照无法还原（复制体会因字段缺失在
     * 客户端同步包编码时崩溃），复制前以反射记录，还原实体后回填。
     */
    private record BurstCopyState(CompoundTag template, Map<String, Object> fieldSnapshot, int remainingCopies, float launchSpeed) {
    }

    /**
     * 获取玩家的连击段数加成
     */
    private static int getComboStacksBonus(Player player) {
        double combo = AttributeManager.getPlayerAttribute(player.getUUID(), "combo");
        return (int) Math.floor(combo);
    }

    /**
     * 监听弹射物加入世界事件
     * 归属玩家的弹射物首次加入世界时，记录模板并启动连击复制循环
     * <p>
     * 时序说明：使用 HIGH 优先级，先于 ProjectileDamageHandler（NORMAL）执行，
     * 确保模板在伤害增幅前记录原始伤害值；复制体加入世界时再由其正常增幅一次，
     * 避免模板携带已增幅的 damage 值导致复制体双重增幅
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 区块加载恢复的弹射物不触发
        if (event.loadedFromDisk()) {
            return;
        }

        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        // 复制体不再次触发
        if (projectile.getPersistentData().getBoolean(TAG_BURST_COPY)) {
            return;
        }

        // 钓鱼浮标无连击意义
        if (projectile instanceof FishingHook) {
            return;
        }

        // 弹射物黑名单（末影珍珠等）：不参与点射复制
        if (ProjectileBlacklist.isBlacklisted(projectile)) {
            return;
        }

        // 仅处理归属玩家的弹射物
        if (!(projectile.getOwner() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 已在复制循环中，不重复触发
        if (ACTIVE.containsKey(playerUUID)) {
            return;
        }

        // 检查玩家的连击段数是否大于0
        int comboStacksBonus = getComboStacksBonus(player);
        if (comboStacksBonus <= 0) {
            return;
        }

        // 记录模板：完整 NBT 快照（含弹射物类型与初始数据）
        CompoundTag template = new CompoundTag();
        template.putString("id", EntityType.getKey(projectile.getType()).toString());
        projectile.saveWithoutId(template);
        // 移除 UUID：复制体必须使用新 UUID，否则与原弹射物冲突，
        // addFreshEntity 会因 UUID 已存在而静默失败（实体加入世界失败，客户端不可见）
        template.remove("UUID");
        // 自定义字段快照：noSave 类弹射物（TACZ 动能子弹等）的弹道参数不入 NBT，必须反射记录
        Map<String, Object> fieldSnapshot = captureCustomFieldSnapshot(projectile);
        float launchSpeed = (float) projectile.getDeltaMovement().length();

        // 冷却物品：弹射物对应物品优先（三叉戟掷出后离手/自带物品的弹射物），否则取主手物品
        Item cooldownItem = resolveCooldownItem(player, projectile);

        // 冷却总时长 = 连击复制期 + 攻击冷却，触发时一次性挂满：
        // 连击与攻击冷却全程禁用玩家使用该物品（物品冷却转圈），同时防止循环期间重入
        int comboDurationTicks = BURST_DELAY_TICKS * comboStacksBonus;
        int totalComboStacks = 1 + comboStacksBonus;
        // 攻击冷却攻速与近战点射同一逻辑：借用属性系统临时施加修正值后读取
        // （弹射物触发时主手为弹射物物品/空手，自动落到默认修正值，同时叠加玩家所有攻速修饰符）
        double attackSpeed = BurstFireManager.captureEffectiveAttackSpeed(player);
        int attackCooldownTicks = BurstFireManager.calcComboCooldownTicks(totalComboStacks, attackSpeed);
        player.getCooldowns().addCooldown(cooldownItem, comboDurationTicks + attackCooldownTicks);
        COOLDOWN_ITEMS.put(playerUUID, cooldownItem);

        // 启动复制循环：首次复制延迟与点射一致
        ACTIVE.put(playerUUID, new BurstCopyState(template, fieldSnapshot, comboStacksBonus, launchSpeed));
        COPY_DELAY.put(playerUUID, BURST_DELAY_TICKS);
    }

    /**
     * 获取弹射物对应的物品（仅用于冷却物品解析）
     * 三叉戟返回三叉戟物品（掷出后离手，必须从弹射物本身取得），
     * 自带物品的弹射物（雪球/鸡蛋/末影珍珠等）返回其物品，
     * 箭类等无对应手持物品的弹射物返回null
     */
    private static Item resolveProjectileItem(Projectile projectile) {
        if (projectile instanceof ThrownTrident) {
            return Items.TRIDENT;
        }
        if (projectile instanceof ThrowableItemProjectile throwable) {
            return throwable.getItem().getItem();
        }
        return null;
    }

    /**
     * 确定冷却物品：弹射物对应物品优先，否则取主手物品
     */
    private static Item resolveCooldownItem(ServerPlayer player, Projectile projectile) {
        Item projectileItem = resolveProjectileItem(projectile);
        return projectileItem != null ? projectileItem : player.getMainHandItem().getItem();
    }

    /**
     * 玩家是否处于弹射物点射的冷却中（连击复制期与攻击冷却全程）：
     * 冷却挂在触发时记录的冷却物品上，按物品冷却状态实时查询
     * （供 AttackModeManager 拦截点射冷却期间开始充能等跨系统协调使用）
     */
    public static boolean isInProjectileBurstCooldown(Player player) {
        Item cooldownItem = COOLDOWN_ITEMS.get(player.getUUID());
        return cooldownItem != null && player.getCooldowns().isOnCooldown(cooldownItem);
    }

    /**
     * 监听复制弹射物的伤害事件
     * <p>
     * 原弹射物命中会赋予目标原版无敌帧（invulnerableTime = 20），
     * 复制弹射物紧随其后命中时伤害会被无敌帧吞掉，因此在伤害结算前重置目标无敌时间
     * <p>
     * 使用 LivingIncomingDamageEvent（而非 LivingDamageEvent.Pre）：
     * 该事件在 LivingEntity.hurt() 的无敌帧检查之前触发，
     * 可以在无敌帧拦截伤害之前将 invulnerableTime 重置为0
     */
    @SubscribeEvent
    public static void onCopiedProjectileDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }

        // 仅处理复制弹射物（原弹射物正常赋予目标无敌帧）
        if (!projectile.getPersistentData().getBoolean(TAG_BURST_COPY)) {
            return;
        }

        LivingEntity target = event.getEntity();
        target.invulnerableTime = 0;
    }

    /**
     * 监听复制弹射物的碰撞事件
     * <p>
     * 复制弹射物产生一次碰撞（实体或方块）后 1 刻直接移除：
     * 碰撞结算正常进行（伤害/插在方块上），下一刻移除复制体，
     * 避免其滞留世界被拾取进背包（包括忠诚附魔三叉戟的飞回拾取）
     */
    @SubscribeEvent
    public static void onCopiedProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        // 仅处理复制弹射物；事件被取消表示允许穿透，不视为完成碰撞
        if (event.isCanceled() || !projectile.getPersistentData().getBoolean(TAG_BURST_COPY)) {
            return;
        }

        Level level = projectile.level();
        if (level.isClientSide()) {
            return;
        }

        // 排定 1 刻后的移除任务
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (projectile.isAlive()) {
                projectile.discard();
            }
        }));
    }

    /**
     * 监听玩家每刻更新事件
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        if (!ACTIVE.containsKey(playerUUID)) {
            return;
        }

        // 玩家已死亡（兜底保护，死亡事件通常已清理）
        if (!player.isAlive()) {
            ACTIVE.remove(playerUUID);
            COPY_DELAY.remove(playerUUID);
            return;
        }

        handleCopyLoop(player);
    }

    /**
     * 处理复制循环逻辑
     */
    private static void handleCopyLoop(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        BurstCopyState state = ACTIVE.get(playerUUID);
        if (state == null) {
            return;
        }

        if (state.remainingCopies() <= 0) {
            ACTIVE.remove(playerUUID);
            COPY_DELAY.remove(playerUUID);
            return;
        }

        int delay = COPY_DELAY.getOrDefault(playerUUID, 0);
        if (delay > 0) {
            COPY_DELAY.put(playerUUID, delay - 1);
            return;
        }

        copyProjectile(player, state);

        int remaining = state.remainingCopies() - 1;
        if (remaining > 0) {
            ACTIVE.put(playerUUID, new BurstCopyState(state.template(), state.fieldSnapshot(), remaining, state.launchSpeed()));
            COPY_DELAY.put(playerUUID, BURST_DELAY_TICKS);
        } else {
            // 连击段数耗尽，结束复制循环（攻击冷却由物品冷却继续倒计时）
            ACTIVE.remove(playerUUID);
            COPY_DELAY.remove(playerUUID);
        }
    }

    /**
     * 复制一发弹射物：
     * 从模板 NBT 还原弹射物 → 打上复制体标记 → 移到玩家眼位 →
     * 按玩家当前视线方向与记录的初始速度发射 → 加入世界
     */
    private static void copyProjectile(ServerPlayer player, BurstCopyState state) {
        Level level = player.level();

        Entity copy = EntityType.loadEntityRecursive(state.template().copy(), level, e -> e);
        if (!(copy instanceof Projectile projectileCopy)) {
            return;
        }

        // 回填自定义字段快照（noSave 类弹射物的弹道参数不入 NBT，NBT 还原后缺失，必须反射回填）
        applyCustomFieldSnapshot(copy, state.fieldSnapshot());

        // 打上复制体标记，防止复制体加入世界时再次触发点射
        projectileCopy.getPersistentData().putBoolean(TAG_BURST_COPY, true);

        // 拾取控制：箭/三叉戟等拾取型弹射物的复制体禁止被拾取
        // （原弹射物被拾取属于正常回收，复制体被拾取进背包则等于凭空复制物品）
        if (projectileCopy instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }

        projectileCopy.setOwner(player);
        projectileCopy.setPos(player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z);
        projectileCopy.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, state.launchSpeed(), 1.0F);
        level.addFreshEntity(projectileCopy);
    }

    /**
     * 捕获弹射物实际类自定义字段快照（从实际类向上遍历，直到原版父类为止）
     * <p>
     * 部分模组弹射物（如 TACZ 动能子弹）实体类型声明 noSave 且未实现 NBT 存取，
     * 弹道参数（弹药/枪械ID、伤害序列、爆炸配置等）只存在于内存字段，
     * NBT 快照还原出的实体这些字段全部缺失，加入世界生成客户端同步包时崩溃。
     */
    private static Map<String, Object> captureCustomFieldSnapshot(Entity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Class<?> clazz = entity.getClass(); clazz != null && !clazz.getName().startsWith("net.minecraft."); clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    snapshot.put(field.getName(), field.get(entity));
                } catch (Throwable ignored) {
                    // 单字段读取失败不影响其余字段
                }
            }
        }
        return snapshot;
    }

    /**
     * 将自定义字段快照回填到复制体
     * <p>
     * 集合类型字段做拷贝（如 TACZ 子弹的距离伤害序列命中时会逐段消费，
     * 复制体与原弹射物共享同一集合会互相污染伤害衰减），其余引用类型直接共享。
     */
    private static void applyCustomFieldSnapshot(Entity target, Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Class<?> clazz = target.getClass(); clazz != null && !clazz.getName().startsWith("net.minecraft."); clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (!snapshot.containsKey(field.getName())) {
                    continue;
                }
                Object value = snapshot.get(field.getName());
                if (value instanceof Collection<?> collection) {
                    value = copyCollection(collection);
                }
                try {
                    field.setAccessible(true);
                    field.set(target, value);
                } catch (Throwable ignored) {
                    // 单字段写入失败不影响其余字段
                }
            }
        }
    }

    /**
     * 按原集合的具体类型浅拷贝集合（LinkedList/ArrayList/HashSet 等），无法拷贝时共享引用（只读集合安全）
     */
    private static Collection<?> copyCollection(Collection<?> source) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Collection<?>> type = (Class<? extends Collection<?>>) source.getClass();
            return type.getConstructor(Collection.class).newInstance(source);
        } catch (Throwable ignored) {
            return source;
        }
    }

    /**
     * 清理玩家所有状态
     */
    private static void cleanupPlayerState(UUID playerUUID) {
        ACTIVE.remove(playerUUID);
        COPY_DELAY.remove(playerUUID);
        COOLDOWN_ITEMS.remove(playerUUID);
    }

    /**
     * 监听玩家退出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }

        cleanupPlayerState(event.getEntity().getUUID());
    }

    /**
     * 监听玩家死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }

        cleanupPlayerState(event.getEntity().getUUID());
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        ACTIVE.clear();
        COPY_DELAY.clear();
        COOLDOWN_ITEMS.clear();
    }
}
