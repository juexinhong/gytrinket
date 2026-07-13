package com.gy_mod.gy_trinket.core.attack_cooldown;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class AttackCooldownEfficiencyModifier {

    private static final String NAMESPACE = "attack_cooldown_efficiency";

    private static final String SHIELD_COOLDOWN_ATTR = "shield_cooldown_reduction_independent";

    private static final String RECOVERY_EFFICIENCY_ATTR = "recovery_efficiency_independent";

    private static final double EFFICIENCY_BONUS = 0.2;

    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;

    private static final Set<UUID> PLAYER_HAS_EFFICIENCY_ITEM = new HashSet<>();

    private static final Map<UUID, Boolean> PLAYER_ATTACK_COOLDOWN_STATE = new HashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            PLAYER_HAS_EFFICIENCY_ITEM.remove(playerUUID);
            PLAYER_ATTACK_COOLDOWN_STATE.remove(playerUUID);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR);
            return;
        }

        boolean hasEfficiencyItem = false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack) && Config.isAttackCooldownEfficiencyItem(stack.getItem())) {
                hasEfficiencyItem = true;
                break;
            }
        }

        if (hasEfficiencyItem) {
            PLAYER_HAS_EFFICIENCY_ITEM.add(playerUUID);
        } else {
            PLAYER_HAS_EFFICIENCY_ITEM.remove(playerUUID);
            PLAYER_ATTACK_COOLDOWN_STATE.remove(playerUUID);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        if (!PLAYER_HAS_EFFICIENCY_ITEM.contains(playerUUID)) {
            return;
        }

        float attackStrength = player.getAttackStrengthScale(0.0f);
        boolean isInAttackCooldown = attackStrength < ATTACK_COOLDOWN_THRESHOLD;

        Boolean previousState = PLAYER_ATTACK_COOLDOWN_STATE.get(playerUUID);
        if (previousState != null && previousState.equals(isInAttackCooldown)) {
            return;
        }

        PLAYER_ATTACK_COOLDOWN_STATE.put(playerUUID, isInAttackCooldown);

        if (!isInAttackCooldown) {
            AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR, EFFICIENCY_BONUS);
            AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR, EFFICIENCY_BONUS);
        } else {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR);
        }
    }
}
