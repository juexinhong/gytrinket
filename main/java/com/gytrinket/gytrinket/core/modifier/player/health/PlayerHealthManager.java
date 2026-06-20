package com.gytrinket.gytrinket.core.modifier.player.health;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
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
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerHealthManager {

    
    
    private static final ResourceLocation HEALTH_BASE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "health_base");
    private static final ResourceLocation HEALTH_PERCENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "health_percent");

    private static final Map<UUID, Float> PLAYER_MAX_HEALTH_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
        double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            player = server.getPlayerList().getPlayer(playerUUID);
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        double totalMultiplier = healthPercent * healthIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (healthBase != 0) {
            AttributeModifier baseModifier = new AttributeModifier(HEALTH_BASE_MODIFIER_ID, healthBase, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(HEALTH_PERCENT_MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            attribute.addTransientModifier(percentModifier);
        }

        float maxHealth = player.getMaxHealth();
        PLAYER_MAX_HEALTH_MAP.put(playerUUID, maxHealth);

        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("player_health_percent") && !attrName.equals("player_health_independent")) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null || !player.isAlive()) {
            return;
        }

        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
        double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

        double totalMultiplier = healthPercent * healthIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (healthBase != 0) {
            AttributeModifier baseModifier = new AttributeModifier(HEALTH_BASE_MODIFIER_ID, healthBase, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(HEALTH_PERCENT_MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            attribute.addTransientModifier(percentModifier);
        }

        float maxHealth = player.getMaxHealth();
        PLAYER_MAX_HEALTH_MAP.put(playerUUID, maxHealth);

        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        ModifierHelper.removeAllModModifiers(attribute);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        Float maxHealth = PLAYER_MAX_HEALTH_MAP.get(playerUUID);

        if (maxHealth != null && maxHealth > 0) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PLAYER_MAX_HEALTH_MAP.remove(player.getUUID());
    }

    public static float getPlayerMaxHealth(UUID playerUUID) {
        return PLAYER_MAX_HEALTH_MAP.getOrDefault(playerUUID, 20.0f);
    }

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_MAX_HEALTH_MAP.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_MAX_HEALTH_MAP.clear();
    }
}
