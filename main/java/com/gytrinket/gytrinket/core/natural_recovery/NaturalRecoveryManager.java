package com.gytrinket.gytrinket.core.natural_recovery;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自然恢复管理器
 * <p>
 * 功能：
 * 1. 玩家生命自然恢复：每3刻恢复基于最大生命值和恢复效率的生命值
 * 2. 护盾自然恢复：每3刻恢复基于最大护盾值和恢复效率的护盾值
 * 3. 攻击冷却惩罚：玩家攻击冷却未结束时，降低恢复速度20%
 * 4. 预留持续自伤位置：可通过负面恢复效率实现持续伤害
 * <p>
 * 恢复计算公式：
 * - 玩家生命恢复 = 配置基础恢复 × 恢复效率 × 攻击冷却惩罚
 * - 护盾恢复 = 配置基础恢复 × 恢复效率 × 攻击冷却惩罚
 * <p>
 * 攻击冷却检测：使用 getAttackStrengthScale(0.0f)，值>0.5时认为冷却未结束
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class NaturalRecoveryManager {

    /** 玩家恢复数据缓存：玩家UUID -> 恢复数据 */
    private static final Map<UUID, RecoveryData> PLAYER_RECOVERY_DATA = new HashMap<>();

    /** 玩家刻计数器：用于实现每4刻触发一次恢复 */
    private static final Map<UUID, Integer> PLAYER_TICK_COUNTER = new HashMap<>();

    /** 恢复触发间隔：每4刻触发一次（相当于1秒触发5次） */
    private static final int RECOVERY_INTERVAL = 4;

    private NaturalRecoveryManager() {}

    /**
     * 监听属性计算完毕事件
     * 初始化玩家的恢复数据缓存
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        PLAYER_RECOVERY_DATA.put(playerUUID, new RecoveryData());
    }

    /**
     * 监听玩家刻事件
     * 每3刻执行一次自然恢复逻辑
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        RecoveryData data = PLAYER_RECOVERY_DATA.computeIfAbsent(playerUUID, k -> new RecoveryData());

        int tickCounter = PLAYER_TICK_COUNTER.getOrDefault(playerUUID, 0) + 1;
        PLAYER_TICK_COUNTER.put(playerUUID, tickCounter);

        if (tickCounter < RECOVERY_INTERVAL) {
            return;
        }

        PLAYER_TICK_COUNTER.put(playerUUID, 0);

        double recoveryEfficiency = AttributeManager.getGroupAttribute(playerUUID, "recovery_efficiency");
        recoveryEfficiency = Math.max(recoveryEfficiency, 0.0);

        // 检测攻击冷却状态：getAttackStrengthScale 返回 0~1，值越高表示冷却越接近完成
        // 当值 > 0.9 时，表示攻击冷却尚未结束（还在冷却中）
        float attackStrengthScale = player.getAttackStrengthScale(0.0f);
        double recoveryMultiplier = (attackStrengthScale > 0.5f) ? Config.getNaturalRecoveryAttackCooldownPenalty() : 1.0;

        double healthModifier = 1.0;
        double shieldModifier = 1.0;
        double additionalShieldRecovery = 0.0;

        if (ShieldNaturalRecoveryHandler.hasShieldNaturalRecovery(playerUUID)) {
            healthModifier = ShieldNaturalRecoveryHandler.getPlayerHealthRecoveryModifier(playerUUID);
            shieldModifier = ShieldNaturalRecoveryHandler.getShieldRecoveryModifier(playerUUID);
            additionalShieldRecovery = ShieldNaturalRecoveryHandler.getShieldRecoveryBase();
        }

        double playerHealthRecovery;
        if (Config.NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED.get() || recoveryEfficiency > 1.0) {
            playerHealthRecovery = Config.getNaturalRecoveryPlayerHealth() * recoveryEfficiency * recoveryMultiplier / 5.0;
        } else {
            playerHealthRecovery = 0.0;
        }
        double shieldRecovery = Config.getNaturalRecoveryShield() * recoveryEfficiency * recoveryMultiplier / 5.0;

        playerHealthRecovery *= healthModifier;
        shieldRecovery = (shieldRecovery + additionalShieldRecovery) * shieldModifier;

        data.lastPlayerHealthRecovery = playerHealthRecovery;
        data.lastShieldRecovery = shieldRecovery;

        if (player.isAlive() && player.getHealth() > 0 && player.getHealth() < player.getMaxHealth()) {
            float healAmount = (float)(player.getMaxHealth() * playerHealthRecovery);
            player.heal(healAmount);
        }

        if (shieldRecovery > 0) {
            double currentShield = ShieldManager.getCurrentShield(playerUUID);
            double maxShield = ShieldManager.getMaxShield(playerUUID);
            if (currentShield > 0 && currentShield < maxShield) {
                double shieldHealAmount = maxShield * shieldRecovery;
                ShieldManager.addShield(playerUUID, shieldHealAmount);
            }
        }
    }

    /**
     * 监听玩家退出事件
     * 清理玩家数据，防止内存泄漏
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        PLAYER_RECOVERY_DATA.remove(playerUUID);
        PLAYER_TICK_COUNTER.remove(playerUUID);
    }

    /**
     * 恢复数据类
     * 存储玩家最后的恢复计算结果
     * <p>
     * 预留字段用于：
     * - 持续玩家自伤（通过负面恢复效率）
     * - 持续护盾自伤（通过负面恢复效率）
     */
    public static class RecoveryData {
        public double lastPlayerHealthRecovery;

        public double lastShieldRecovery;

        public double getLastPlayerHealthRecovery() {
            return lastPlayerHealthRecovery;
        }

        public double getLastShieldRecovery() {
            return lastShieldRecovery;
        }
    }
}