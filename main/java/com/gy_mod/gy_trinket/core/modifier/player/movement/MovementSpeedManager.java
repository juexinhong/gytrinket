package com.gy_mod.gy_trinket.core.modifier.player.movement;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class MovementSpeedManager {

    private static final String MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "movement_speed";
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

        double totalMultiplier = movementSpeedPercent * movementSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(modifier);
        }

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
