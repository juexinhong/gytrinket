package com.gytrinket.gytrinket.core.ghost_fuselage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackEvent;
import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幽灵机身管理器
 * <p>
 * 幽灵机身是机身物品，仅在光点核心内时生效。
 * <p>
 * 机制：
 * <ul>
 *   <li>玩家持续进入隐身状态（不算为原版隐身），需要2秒达到完全隐身</li>
 *   <li>随着隐身进度增加，持续获得动态独立乘区玩家伤害属性加成</li>
 *   <li>达到完全隐身时，属性值到达+300%（受光点等级提升上限）</li>
 *   <li>高速移动、部署构造体、左键攻击（含空挥）、右键使用物品都会降低隐身进度</li>
 *   <li>玩家光点等级每级增加0.5%隐身速度和最大属性上限</li>
 * </ul>
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class GhostFuselageManager {

    private static final String NAMESPACE = "ghost_fuselage";
    private static final String ATTR_DAMAGE_INDEPENDENT = "attack_damage_independent";

    /** 隐身进度上限（80% = 完全隐身） */
    private static final double STEALTH_CAP = 0.8;

    /** 拥有幽灵机身能力的玩家集合 */
    private static final Set<UUID> PLAYER_HAS_GHOST = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 玩家隐身进度数据 (0.0 ~ 1.0) */
    private static final Map<UUID, GhostData> PLAYER_GHOST_DATA = new ConcurrentHashMap<>();

    /** 客户端同步的移动隐身消耗量（客户端计算好的值） */
    private static final Map<UUID, Float> SYNCED_MOVE_REDUCTION = new ConcurrentHashMap<>();

    private GhostFuselageManager() {}

    /**
     * 接收客户端同步的移动隐身消耗量
     */
    public static void setSyncedMoveReduction(UUID playerUUID, float reduction) {
        if (reduction < 0.0001f) {
            SYNCED_MOVE_REDUCTION.remove(playerUUID);
        } else {
            SYNCED_MOVE_REDUCTION.put(playerUUID, reduction);
        }
    }

    /**
     * 判断玩家是否拥有幽灵机身能力
     */
    public static boolean hasGhostFuselage(Player player) {
        return PLAYER_HAS_GHOST.contains(player.getUUID());
    }

    /**
     * 设置玩家是否拥有幽灵机身能力
     */
    public static void setHasGhostFuselage(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_GHOST.add(playerUUID);
        } else {
            PLAYER_HAS_GHOST.remove(playerUUID);
            PLAYER_GHOST_DATA.remove(playerUUID);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
        }
    }

    /**
     * 获取玩家当前隐身进度
     */
    public static double getStealthProgress(UUID playerUUID) {
        GhostData data = PLAYER_GHOST_DATA.get(playerUUID);
        return data != null ? data.progress : 0;
    }

    /**
     * 判断玩家是否处于完全隐身状态（供Mixin调用）
     */
    public static boolean isFullyStealthed(Player player) {
        if (!PLAYER_HAS_GHOST.contains(player.getUUID())) {
            return false;
        }
        GhostData data = PLAYER_GHOST_DATA.get(player.getUUID());
        return data != null && data.progress >= STEALTH_CAP;
    }

    /**
     * 直接降低隐身进度（供外部调用，如构造体部署时）
     */
    public static void reduceProgress(UUID playerUUID, double amount) {
        GhostData data = PLAYER_GHOST_DATA.get(playerUUID);
        if (data != null) {
            data.progress = Math.max(0, data.progress - amount);
            data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
            updateDamageAttribute(playerUUID, data.progress);
        }
    }

    /**
     * 玩家tick逻辑：处理隐身进度增减和属性更新
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!hasGhostFuselage(player)) {
            return;
        }

        int modLevel = Math.max(0, ModLevelManager.getModLevel(uuid));

        GhostData data = PLAYER_GHOST_DATA.computeIfAbsent(uuid, k -> new GhostData());

        // 递减破隐冷却
        if (data.breakCooldown > 0) {
            data.breakCooldown--;
        }

        // 计算隐身速度加成：基础速度 × (1 + level × 0.005)
        double stealthSpeedMultiplier = 1.0 + modLevel * Config.getGhostFuselageStealthSpeedBonusPerLevel();
        // 每tick增加进度 = STEALTH_CAP / fullStealthTicks
        int fullStealthTicks = Config.getGhostFuselageFullStealthTicks();
        double progressIncreasePerTick = (STEALTH_CAP / fullStealthTicks) * stealthSpeedMultiplier;

        // 破隐冷却期间不累加隐身进度
        if (data.breakCooldown <= 0) {
            data.progress = Math.min(1.0, data.progress + progressIncreasePerTick);
        }

        // 使用客户端同步的移动隐身消耗量（客户端已计算好）
        double moveReduction = SYNCED_MOVE_REDUCTION.getOrDefault(uuid, 0f);
        if (moveReduction > 0) {
            data.progress = Math.max(0, data.progress - moveReduction);
            data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
        }

        // 右键使用物品持续扣除隐身进度
        if (player.isUsingItem()) {
            data.progress = Math.max(0, data.progress - Config.getGhostFuselageUseItemReductionPerTick());
            data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
        }

        // 充能攻击充能/释放由 ChargedAttackEvent 事件处理，此处不再直接调用

        // 更新伤害属性
        updateDamageAttribute(uuid, data.progress);

        // 完全隐身时设置原版invisible标签（供渲染/其他系统识别）
        // 目标选取排除由 TargetingConditionsMixin 处理
        if (data.progress >= STEALTH_CAP) {
            player.setInvisible(true);
            // 首次进入完全隐身时清理一次仇恨
            if (!data.wasFullyStealthed) {
                clearMobAggro(player);
                data.wasFullyStealthed = true;
            }
        } else {
            // 恢复时避免覆盖药水隐身
            if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                player.setInvisible(false);
            }
            data.wasFullyStealthed = false;
        }

        // 同步隐身进度到客户端
        setPlayerVisibility(player, data.progress);
    }

    /**
     * 左键攻击实体降低隐身进度（仅服务端处理）
     * <p>
     * AttackEntityEvent 在 NeoForge 1.21.1 中客户端和服务端都会触发，
     * 必须加侧检查避免双降低。
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        handleLeftClickAttack(event.getEntity());
    }

    /**
     * 右键使用物品降低隐身进度（仅服务端处理）
     * <p>
     * 任何物品右键都触发一次大量扣除（与左键攻击一致），
     * 若该物品还能持续使用（食物、弓等），tick中 isUsingItem() 还会每刻缓慢扣除。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getItemStack().isEmpty()) {
            return;
        }
        handleLeftClickAttack(event.getEntity());
    }

    /**
     * 客户端通知空挥扣除隐身进度（由网络包调用）
     */
    public static void onClientSwingAttack(ServerPlayer player) {
        handleLeftClickAttack(player);
    }

    private static void handleLeftClickAttack(Player player) {
        UUID uuid = player.getUUID();
        if (!PLAYER_HAS_GHOST.contains(uuid)) {
            return;
        }
        GhostData data = PLAYER_GHOST_DATA.get(uuid);
        if (data != null) {
            data.progress = Math.max(0, data.progress - Config.getGhostFuselageAttackReduction());
            data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
            // 不在此处立即调用 updateDamageAttribute：
            // AttackEntityEvent 在 Player.attack() 伤害计算之前触发，
            // 若立即更新修饰符会导致本次攻击伤害按降低后的进度计算。
            // 延迟到下一tick的 onPlayerTick 中更新，确保本次攻击使用满隐身伤害。
        }
    }

    /**
     * 充能攻击事件：充能期间每刻扣除隐身进度，释放时按攻击扣除
     */
    @SubscribeEvent
    public static void onChargedAttackEvent(ChargedAttackEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUUID();
        if (!PLAYER_HAS_GHOST.contains(uuid)) {
            return;
        }
        GhostData data = PLAYER_GHOST_DATA.get(uuid);
        if (data == null) {
            return;
        }

        switch (event.getType()) {
            case CHARGING:
                // 充能期间每刻扣除（与使用物品消减一致）
                data.progress = Math.max(0, data.progress - Config.getGhostFuselageUseItemReductionPerTick());
                data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
                updateDamageAttribute(uuid, data.progress);
                break;
            case RELEASED:
                // 释放时按攻击扣除（与左键攻击一致）
                data.progress = Math.max(0, data.progress - Config.getGhostFuselageAttackReduction());
                data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
                // 不立即更新修饰符，原因同 handleLeftClickAttack：
                // 释放事件在伤害计算之前触发，延迟到下一tick更新。
                break;
        }
    }

    /**
     * 构造体加入世界时，降低玩家隐身进度（部署扣除）
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractConstructEntity)) {
            return;
        }
        if (event.getLevel().getServer() == null) {
            return;
        }

        // 获取构造体的拥有者
        UUID ownerUUID = ((AbstractConstructEntity) entity).getOwnerUUID();
        if (ownerUUID == null || !PLAYER_HAS_GHOST.contains(ownerUUID)) {
            return;
        }

        GhostData data = PLAYER_GHOST_DATA.get(ownerUUID);
        if (data != null) {
            data.progress = Math.max(0, data.progress - Config.getGhostFuselageDeployReduction());
            data.breakCooldown = Config.getGhostFuselageBreakCooldownTicks();
            updateDamageAttribute(ownerUUID, data.progress);
        }
    }

    /**
     * 更新动态伤害属性
     */
    private static void updateDamageAttribute(UUID playerUUID, double progress) {
        int modLevel = Math.max(0, ModLevelManager.getModLevel(playerUUID));
        double maxDamageBonus = Config.getGhostFuselageBaseMaxDamageBonus() * (1.0 + modLevel * Config.getGhostFuselageMaxBonusPerLevel());
        // progress归一化到0~1范围（STEALTH_CAP=0.8对应满伤害）
        double normalizedProgress = Math.min(progress, STEALTH_CAP) / STEALTH_CAP;
        double currentBonus = normalizedProgress * maxDamageBonus;
        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, ATTR_DAMAGE_INDEPENDENT, currentBonus);
    }

    /**
     * 设置玩家可见度（自定义隐身，不算为原版隐身）
     * <p>
     * 通过网络包同步隐身进度到客户端，由客户端渲染器处理视觉隐身效果。
     */
    private static void setPlayerVisibility(ServerPlayer player, double progress) {
        GhostFuselageSyncHelper.sendStealthProgress(player, progress);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_HAS_GHOST.remove(uuid);
        PLAYER_GHOST_DATA.remove(uuid);
        SYNCED_MOVE_REDUCTION.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_GHOST_DATA.remove(uuid);
        SYNCED_MOVE_REDUCTION.remove(uuid);
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
    }

    public static void clearAllData() {
        PLAYER_HAS_GHOST.clear();
        PLAYER_GHOST_DATA.clear();
        SYNCED_MOVE_REDUCTION.clear();
    }

    /**
     * 检查玩家光点核心是否拥有幽灵机身物品
     */
    public static boolean hasGhostFuselageInStore(UUID playerUUID) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isGhostFuselageItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 清除周围以该玩家为目标的怪物仇恨（仅清除一次）
     */
    private static void clearMobAggro(ServerPlayer player) {
        var targetingCondition = TargetingConditions.forCombat().ignoreLineOfSight()
            .selector(e -> ((Mob) e).getTarget() == player);
        player.level().getNearbyEntities(Mob.class, targetingCondition, player, player.getBoundingBox().inflate(40D))
            .forEach(mob -> {
                mob.setTarget(null);
                mob.targetSelector.getAvailableGoals().forEach(WrappedGoal::stop);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            });
    }

    /**
     * 幽灵机身数据 - 每个玩家的隐身进度状态
     */
    private static class GhostData {
        /** 隐身进度 0.0 ~ 1.0 */
        double progress;
        /** 破隐冷却（tick），进度被扣除时设为5，冷却期间不累加隐身进度 */
        int breakCooldown;
        /** 上一tick是否处于完全隐身状态（用于首次进入时清理仇恨） */
        boolean wasFullyStealthed;

        GhostData() {
            this.progress = 0;
            this.breakCooldown = 0;
            this.wasFullyStealthed = false;
        }
    }
}
