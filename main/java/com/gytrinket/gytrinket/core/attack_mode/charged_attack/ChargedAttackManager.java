package com.gytrinket.gytrinket.core.attack_mode.charged_attack;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager;
import com.gytrinket.gytrinket.core.attack_mode.AttackStateManager;
import com.gytrinket.gytrinket.core.attack_mode.PlayerAttackLockManager;
import com.gytrinket.gytrinket.core.attack_mode.GrudgeManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能攻击管理器 - 服务端核心逻辑
 * <p>
 * 充能攻击系统：
 * 1. 需要光点核心中有指定物品才能启用
 * 2. 生效时禁用玩家正常攻击行为
 * 3. 按住左键时进行充能，充能无上限但有阻力制衡
 * 4. 松开左键释放攻击，伤害 = 玩家当前伤害 * (1 + 充能值)
 * 5. 只影响释放时的这一次攻击，后续连击不受影响
 * <p>
 * 跨系统交互通过 AttackModeManager 策略管理：
 * - 充能期间强袭触发由管理器处理
 * - 充能释放后触发点射由管理器处理
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackManager {

    // 玩家充能数据
    private static final Map<UUID, ChargedAttackData> PLAYER_CHARGE_DATA = new ConcurrentHashMap<>();

    // 拥有充能攻击能力的玩家集合
    private static final Set<UUID> PLAYER_HAS_CHARGED_ATTACK = new java.util.concurrent.CopyOnWriteArraySet<>();

    // 长按右键充能攻速修正值的动态属性命名空间与属性名
    private static final String CHARGED_ATTACK_NAMESPACE = "charged_attack";
    private static final String ATTACK_SPEED_FLAT_ATTRIBUTE = "attack_speed_flat";

    private ChargedAttackManager() {}

    /**
     * 将充能物品白名单攻击速度修正值写入模组属性账本（动态属性，命名空间 charged_attack）
     * 由 AttackSpeedManager 监听账本变化统一投影到原版攻击速度属性
     * 仅对非武器物品（武器类与工具类武器自带攻速修正，不受限）且修正值非0时写入，
     * 武器类或修正值为0时确保移除残留
     */
    private static void applyItemUseChargeSpeedAttribute(ServerPlayer player) {
        Item held = player.getMainHandItem().getItem();
        if (Config.isWeaponLikeItem(held)) {
            removeItemUseChargeSpeedAttribute(player);
            return;
        }
        double modifierValue = Config.getItemUseChargeSpeedModifier(held);
        if (modifierValue == 0) {
            removeItemUseChargeSpeedAttribute(player);
            return;
        }
        AttributeManager.setDynamicAttribute(
            player.getUUID(), CHARGED_ATTACK_NAMESPACE, ATTACK_SPEED_FLAT_ATTRIBUTE, modifierValue);
    }

    /**
     * 从模组属性账本移除长按右键充能的攻速修正值动态属性
     * 不存在时为无操作（不触发事件），充能结束/取消时调用
     */
    private static void removeItemUseChargeSpeedAttribute(Player player) {
        AttributeManager.removeDynamicAttribute(
            player.getUUID(), CHARGED_ATTACK_NAMESPACE, ATTACK_SPEED_FLAT_ATTRIBUTE);
    }

    /**
     * 通过UUID从模组属性账本移除长按右键充能的攻速修正值动态属性（玩家可能不在充能调用链中）
     */
    private static void removeItemUseChargeSpeedAttribute(UUID playerUUID) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            removeItemUseChargeSpeedAttribute(player);
        }
    }

    /**
     * 判断玩家是否拥有充能攻击能力
     */
    public static boolean hasChargedAttack(Player player) {
        return PLAYER_HAS_CHARGED_ATTACK.contains(player.getUUID());
    }

    /**
     * 设置玩家是否拥有充能攻击能力
     */
    public static void setHasChargedAttack(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_CHARGED_ATTACK.add(playerUUID);
        } else {
            PLAYER_HAS_CHARGED_ATTACK.remove(playerUUID);
            PLAYER_CHARGE_DATA.remove(playerUUID);
            // 模块卸下/失效时移除充能攻速修正值（防御性）
            removeItemUseChargeSpeedAttribute(playerUUID);
        }
    }

    /**
     * 玩家是否正在充能中
     */
    public static boolean isCharging(Player player) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(player.getUUID());
        return data != null && data.charging;
    }

    /**
     * 获取玩家当前充能值
     */
    public static double getChargeValue(Player player) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(player.getUUID());
        return data != null ? data.chargeValue : 0;
    }

    /**
     * 计算充能速率
     * 充能速率仅受攻击速度影响，不受攻击伤害影响
     * 长按右键充能时白名单修正值经模组属性账本投影施加，此处直接读取属性值即可
     */
    public static double calculateChargeRate(Player player) {
        double baseRate = Config.getChargedAttackBaseChargeRate();
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double speedMultiplier = attackSpeed * Config.getChargedAttackSpeedScaleFactor();

        return baseRate * speedMultiplier;
    }

    /**
     * 计算带阻力的充能增量
     * 阻力阈值仅受攻击速度影响，不受攻击伤害影响
     */
    public static double calculateChargeIncrement(double currentCharge, double baseRate, Player player) {
        double dragCoeff = Config.getChargedAttackDragCoefficient();
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double dragThreshold = attackSpeed * Config.getChargedAttackDragThresholdFactor();
        double dragFactor = 1.0 - dragCoeff * currentCharge / (currentCharge + dragThreshold);
        return baseRate * Math.max(dragFactor, 0.01);
    }

    /**
     * 开始充能
     * 如果已经在充能中，不重置充能值（幂等操作）
     */
    public static void startCharging(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        // 攻击锁定时禁用充能
        if (PlayerAttackLockManager.isLocked(playerUUID)) {
            return;
        }
        // 点射进行中或冷却期间禁用充能（近战点射连击冷却/弹射物点射物品冷却）
        if (AttackModeManager.isChargingDisabledDuringBurstFire(player)) {
            return;
        }

        ChargedAttackData data = PLAYER_CHARGE_DATA.computeIfAbsent(playerUUID, k -> new ChargedAttackData());
        if (data.charging) {
            // 已经在充能中，不重置
            return;
        }
        data.charging = true;
        data.chargeValue = 0;
        data.hasSeenHeld = false;
        data.itemUseCharge = false;
    }

    /**
     * 更新充能值（每tick调用）
     * 强袭触发已移至 AttackModeManager，此处仅处理充能计算
     */
    public static void updateCharging(UUID playerUUID, Player player) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        if (data == null || !data.charging) {
            return;
        }

        // 攻击强度小于1时暂停充能（如挖掘方块时攻击强度被消耗）
        // 正常充能时攻击输入在客户端被取消，攻击强度不会被消耗，此处检查不会触发。
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        // 计算充能增量（长按右键充能时修正值已经账本投影施加，直接读属性）
        double chargeRate = calculateChargeRate(player);

        // 添加积怨充能速率（也受阻力影响）
        double grudgeRate = GrudgeManager.getTotalGrudgeChargeRate(playerUUID);
        chargeRate += grudgeRate;

        double increment = calculateChargeIncrement(data.chargeValue, chargeRate, player);
        data.chargeValue += increment;
    }

    /**
     * 释放充能攻击，返回充能值
     * 不立即清零充能值，改为标记释放状态，由tick进行快速消退
     */
    public static double releaseCharge(UUID playerUUID) {
        return releaseCharge(playerUUID, true);
    }

    /**
     * 释放充能攻击（可指定是否写入近战伤害Tracker）
     *
     * @param storeToTracker true=左键释放，写入Tracker供近战伤害消耗；
     *                       false=长按右键充能释放，不写Tracker（近战不加成，仅箭矢类弹射物按消退值增幅）
     */
    public static double releaseCharge(UUID playerUUID, boolean storeToTracker) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        if (data == null || !data.charging) {
            return 0;
        }

        double chargeValue = data.chargeValue;
        data.charging = false;
        data.releasing = true;
        data.itemUseCharge = !storeToTracker;

        if (storeToTracker) {
            // 存储充能值到Tracker
            ChargedAttackDamageTracker.setChargeValue(playerUUID, chargeValue);
        }

        return chargeValue;
    }

    /**
     * 长按右键充能开始（客户端鼠标右键按下时请求）：
     * 通用右键充能入口，不依赖任何具体物品，由服务端校验充能攻击解锁状态
     */
    public static void startItemUseCharge(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!hasChargedAttack(player)) {
            return;
        }
        // 攻击锁定时禁用充能
        if (PlayerAttackLockManager.isLocked(uuid)) {
            return;
        }
        // 点射进行中或冷却期间禁用充能（近战点射连击冷却/弹射物点射物品冷却）
        if (AttackModeManager.isChargingDisabledDuringBurstFire(player)) {
            return;
        }
        ChargedAttackData data = PLAYER_CHARGE_DATA.computeIfAbsent(uuid, k -> new ChargedAttackData());
        if (data.charging) {
            // 已经在充能中（左键或右键），不重置
            return;
        }
        data.charging = true;
        data.chargeValue = 0;
        data.hasSeenHeld = false;
        data.itemUseCharge = true;

        // 将充能物品白名单攻击速度修正值写入模组属性账本（由 AttackSpeedManager 统一投影）
        applyItemUseChargeSpeedAttribute(player);
    }

    /**
     * 长按右键松开时释放：不触发任何攻击行为，
     * 仅让充能值进入消退期（消退期间归属玩家的箭矢类弹射物加入世界时按当前充能值增幅）
     */
    public static void releaseChargeFromItemUse(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // 无论是否在右键充能中，均移除充能攻速修正值（防御性）
        removeItemUseChargeSpeedAttribute(player);
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(uuid);
        if (data == null || !data.charging || !data.itemUseCharge) {
            return;
        }

        double chargeValue = releaseCharge(uuid, false);
        if (chargeValue > 0) {
            // 发布释放事件（供幽灵机身等系统使用）
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new ChargedAttackEvent(ChargedAttackEvent.Type.RELEASED, player));

            // 同步释放后的充能值到客户端（HUD进入消退显示）
            com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, chargeValue);
        }
    }

    /**
     * 获取释放后消退期内的当前充能值（未在消退期返回0）
     * 供弹射物加入世界时按当前充能值增幅（基础伤害 × (1 + 充能值)）
     */
    public static double getReleasingChargeValue(UUID playerUUID) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        return (data != null && data.releasing) ? data.chargeValue : 0;
    }

    /**
     * 取消充能（不释放攻击）
     */
    public static void cancelCharging(UUID playerUUID) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        if (data != null) {
            data.charging = false;
            data.chargeValue = 0;
            data.itemUseCharge = false;
        }
        // 取消充能时移除充能攻速修正值（防御性）
        removeItemUseChargeSpeedAttribute(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        // 防御性清理：非右键充能状态时不应存在充能攻速修正值
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(uuid);
        if (data == null || !data.charging || !data.itemUseCharge) {
            removeItemUseChargeSpeedAttribute(player);
        }

        if (!hasChargedAttack(player)) {
            return;
        }

        data = PLAYER_CHARGE_DATA.get(uuid);
        if (data == null) {
            return;
        }

        // 攻击锁定时取消充能
        if (PlayerAttackLockManager.isLocked(uuid)) {
            if (data.charging || data.releasing) {
                cancelCharging(uuid);
                com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
            }
            return;
        }

        if (data.charging) {
            if (data.itemUseCharge) {
                // 长按右键充能：由客户端"松开右键"包结束充能，不依赖左键按住状态
                data.hasSeenHeld = true;
                updateCharging(uuid, player);

                // 发布充能中事件（供幽灵机身等系统使用）
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new ChargedAttackEvent(ChargedAttackEvent.Type.CHARGING, player));

                // 每3 tick同步充能值到客户端
                data.syncTickCounter++;
                if (data.syncTickCounter >= 3) {
                    data.syncTickCounter = 0;
                    com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, data.chargeValue);
                }
            } else if (AttackStateManager.isPlayerHeld(player)) {
                // 持续充能（标记已确认按住状态，供高延迟下松开判定使用）
                data.hasSeenHeld = true;
                updateCharging(uuid, player);

                // 发布充能中事件（供幽灵机身等系统使用）
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new ChargedAttackEvent(ChargedAttackEvent.Type.CHARGING, player));

                // 每3 tick同步充能值到客户端
                data.syncTickCounter++;
                if (data.syncTickCounter >= 3) {
                    data.syncTickCounter = 0;
                    com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, data.chargeValue);
                }
            } else if (data.hasSeenHeld && AttackStateManager.isPlayerReleased(player)) {
                // 松开左键 - 释放充能攻击
                // hasSeenHeld 门控：充能启动请求(ChargedAttackPayload)与按住状态包(AttackStatePayload)
                // 是两个独立数据包，高延迟客户端下状态包可能晚于启动请求到达；
                // 在确认过"按住"之前服务端状态默认为 RELEASED，此时释放会得到 0 充能值
                // 并永久终止本次充能（客机充能攻击失灵的根因），因此必须先确认过按住
                double chargeValue = releaseCharge(uuid);
                if (chargeValue > 0) {
                    // 发布释放事件（供幽灵机身等系统使用）
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new ChargedAttackEvent(ChargedAttackEvent.Type.RELEASED, player));

                    // 通知客户端释放攻击
                    com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, chargeValue);
                }
            }
        } else if (data.releasing) {
            // 释放后快速消退：每刻消退 1 + 当前值的30%
            double decay = 1.0 + data.chargeValue * 0.3;
            data.chargeValue -= decay;

            if (data.chargeValue <= 0) {
                data.chargeValue = 0;
                data.releasing = false;
                data.itemUseCharge = false;
                // 同步0到客户端，清空HUD显示
                com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
            } else {
                // 同步消退中的充能值到客户端
                data.syncTickCounter++;
                if (data.syncTickCounter >= 3) {
                    data.syncTickCounter = 0;
                    com.gytrinket.gytrinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, data.chargeValue);
                }
            }

            // 同步消退中的充能值到Tracker，供近战伤害处理使用
            // （长按右键充能释放不进近战通道，仅箭矢类弹射物按当前充能值增幅）
            if (!data.itemUseCharge) {
                ChargedAttackDamageTracker.setChargeValue(uuid, data.chargeValue);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_CHARGE_DATA.remove(uuid);
        PLAYER_HAS_CHARGED_ATTACK.remove(uuid);
        ChargedAttackDamageTracker.removePlayer(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_CHARGE_DATA.remove(uuid);
        ChargedAttackDamageTracker.removePlayer(uuid);
    }

    public static void clearAllData() {
        PLAYER_CHARGE_DATA.clear();
        PLAYER_HAS_CHARGED_ATTACK.clear();
        ChargedAttackDamageTracker.clearAll();
    }

    private static class ChargedAttackData {
        boolean charging;
        boolean releasing;
        double chargeValue;
        // 同步计时器（每3 tick同步一次充能值到客户端）
        int syncTickCounter;
        // 是否已通过状态包确认过"按住左键"（高延迟下状态包晚于充能启动请求到达，
        // 确认前不允许按"松开"释放，防止 0 充能值误释放终止充能）
        boolean hasSeenHeld;
        // 本次充能是否源于长按右键（充能阶段=true=右键充能源；释放后=true=不写近战Tracker）
        boolean itemUseCharge;

        ChargedAttackData() {
            this.charging = false;
            this.releasing = false;
            this.chargeValue = 0;
            this.syncTickCounter = 0;
            this.hasSeenHeld = false;
            this.itemUseCharge = false;
        }
    }
}
