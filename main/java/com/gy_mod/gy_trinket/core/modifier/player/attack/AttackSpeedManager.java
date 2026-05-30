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
public class AttackSpeedManager {

    private static final String MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "attack_speed";
    private static final UUID MODIFIER_UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-012345678902");

    private static final Map<UUID, Double> PLAYER_ATTACK_SPEED_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double attackSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_percent");
        double attackSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_independent");

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

        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("attack_speed_percent") && !attrName.equals("attack_speed_independent")) {
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

        double attackSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_percent");
        double attackSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_independent");

        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.ATTACK_SPEED);
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
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
        PLAYER_ATTACK_SPEED_MAP.remove(player.getUUID());
    }

    public static double getPlayerAttackSpeed(UUID playerUUID) {
        return PLAYER_ATTACK_SPEED_MAP.getOrDefault(playerUUID, 4.0);
    }

    public static void clearAllData() {
        PLAYER_ATTACK_SPEED_MAP.clear();
    }
}
