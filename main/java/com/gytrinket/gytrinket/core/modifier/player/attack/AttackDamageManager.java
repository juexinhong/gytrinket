package com.gytrinket.gytrinket.core.modifier.player.attack;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
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
public class AttackDamageManager {

    
    
    private static final ResourceLocation BASE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_damage_base");
    private static final ResourceLocation PERCENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_damage_percent");

    private static final Map<UUID, Double> PLAYER_ATTACK_DAMAGE_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (attackDamageBase != 0) {
            AttributeModifier baseModifier = new AttributeModifier(BASE_MODIFIER_ID, attackDamageBase, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(PERCENT_MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            attribute.addTransientModifier(percentModifier);
        }

        PLAYER_ATTACK_DAMAGE_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("attack_damage_percent") && !attrName.equals("attack_damage_independent")) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null || !player.isAlive()) {
            return;
        }

        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (attackDamageBase != 0) {
            AttributeModifier baseModifier = new AttributeModifier(BASE_MODIFIER_ID, attackDamageBase, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(PERCENT_MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            attribute.addTransientModifier(percentModifier);
        }

        PLAYER_ATTACK_DAMAGE_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.ATTACK_DAMAGE);
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
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        ModifierHelper.removeAllModModifiers(attribute);
        PLAYER_ATTACK_DAMAGE_MAP.remove(player.getUUID());
    }

    public static double getPlayerAttackDamage(UUID playerUUID) {
        return PLAYER_ATTACK_DAMAGE_MAP.getOrDefault(playerUUID, 1.0);
    }

    public static void clearAllData() {
        PLAYER_ATTACK_DAMAGE_MAP.clear();
    }
}
