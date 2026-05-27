package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BinaryProtocolHandler implements DamageHandler {

    private static final int PRIORITY = 90;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();

        if (!hasBinaryProtocolItem(player)) {
            return;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);

        if (damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return;
        }

        float currentDamage = context.getCurrentDamage();
        if (currentDamage <= 0) {
            return;
        }
        float splitDamage = currentDamage / 2.0f;

        boolean isPlayerSelfDamage = (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE);

        DamageSource protocolDamageSource;
        boolean protocolTargetIsShield;
        if (isPlayerSelfDamage) {
            protocolDamageSource = ModDamageTypes.getProtocolShieldSelfDamageSource(player.level());
            protocolTargetIsShield = true;
        } else {
            protocolDamageSource = ModDamageTypes.getProtocolPlayerSelfDamageSource(player.level());
            protocolTargetIsShield = false;
        }

        float actualProtocolDamage = splitDamage;
        float remainingDamage = 0;

        if (protocolTargetIsShield) {
            double currentShield = ShieldManager.getCurrentShield(player.getUUID());
            if (splitDamage > currentShield - 1) {
                actualProtocolDamage = (float) (currentShield - 1);
                remainingDamage = splitDamage - actualProtocolDamage;
            }
        } else {
            float currentHealth = player.getHealth();
            if (splitDamage > currentHealth - 1) {
                actualProtocolDamage = currentHealth - 1;
                remainingDamage = splitDamage - actualProtocolDamage;
            }
        }

        if (actualProtocolDamage > 0) {
            player.hurt(protocolDamageSource, actualProtocolDamage);
        }

        context.setCurrentDamage(splitDamage + remainingDamage);
    }

    private boolean hasBinaryProtocolItem(Player player) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(player.getUUID(), stack) && Config.isBinaryProtocolItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}