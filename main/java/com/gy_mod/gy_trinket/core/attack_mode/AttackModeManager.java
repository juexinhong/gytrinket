package com.gy_mod.gy_trinket.core.attack_mode;

import com.gy_mod.gy_trinket.core.attack_mode.assault.AssaultManager;
import com.gy_mod.gy_trinket.core.attack_mode.burst_fire.BurstFireManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击模式管理器 - 策略模式
 * <p>
 * 根据启用的攻击模式组合选择对应的攻击行为策略。
 * 严格规定什么时候使用什么攻击行为，避免各系统自行判定导致冲突。
 * <p>
 * 组合策略：
 * <ul>
 *   <li>点射：点射</li>
 *   <li>强袭：强袭</li>
 *   <li>充能：充能</li>
 *   <li>点射+强袭：强袭攻击后触发点射。点射期间和冷却期间不触发强袭。点射自动攻击触发强袭效果。</li>
 *   <li>点射+充能：充能攻击后触发一次点射（点射不会自主触发）。</li>
 *   <li>强袭+充能：充能期间以攻击速度频率触发强袭效果。</li>
 *   <li>点射+充能+强袭：充能期间以攻击速度频率触发强袭效果。充能攻击后触发一次点射（点射不会自主触发）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackModeManager {

    /** 攻击模式组合枚举 */
    public enum AttackModeCombo {
        NONE,                   // 无攻击模式
        ONLY_BURST,             // 仅点射
        ONLY_ASSAULT,           // 仅强袭
        ONLY_CHARGED,           // 仅充能
        BURST_ASSAULT,          // 点射+强袭
        BURST_CHARGED,          // 点射+充能
        ASSAULT_CHARGED,        // 强袭+充能
        BURST_ASSAULT_CHARGED   // 点射+充能+强袭
    }

    /** 每个玩家的攻击模式数据 */
    private static final Map<UUID, PlayerAttackModes> PLAYER_MODES = new ConcurrentHashMap<>();

    /** 充能期间强袭触发冷却 */
    private static final Map<UUID, Integer> CHARGE_ASSAULT_COOLDOWN = new ConcurrentHashMap<>();

    /** 标记充能释放后需要触发点射 */
    private static final Map<UUID, Boolean> PENDING_BURST_FROM_CHARGED = new ConcurrentHashMap<>();

    /** 记录本tick已触发电能释放的玩家（避免重复触发） */
    private static final Set<UUID> ELECTRIC_TRIGGERED_THIS_TICK = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 记录本tick玩家是否进行了正常攻击（用于挥空检测） */
    private static final Set<UUID> PLAYER_ATTACKED_THIS_TICK = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 记录上一tick玩家的攻击强度（用于检测挥空攻击） */
    private static final Map<UUID, Float> PREVIOUS_ATTACK_STRENGTH = new ConcurrentHashMap<>();

    private AttackModeManager() {}

    // ===== 攻击模式数据 =====

    public static class PlayerAttackModes {
        public boolean hasBurstFire;
        public boolean hasAssault;
        public boolean hasChargedAttack;
        public boolean hasElectricDischarge;
    }

    public static PlayerAttackModes getPlayerModes(UUID uuid) {
        return PLAYER_MODES.getOrDefault(uuid, new PlayerAttackModes());
    }

    public static void updatePlayerModes(UUID uuid, boolean hasBurst, boolean hasAssault, boolean hasCharged, boolean hasElectric) {
        PlayerAttackModes modes = PLAYER_MODES.computeIfAbsent(uuid, k -> new PlayerAttackModes());
        modes.hasBurstFire = hasBurst;
        modes.hasAssault = hasAssault;
        modes.hasChargedAttack = hasCharged;
        modes.hasElectricDischarge = hasElectric;
    }

    // ===== 组合计算 =====

    /**
     * 根据玩家当前启用的攻击模式计算组合
     */
    public static AttackModeCombo getCombo(UUID uuid) {
        PlayerAttackModes modes = getPlayerModes(uuid);
        return computeCombo(modes.hasBurstFire, modes.hasAssault, modes.hasChargedAttack);
    }

    /**
     * 根据启用的攻击模式计算组合
     */
    public static AttackModeCombo computeCombo(boolean hasBurst, boolean hasAssault, boolean hasCharged) {
        if (hasBurst && hasAssault && hasCharged) return AttackModeCombo.BURST_ASSAULT_CHARGED;
        if (hasBurst && hasAssault) return AttackModeCombo.BURST_ASSAULT;
        if (hasBurst && hasCharged) return AttackModeCombo.BURST_CHARGED;
        if (hasAssault && hasCharged) return AttackModeCombo.ASSAULT_CHARGED;
        if (hasBurst) return AttackModeCombo.ONLY_BURST;
        if (hasAssault) return AttackModeCombo.ONLY_ASSAULT;
        if (hasCharged) return AttackModeCombo.ONLY_CHARGED;
        return AttackModeCombo.NONE;
    }

    // ===== 策略查询方法 =====

    /**
     * 点射是否可以由玩家正常攻击自主触发。
     * 在有充能攻击的组合中，点射不会自主触发，只能由充能释放后触发。
     */
    public static boolean canBurstFireTriggerFromNormalAttack(UUID uuid) {
        AttackModeCombo combo = getCombo(uuid);
        return combo == AttackModeCombo.ONLY_BURST || combo == AttackModeCombo.BURST_ASSAULT;
    }

    /**
     * 点射是否可以由充能释放后触发
     */
    public static boolean canBurstFireTriggerFromChargedRelease(AttackModeCombo combo) {
        return combo == AttackModeCombo.BURST_CHARGED || combo == AttackModeCombo.BURST_ASSAULT_CHARGED;
    }

    /**
     * 点射自动攻击是否触发强袭效果。
     * 仅在点射+强袭组合中，点射自动攻击触发强袭。
     */
    public static boolean doesBurstFireAutoAttackTriggerAssault(AttackModeCombo combo) {
        return combo == AttackModeCombo.BURST_ASSAULT;
    }

    /**
     * 强袭攻击是否触发点射。
     * 仅在点射+强袭组合中，强袭攻击触发点射。
     */
    public static boolean doesAssaultAttackTriggerBurstFire(AttackModeCombo combo) {
        return combo == AttackModeCombo.BURST_ASSAULT;
    }

    /**
     * 充能期间是否以攻击速度频率触发强袭效果
     */
    public static boolean doesAssaultTriggerDuringCharging(AttackModeCombo combo) {
        return combo == AttackModeCombo.ASSAULT_CHARGED || combo == AttackModeCombo.BURST_ASSAULT_CHARGED;
    }

    /**
     * 点射期间或冷却期间，强袭自动攻击是否被禁用。
     * 仅在点射+强袭组合中生效。
     */
    public static boolean isAssaultAutoAttackDisabled(Player player) {
        AttackModeCombo combo = getCombo(player.getUUID());
        if (combo != AttackModeCombo.BURST_ASSAULT) {
            return false;
        }
        return BurstFireManager.isInBurstFireState(player) || BurstFireManager.isInComboCooldown(player.getUUID());
    }

    // ===== 充能释放后点射触发 =====

    /** 充能释放后是否需要触发点射 */
    public static boolean hasPendingBurstFromCharged(UUID uuid) {
        return PENDING_BURST_FROM_CHARGED.getOrDefault(uuid, false);
    }

    /** 消费充能释放后的点射触发标记 */
    public static boolean consumePendingBurstFromCharged(UUID uuid) {
        Boolean flag = PENDING_BURST_FROM_CHARGED.remove(uuid);
        return flag != null && flag;
    }

    // ===== 回调方法 =====

    /**
     * 点射自动攻击命中后调用。
     * 根据策略决定是否触发强袭和电能释放。
     */
    public static void onBurstFireAutoAttack(ServerPlayer player, LivingEntity target) {
        UUID uuid = player.getUUID();
        AttackModeCombo combo = getCombo(uuid);

        // 点射+强袭：点射自动攻击触发强袭
        if (doesBurstFireAutoAttackTriggerAssault(combo)) {
            AssaultManager.triggerAssault(player);
        }

        // 电能释放：自动攻击也能触发
        PlayerAttackModes modes = getPlayerModes(uuid);
        if (modes.hasElectricDischarge) {
            ElectricDischargeManager.releaseElectric(player);
        }
    }

    // ===== 事件处理 =====

    /**
     * 攻击事件处理 - HIGHEST优先级
     * <p>
     * 核心逻辑：充能期间禁用正常攻击。
     * 当玩家正在充能中时，正常攻击一律取消。
     * 只有以下情况允许攻击通过：
     * 1. 充能释放攻击（chargeValue > 0）
     * 2. 点射自动攻击（IS_AUTO_ATTACKING = true）
     * <p>
     * 未在充能中时，正常攻击允许通过（包括强袭攻击）。
     * 充能的启动由客户端 ChargedAttackMessage(0) 直接触发，不依赖攻击事件。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        // 攻击锁定：仅取消对 LivingEntity 的攻击，非 LivingEntity（船、矿车等）允许通过
        if (PlayerAttackLockManager.isLocked(uuid)) {
            if (event.getTarget() instanceof LivingEntity) {
                event.setCanceled(true);
                return;
            }
            // 非生命实体攻击放行，但特殊攻击模式不触发
            return;
        }
        PlayerAttackModes modes = getPlayerModes(uuid);
        AttackModeCombo combo = getCombo(uuid);

        // 标记本tick玩家进行了攻击（用于挥空检测）
        PLAYER_ATTACKED_THIS_TICK.add(uuid);

        // ===== 充能释放攻击的特效触发 =====
        // 客户端 InteractionKeyMappingTriggered 已从根源上阻止充能期间的攻击输入，
        // 服务端不再需要禁用攻击逻辑。此处仅处理充能释放攻击（chargeValue > 0）的特效触发。
        double chargeValue = ChargedAttackDamageTracker.getChargeValue(uuid);
        if (modes.hasChargedAttack && chargeValue > 0) {
            // 充能释放攻击：触发电能释放
            triggerElectricDischarge(player);
            // 点射+充能 / 三合一：充能释放后触发点射
            if (canBurstFireTriggerFromChargedRelease(combo)) {
                PENDING_BURST_FROM_CHARGED.put(uuid, true);
            }
            return;
        }

        // ===== 无充能攻击的普通流程 =====

        // 电能释放：正常攻击打到目标时触发
        triggerElectricDischarge(player);

        // 点射+强袭：强袭攻击后触发点射（仅非点射自动攻击时，避免与 onBurstFireAutoAttack 双重触发）
        if (doesAssaultAttackTriggerBurstFire(combo) && AttackStateManager.isPlayerHeld(player)
                && !BurstFireManager.isInBurstFireState(player)) {
            if (event.getTarget() instanceof LivingEntity target) {
                AssaultManager.triggerAssault(player);
                BurstFireManager.startBurstFromAssault(player, target);
            }
        }
    }

    /**
     * 玩家攻击时触发电能释放（由 ChargedAttackDamageHandler 和挥空检测调用）。
     */
    public static void triggerElectricDischarge(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAttackModes modes = getPlayerModes(uuid);

        if (modes.hasElectricDischarge && !ELECTRIC_TRIGGERED_THIS_TICK.contains(uuid)) {
            ElectricDischargeManager.releaseElectric(player);
            ELECTRIC_TRIGGERED_THIS_TICK.add(uuid);
        }
    }

    // ===== Tick处理 =====

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        // ===== 攻击锁定：取消进行中的特殊攻击模式 =====
        if (PlayerAttackLockManager.isLocked(uuid)) {
            PlayerAttackLockManager.cancelActiveModes(uuid);
            // 同步充能状态到客户端（cancelActiveModes可能先于ChargedAttackManager的tick运行，
            // 此时需要确保客户端收到reset，避免HUD残留）
            if (ChargedAttackManager.hasChargedAttack(player)) {
                NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
            }
            // 跳过所有特殊攻击模式逻辑
            PLAYER_ATTACKED_THIS_TICK.remove(uuid);
            ELECTRIC_TRIGGERED_THIS_TICK.remove(uuid);
            return;
        }

        PlayerAttackModes modes = getPlayerModes(uuid);
        AttackModeCombo combo = getCombo(uuid);

        // ===== 强袭按住维持逻辑 =====
        if (modes.hasAssault) {
            handleAssaultHoldLogic(player, combo);
        }

        // ===== 充能期间强袭触发逻辑 =====
        if (doesAssaultTriggerDuringCharging(combo) && ChargedAttackManager.isCharging(player)) {
            handleChargedAssaultTrigger(player);
        }

        // ===== 电能释放挥空检测 =====
        if (modes.hasElectricDischarge) {
            handleSwingDetection(player);
        }

        // ===== 清理本tick的临时数据 =====
        PLAYER_ATTACKED_THIS_TICK.remove(uuid);
        ELECTRIC_TRIGGERED_THIS_TICK.remove(uuid);
    }

    /**
     * 处理强袭按住维持逻辑。
     * 按住左键时强袭叠层维持，松开时根据组合策略决定是否取消。
     */
    private static void handleAssaultHoldLogic(ServerPlayer player, AttackModeCombo combo) {
        UUID uuid = player.getUUID();
        boolean isHolding = AttackStateManager.isPlayerHeld(player);
        boolean isBurstFiring = BurstFireManager.isInBurstFireState(player);
        boolean isCharging = ChargedAttackManager.isCharging(player);
        boolean isInCooldown = BurstFireManager.isInComboCooldown(uuid);

        if (!isHolding) {
            boolean shouldMaintain = false;

            // 点射+强袭：点射期间和冷却期间维持强袭
            if (combo == AttackModeCombo.BURST_ASSAULT && (isBurstFiring || isInCooldown)) {
                shouldMaintain = true;
            }

            // 含充能的组合：充能期间维持强袭
            if (doesAssaultTriggerDuringCharging(combo) && isCharging) {
                shouldMaintain = true;
            }

            // 三合一：点射期间也维持强袭（充能释放后触发的点射）
            if (combo == AttackModeCombo.BURST_ASSAULT_CHARGED && isBurstFiring) {
                shouldMaintain = true;
            }

            if (!shouldMaintain) {
                AssaultManager.clearAssault(uuid);
            }
        }
    }

    /**
     * 处理充能期间强袭触发。
     * 按照玩家当前攻击速度频率触发强袭。
     */
    private static void handleChargedAssaultTrigger(ServerPlayer player) {
        // 攻击强度<1时充能暂停，强袭也同步停止
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        UUID uuid = player.getUUID();
        int cooldown = CHARGE_ASSAULT_COOLDOWN.getOrDefault(uuid, 0);

        if (cooldown > 0) {
            CHARGE_ASSAULT_COOLDOWN.put(uuid, cooldown - 1);
        } else {
            AssaultManager.triggerAssault(player);
            double baseAttackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            CHARGE_ASSAULT_COOLDOWN.put(uuid, (int) Math.max(1, Math.ceil(20.0 / baseAttackSpeed) - 1));
        }
    }

    /**
     * 检测玩家攻击挥空。
     * 当玩家攻击强度从高变低（说明执行了攻击动作），但没有通过 AttackEntityEvent 打到目标，
     * 则判定为挥空，触发电能释放。
     */
    private static void handleSwingDetection(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float currentStrength = player.getAttackStrengthScale(0.0F);
        Float previousStrength = PREVIOUS_ATTACK_STRENGTH.get(uuid);

        if (previousStrength != null && previousStrength >= 0.9F && currentStrength < previousStrength) {
            if (!PLAYER_ATTACKED_THIS_TICK.contains(uuid)) {
                triggerElectricDischarge(player);
            }
        }

        PREVIOUS_ATTACK_STRENGTH.put(uuid, currentStrength);
    }

    // ===== 清理 =====

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_MODES.remove(uuid);
        CHARGE_ASSAULT_COOLDOWN.remove(uuid);
        PENDING_BURST_FROM_CHARGED.remove(uuid);
        ELECTRIC_TRIGGERED_THIS_TICK.remove(uuid);
        PLAYER_ATTACKED_THIS_TICK.remove(uuid);
        PREVIOUS_ATTACK_STRENGTH.remove(uuid);
    }

    public static void clearAllData() {
        PLAYER_MODES.clear();
        CHARGE_ASSAULT_COOLDOWN.clear();
        PENDING_BURST_FROM_CHARGED.clear();
        ELECTRIC_TRIGGERED_THIS_TICK.clear();
        PLAYER_ATTACKED_THIS_TICK.clear();
        PREVIOUS_ATTACK_STRENGTH.clear();
    }
}
