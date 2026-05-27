package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class BarrierHandler implements DamageHandler {
    private static final int PRIORITY = 30;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();
        UUID playerUUID = player.getUUID();
        LivingEntity attackedEntity = context.getAttackedEntity();

        if (!hasBarrierItem(player)) {
            return;
        }

        if (ShieldManager.getCurrentShield(playerUUID) <= 0) {
            return;
        }

        boolean shouldApplyBarrier = false;

        if (!ShieldTransferManager.hasTransferredShield(playerUUID)) {
            shouldApplyBarrier = true;
        } else {
            if (ShieldTransferManager.isEntityProtected(playerUUID, attackedEntity.getUUID())) {
                shouldApplyBarrier = true;
            }
        }

        if (!shouldApplyBarrier) {
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.BURN_DAMAGE ||
            damageType == ModDamageTypes.ON_FIRE_DAMAGE) {
            return;
        }

        float originalDamage = context.getOriginalDamage();
        if (originalDamage > 5.0f) {
            context.setCurrentDamage(5.0f);
        }
    }

    private boolean hasBarrierItem(Player player) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(player.getUUID(), stack) && Config.isBarrierItem(stack.getItem())) {
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
