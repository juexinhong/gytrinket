package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能攻击伤害追踪器
 * <p>
 * 存储玩家释放后的充能值，供伤害事件使用
 * 充能值随tick快速消退，每次攻击消耗当前充能值
 */
public class ChargedAttackDamageTracker {

    private static final Map<UUID, Double> PENDING_CHARGE_DAMAGE = new ConcurrentHashMap<>();

    private ChargedAttackDamageTracker() {}

    /**
     * 设置待处理的充能伤害值
     */
    public static void setChargeValue(UUID playerUUID, double chargeValue) {
        PENDING_CHARGE_DAMAGE.put(playerUUID, chargeValue);
    }

    /**
     * 消费充能伤害值（一次性，读取后移除）
     */
    public static double consumeChargeValue(UUID playerUUID) {
        Double value = PENDING_CHARGE_DAMAGE.remove(playerUUID);
        return value != null ? value : 0.0;
    }

    /**
     * 获取充能伤害值（不移除）
     */
    public static double getChargeValue(UUID playerUUID) {
        return PENDING_CHARGE_DAMAGE.getOrDefault(playerUUID, 0.0);
    }

    /**
     * 清理玩家数据
     */
    public static void removePlayer(UUID playerUUID) {
        PENDING_CHARGE_DAMAGE.remove(playerUUID);
    }

    public static void clearAll() {
        PENDING_CHARGE_DAMAGE.clear();
    }
}
