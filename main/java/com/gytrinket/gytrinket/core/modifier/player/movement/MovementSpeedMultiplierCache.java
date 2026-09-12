package com.gytrinket.gytrinket.core.modifier.player.movement;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家速度聚合乘数缓存（双端）
 * <p>
 * 服务端：MovementSpeedManager 在聚合乘数变化时写入（并同步客户端）。
 * 客户端：由 S2C 包 SyncMovementSpeedMultiplierPayload 写入（本地玩家）。
 * <p>
 * 用途：{@code MovementSpeedSwimMixin} 在 travel 水中分支读取该乘数，
 * 使速度属性影响游泳速度（原版水中输入速度硬编码，不消费 MOVEMENT_SPEED）。
 */
public final class MovementSpeedMultiplierCache {

    private static final Map<UUID, Double> MULTIPLIERS = new ConcurrentHashMap<>();

    private MovementSpeedMultiplierCache() {}

    public static void set(UUID playerUUID, double multiplier) {
        if (multiplier == 1.0D) {
            MULTIPLIERS.remove(playerUUID);
        } else {
            MULTIPLIERS.put(playerUUID, multiplier);
        }
    }

    public static void remove(UUID playerUUID) {
        MULTIPLIERS.remove(playerUUID);
    }

    /** 读取玩家速度乘数；无记录（未施加或默认值）返回 1.0 */
    public static float getMultiplier(Player player) {
        Double multiplier = MULTIPLIERS.get(player.getUUID());
        return multiplier == null ? 1.0F : multiplier.floatValue();
    }

    public static void clear() {
        MULTIPLIERS.clear();
    }
}
