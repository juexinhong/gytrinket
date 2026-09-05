package com.gytrinket.gytrinket.core.modifier.player.knockback;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class KnockbackResistanceManager {

    
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "knockback_resistance");

    private static final Map<UUID, Double> PLAYER_KNOCKBACK_RESISTANCE_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double knockbackResistance = AttributeManager.getPlayerAttribute(playerUUID, "knockback_resistance");

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

        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        // 负值不挂原版修饰符（原版属性 clamp 到 [0,1]，负修饰符只会错误抵消玩家装备自带的原版抗性），
        // 负值专门通过 LivingKnockBackEvent 提升玩家被击退的力度
        if (knockbackResistance > 0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, knockbackResistance, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(modifier);
        }

        // 存本模组账本值（非原版属性值）：原版属性被 clamp 到 [0,1]，负值读取不到
        PLAYER_KNOCKBACK_RESISTANCE_MAP.put(playerUUID, knockbackResistance);
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
