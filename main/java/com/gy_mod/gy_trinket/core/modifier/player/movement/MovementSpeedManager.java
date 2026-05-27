package com.gy_mod.gy_trinket.core.modifier.player.movement;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 移动速度修饰符管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，获取移动速度属性并应用
 * 2. 使用 MULTIPLY_TOTAL 操作，应用百分比加成
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class MovementSpeedManager {

    private static final String MODIFIER_NAME = "player_movement_speed_modifier";
    private static final UUID MODIFIER_UUID = UUID.fromString("e3f4a5b6-c7d8-9012-ef01-234567890124");

    private static final Map<UUID, Double> PLAYER_MOVEMENT_SPEED_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double movementSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_percent");
        double movementSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_independent");

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        // 计算总倍数
        double totalMultiplier = movementSpeedPercent * movementSpeedIndependent;

        if (totalMultiplier != 1.0) {
            // 原版修饰符乘法会自动+1，所以需要-1来补偿
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL, MODIFIER_UUID, MODIFIER_NAME);
        } else {
            removeModifier(player, MODIFIER_UUID);
        }

        PLAYER_MOVEMENT_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    private static void addModifier(Player player, double value, AttributeModifier.Operation operation, UUID modifierUuid, String modifierName) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        removeModifier(player, modifierUuid);

        AttributeModifier modifier = new AttributeModifier(modifierUuid, modifierName, value, operation);
        attribute.addPermanentModifier(modifier);
    }

    private static void removeModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(modifierUuid)) {
                attribute.removeModifier(modifier);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        removeModifier(player, MODIFIER_UUID);
        PLAYER_MOVEMENT_SPEED_MAP.remove(player.getUUID());
    }

    public static double getPlayerMovementSpeed(UUID playerUUID) {
        return PLAYER_MOVEMENT_SPEED_MAP.getOrDefault(playerUUID, 0.1);
    }

    public static void clearAllData() {
        PLAYER_MOVEMENT_SPEED_MAP.clear();
    }
}