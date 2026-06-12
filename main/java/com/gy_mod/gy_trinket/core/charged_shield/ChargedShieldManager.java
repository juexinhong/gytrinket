package com.gy_mod.gy_trinket.core.charged_shield;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能护盾管理器 - 服务端核心逻辑
 * <p>
 * 当玩家充能时，为玩家提供动态独立乘区护盾效果和护盾效果半径。
 * 动态属性值 = 累计充能值 * 充能比率，上限为maxBonus。
 * 停止充能后，动态属性快速消退。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedShieldManager {

    private static final String NAMESPACE = "charged_shield";

    // 拥有充能护盾能力的玩家集合
    private static final Set<UUID> PLAYER_HAS_CHARGED_SHIELD = new java.util.concurrent.CopyOnWriteArraySet<>();

    // 玩家当前的动态属性值（用于消退）
    private static final Map<UUID, Double> PLAYER_CURRENT_BONUS = new ConcurrentHashMap<>();

    private ChargedShieldManager() {}

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
            // 清除动态属性
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, "shield_effect_independent");
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, "shield_effect_radius");
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

        if (!hasChargedShield(player)) {
            return;
        }

        double currentBonus = PLAYER_CURRENT_BONUS.getOrDefault(uuid, 0.0);

        if (ChargedAttackManager.isCharging(player)) {
            // 充能中：根据充能值计算动态属性
            double chargeValue = ChargedAttackManager.getChargeValue(player);
            double targetBonus = Math.min(chargeValue * Config.getChargedShieldChargeRatio(), Config.getChargedShieldMaxBonus());

            // 更新当前bonus（充能期间直接设置为目标值）
            currentBonus = targetBonus;
            PLAYER_CURRENT_BONUS.put(uuid, currentBonus);
        } else if (currentBonus > 0) {
            // 未充能但有残余bonus：快速消退
            double decayRate = Config.getChargedShieldDecayRate();
            currentBonus = Math.max(0, currentBonus - decayRate);

            if (currentBonus <= 0) {
                PLAYER_CURRENT_BONUS.remove(uuid);
                // 完全消退，移除动态属性
                AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent");
                AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius");
                return;
            }

            PLAYER_CURRENT_BONUS.put(uuid, currentBonus);
        } else {
            // 没有充能也没有残余bonus，无需处理
            return;
        }

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
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent");
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius");
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_CURRENT_BONUS.remove(uuid);
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent");
        AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius");
    }

    public static void clearAllData() {
        for (UUID uuid : PLAYER_HAS_CHARGED_SHIELD) {
            AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_independent");
            AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, "shield_effect_radius");
        }
        PLAYER_HAS_CHARGED_SHIELD.clear();
        PLAYER_CURRENT_BONUS.clear();
    }
}
