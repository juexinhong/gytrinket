package com.gytrinket.gytrinket.core.charged_shield;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能护盾管理器 - 服务端核心逻辑
 * <p>
 * 当玩家充能时，为玩家提供动态独立乘区护盾效果和护盾效果半径。
 * 动态属性值 = 累计充能值 * 充能比率，上限为maxBonus。
 * <p>
 * 过渡逻辑：
 * 1. 充能中：属性线性过渡到当前充能值对应的目标值
 * 2. 释放充能后：属性保持0.75秒不变，之后线性过渡到0
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedShieldManager {

    private static final String NAMESPACE = "charged_shield";

    // 释放后延迟消退的tick数（0.75秒 = 15tick）
    private static final int RELEASE_DELAY_TICKS = 15;

    // 拥有充能护盾能力的玩家集合
    private static final Set<UUID> PLAYER_HAS_CHARGED_SHIELD = new java.util.concurrent.CopyOnWriteArraySet<>();

    // 玩家当前的动态属性值
    private static final Map<UUID, Double> PLAYER_CURRENT_BONUS = new ConcurrentHashMap<>();

    // 玩家释放充能后的延迟计时器（<=0表示无需延迟，>0表示正在延迟中）
    private static final Map<UUID, Integer> PLAYER_RELEASE_DELAY = new ConcurrentHashMap<>();

    // 玩家上一tick是否在充能（用于检测充能→释放的瞬间）
    private static final Map<UUID, Boolean> PLAYER_WAS_CHARGING = new ConcurrentHashMap<>();

    private ChargedShieldManager() {}

    /**
     * 清除指定玩家的所有动态属性
     */
    private static void removeAllDynamicAttributes(UUID uuid) {
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent");
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius");
    }

    /**
     * 判断玩家是否拥有充能护盾能力
     */
    public static boolean hasChargedShield(Player player) {
        return PLAYER_HAS_CHARGED_SHIELD.contains(player.getUUID());
    }

    /**
     * 设置玩家是否拥有充能护盾能力
     */
    public static void setHasChargedShield(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_CHARGED_SHIELD.add(playerUUID);
        } else {
            PLAYER_HAS_CHARGED_SHIELD.remove(playerUUID);
            PLAYER_CURRENT_BONUS.remove(playerUUID);
            PLAYER_RELEASE_DELAY.remove(playerUUID);
            PLAYER_WAS_CHARGING.remove(playerUUID);
            // 清除动态属性
            removeAllDynamicAttributes(playerUUID);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!hasChargedShield(player)) {
            return;
        }

        double currentBonus = PLAYER_CURRENT_BONUS.getOrDefault(uuid, 0.0);
        boolean isCharging = ChargedAttackManager.isCharging(player);
        boolean wasCharging = PLAYER_WAS_CHARGING.getOrDefault(uuid, false);

        // 检测充能→释放的瞬间，启动延迟计时器
        if (wasCharging && !isCharging) {
            PLAYER_RELEASE_DELAY.put(uuid, RELEASE_DELAY_TICKS);
        }
        PLAYER_WAS_CHARGING.put(uuid, isCharging);

        int releaseDelay = PLAYER_RELEASE_DELAY.getOrDefault(uuid, 0);

        if (isCharging) {
            // 充能中：线性过渡到目标值
            double chargeValue = ChargedAttackManager.getChargeValue(player);
            double targetBonus = Math.min(chargeValue * Config.getChargedShieldChargeRatio(), Config.getChargedShieldMaxBonus());

            // 充能中清除延迟计时器
            if (releaseDelay > 0) {
                PLAYER_RELEASE_DELAY.remove(uuid);
                releaseDelay = 0;
            }

            // 线性过渡
            double transitionRate = Config.getChargedShieldDecayRate();
            if (currentBonus < targetBonus) {
                currentBonus = Math.min(currentBonus + transitionRate, targetBonus);
            } else if (currentBonus > targetBonus) {
                currentBonus = Math.max(currentBonus - transitionRate, targetBonus);
            }
        } else if (releaseDelay > 0) {
            // 释放后的延迟期间：属性保持不变
            releaseDelay--;
            if (releaseDelay <= 0) {
                PLAYER_RELEASE_DELAY.remove(uuid);
            } else {
                PLAYER_RELEASE_DELAY.put(uuid, releaseDelay);
            }
        } else if (currentBonus > 0) {
            // 延迟结束后：线性过渡到0
            double transitionRate = Config.getChargedShieldDecayRate();
            currentBonus = Math.max(0, currentBonus - transitionRate);

            if (currentBonus <= 0) {
                PLAYER_CURRENT_BONUS.remove(uuid);
                PLAYER_WAS_CHARGING.remove(uuid);
                // 完全消退，移除动态属性
                removeAllDynamicAttributes(uuid);
                return;
            }
        } else {
            // 没有充能也没有残余bonus，无需处理
            return;
        }

        PLAYER_CURRENT_BONUS.put(uuid, currentBonus);

        // 设置动态属性
        AttributeManager.setDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent", currentBonus);
        AttributeManager.setDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius", currentBonus);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_HAS_CHARGED_SHIELD.remove(uuid);
        PLAYER_CURRENT_BONUS.remove(uuid);
        PLAYER_RELEASE_DELAY.remove(uuid);
        PLAYER_WAS_CHARGING.remove(uuid);
        removeAllDynamicAttributes(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_CURRENT_BONUS.remove(uuid);
        PLAYER_RELEASE_DELAY.remove(uuid);
        PLAYER_WAS_CHARGING.remove(uuid);
        removeAllDynamicAttributes(uuid);
    }
}
