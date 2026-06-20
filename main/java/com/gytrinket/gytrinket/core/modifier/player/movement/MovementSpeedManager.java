package com.gytrinket.gytrinket.core.modifier.player.movement;

import com.gytrinket.gytrinket.core.attack_mode.AttackSpeedPenaltyManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class MovementSpeedManager {

    
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "movement_speed");

    private static final Map<UUID, Double> PLAYER_MOVEMENT_SPEED_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double movementSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_percent");
        double movementSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_independent");

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        double totalMultiplier = movementSpeedPercent * movementSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        // 通知 AttackSpeedPenaltyManager 重新添加攻击惩罚修改器
        AttackSpeedPenaltyManager.markForceRefresh(playerUUID);

        PLAYER_MOVEMENT_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AttributeManager.recalculateAndCachePlayerAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
        PLAYER_MOVEMENT_SPEED_MAP.remove(player.getUUID());
    }

    public static double getPlayerMovementSpeed(UUID playerUUID) {
        return PLAYER_MOVEMENT_SPEED_MAP.getOrDefault(playerUUID, 0.1);
    }

    public static void clearAllData() {
        PLAYER_MOVEMENT_SPEED_MAP.clear();
    }
}
