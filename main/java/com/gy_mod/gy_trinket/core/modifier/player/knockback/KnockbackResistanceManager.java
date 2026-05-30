package com.gy_mod.gy_trinket.core.modifier.player.knockback;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class KnockbackResistanceManager {

    private static final String MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "knockback_resistance";
    private static final UUID MODIFIER_UUID = UUID.fromString("a5b6c7d8-e9f0-1234-0123-456789012346");

    private static final Map<UUID, Double> PLAYER_KNOCKBACK_RESISTANCE_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double knockbackResistance = AttributeManager.getPlayerAttribute(playerUUID, "knockback_resistance");

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

        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (knockbackResistance != 0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, knockbackResistance, AttributeModifier.Operation.ADDITION);
            attribute.addTransientModifier(modifier);
        }

        PLAYER_KNOCKBACK_RESISTANCE_MAP.put(playerUUID, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        double knockbackResistance = getPlayerKnockbackResistance(player.getUUID());
        if (knockbackResistance < 0) {
            float multiplier = (float) (1.0 - knockbackResistance);
            event.setStrength(event.getStrength() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.KNOCKBACK_RESISTANCE);
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
        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        ModifierHelper.removeAllModModifiers(attribute);
        PLAYER_KNOCKBACK_RESISTANCE_MAP.remove(player.getUUID());
    }

    public static double getPlayerKnockbackResistance(UUID playerUUID) {
        return PLAYER_KNOCKBACK_RESISTANCE_MAP.getOrDefault(playerUUID, 0.0);
    }

    public static void clearAllData() {
        PLAYER_KNOCKBACK_RESISTANCE_MAP.clear();
    }
}
