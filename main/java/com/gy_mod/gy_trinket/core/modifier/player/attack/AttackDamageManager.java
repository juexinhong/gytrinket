package com.gy_mod.gy_trinket.core.modifier.player.attack;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackDamageManager {

    private static final String BASE_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "attack_damage_base";
    private static final String PERCENT_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "attack_damage_percent";
    private static final UUID BASE_MODIFIER_UUID = UUID.fromString("d2e3f4a5-b6c7-8901-def0-123456789013");
    private static final UUID PERCENT_MODIFIER_UUID = UUID.fromString("e3f4a5b6-c7d8-9012-ef01-234567890124");

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
            AttributeModifier baseModifier = new AttributeModifier(BASE_MODIFIER_UUID, BASE_MODIFIER_NAME, attackDamageBase, AttributeModifier.Operation.ADDITION);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(PERCENT_MODIFIER_UUID, PERCENT_MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
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
            AttributeModifier baseModifier = new AttributeModifier(BASE_MODIFIER_UUID, BASE_MODIFIER_NAME, attackDamageBase, AttributeModifier.Operation.ADDITION);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(PERCENT_MODIFIER_UUID, PERCENT_MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
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
