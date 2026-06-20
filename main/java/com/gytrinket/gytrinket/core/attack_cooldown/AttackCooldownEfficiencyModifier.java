package com.gytrinket.gytrinket.core.attack_cooldown;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.shield.cooldown.CooldownContext;
import com.gytrinket.gytrinket.core.shield.cooldown.IShieldCooldownModifier;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class AttackCooldownEfficiencyModifier implements IShieldCooldownModifier {

    public static final String NAME = "attack_cooldown_efficiency";

    private static final String NAMESPACE = "attack_cooldown_efficiency";

    private static final String SHIELD_COOLDOWN_ATTR = "shield_cooldown_reduction_independent";

    private static final String RECOVERY_EFFICIENCY_ATTR = "recovery_efficiency_independent";

    private static final double EFFICIENCY_BONUS = 0.2;

    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;

    private static final Set<UUID> PLAYER_HAS_EFFICIENCY_ITEM = new HashSet<>();

    private static final Map<UUID, Boolean> PLAYER_ATTACK_COOLDOWN_STATE = new HashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean onPreTick(ShieldCooldownManager.CooldownData state, CooldownContext context) {
        UUID playerUUID = context.getPlayerUUID();

        if (!PLAYER_HAS_EFFICIENCY_ITEM.contains(playerUUID)) {
            return false;
        }

        Player player = getPlayer(playerUUID);
        if (player == null) {
            return false;
        }

        float attackStrength = player.getAttackStrengthScale(0.0f);
        boolean isInAttackCooldown = attackStrength < ATTACK_COOLDOWN_THRESHOLD;

        Boolean previousState = PLAYER_ATTACK_COOLDOWN_STATE.get(playerUUID);

        if (previousState != null && previousState.equals(isInAttackCooldown)) {
            return false;
        }

        PLAYER_ATTACK_COOLDOWN_STATE.put(playerUUID, isInAttackCooldown);

        if (!isInAttackCooldown) {
            AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR, EFFICIENCY_BONUS);
            AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR, EFFICIENCY_BONUS);
        } else {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, SHIELD_COOLDOWN_ATTR);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, RECOVERY_EFFICIENCY_ATTR);
        }

        return false;
    }

    private Player getPlayer(UUID playerUUID) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
        }
        return null;
    }

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
}
