package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 * 4. 松开左键释放攻击，伤害 = 玩家当前伤害 + 充能值
 * 5. 只影响释放时的这一次攻击，后续连击不受影响
 * <p>
 * 跨系统交互通过 AttackModeManager 策略管理：
 * - 充能期间强袭触发由管理器处理
 * - 充能释放后触发点射由管理器处理
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackManager {

    // 玩家充能数据
    private static final Map<UUID, ChargedAttackData> PLAYER_CHARGE_DATA = new ConcurrentHashMap<>();

    // 拥有充能攻击能力的玩家集合
    private static final Set<UUID> PLAYER_HAS_CHARGED_ATTACK = new java.util.concurrent.CopyOnWriteArraySet<>();

    private ChargedAttackManager() {}

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
     */
    public static double calculateChargeRate(Player player) {
        double baseRate = Config.getChargedAttackBaseChargeRate();
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);

        double damageMultiplier = attackDamage * Config.getChargedAttackDamageScaleFactor();
        double speedMultiplier = attackSpeed * Config.getChargedAttackSpeedScaleFactor();

        return baseRate * damageMultiplier * speedMultiplier;
    }

    /**
     * 计算带阻力的充能增量
     */
    public static double calculateChargeIncrement(double currentCharge, double baseRate, Player player) {
        double dragCoeff = Config.getChargedAttackDragCoefficient();
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double dragThreshold = attackDamage * attackSpeed * Config.getChargedAttackDragThresholdFactor();
        double dragFactor = 1.0 - dragCoeff * currentCharge / (currentCharge + dragThreshold);
        return baseRate * Math.max(dragFactor, 0.01);
    }

    /**
     * 开始充能
     * 如果已经在充能中，不重置充能值（幂等操作）
     */
    public static void startCharging(UUID playerUUID) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.computeIfAbsent(playerUUID, k -> new ChargedAttackData());
        if (data.charging) {
            // 已经在充能中，不重置
            return;
        }
        data.charging = true;
        data.chargeValue = 0;
        data.hasReflectedOnce = false;
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

        float attackStrength = player.getAttackStrengthScale(0.0F);

        // 充能期间首次攻击强度低于0.5时，反射为1
        if (!data.hasReflectedOnce && attackStrength < 0.5F) {
            reflectAttackStrengthToFull(player);
            data.hasReflectedOnce = true;
            attackStrength = 1.0F;
        }

        // 攻击强度小于1时暂停充能
        if (attackStrength < 1.0F) {
            return;
        }

        // 计算充能增量
        double chargeRate = calculateChargeRate(player);
        double increment = calculateChargeIncrement(data.chargeValue, chargeRate, player);
        data.chargeValue += increment;
    }

    /**
     * 使用反射设置玩家攻击强度为满
     */
    private static void reflectAttackStrengthToFull(Player player) {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            gytrinket.LOGGER.warn("Failed to reflect attack strength to full", e);
        }
    }

    /**
     * 释放充能攻击，返回充能值
     */
    public static double releaseCharge(UUID playerUUID) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        if (data == null || !data.charging) {
            return 0;
        }

        double chargeValue = data.chargeValue;
        data.charging = false;
        data.chargeValue = 0;

        // 存储充能值到Tracker
        ChargedAttackDamageTracker.setChargeValue(playerUUID, chargeValue);

        return chargeValue;
    }

    /**
     * 取消充能（不释放攻击）
     */
    public static void cancelCharging(UUID playerUUID) {
        ChargedAttackData data = PLAYER_CHARGE_DATA.get(playerUUID);
        if (data != null) {
            data.charging = false;
            data.chargeValue = 0;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!hasChargedAttack(player)) {
            return;
        }

        ChargedAttackData data = PLAYER_CHARGE_DATA.get(uuid);
        if (data == null || !data.charging) {
            return;
        }

        // 检查玩家是否仍然按住左键
        if (AttackStateManager.isPlayerHeld(player)) {
            // 持续充能
            updateCharging(uuid, player);

            // 每3 tick同步充能值到客户端
            data.syncTickCounter++;
            if (data.syncTickCounter >= 3) {
                data.syncTickCounter = 0;
                com.gy_mod.gy_trinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, data.chargeValue);
            }
        } else if (AttackStateManager.isPlayerReleased(player)) {
            // 松开左键 - 释放充能攻击
            double chargeValue = releaseCharge(uuid);
            if (chargeValue > 0) {
                // 通知客户端释放攻击
                com.gy_mod.gy_trinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, chargeValue);
            }
            // 同步0到客户端，清空HUD显示
            com.gy_mod.gy_trinket.network.NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
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
        double chargeValue;
        // 充能期间是否已完成首次反射（攻击强度首次低于0.5时反射为1）
        boolean hasReflectedOnce;
        // 同步计时器（每3 tick同步一次充能值到客户端）
        int syncTickCounter;

        ChargedAttackData() {
            this.charging = false;
            this.chargeValue = 0;
            this.hasReflectedOnce = false;
            this.syncTickCounter = 0;
        }
    }
}
