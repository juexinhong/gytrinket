package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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
        if (context.isAnySelfDamage() ||
            damageType == ModDamageTypes.BURN_DAMAGE ||
            damageType == ModDamageTypes.ON_FIRE_DAMAGE) {
            return;
        }

        // 基于当前伤害（可能已被前序处理器如适应性装甲减伤）向下钳制，
        // 只在伤害仍高于上限时钳制，避免把已减免的伤害重新抬高覆盖掉
        float currentDamage = context.getCurrentDamage();
        float maxDamage = (float) DefsManager.resolveMechanicValue(player.getServer(), playerUUID,
                "barrier_items", "barrier_max_damage", Config.BARRIER_MAX_DAMAGE.get().doubleValue());
        if (currentDamage > maxDamage) {
            context.setCurrentDamage(maxDamage);
        }
    }

    private boolean hasBarrierItem(Player player) {
        return PlayerStoreUtils.hasActiveItem(player, Config::isBarrierItem);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}

