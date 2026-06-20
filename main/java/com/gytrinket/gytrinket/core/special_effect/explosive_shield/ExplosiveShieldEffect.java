package com.gytrinket.gytrinket.core.special_effect.explosive_shield;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.explosion.SimulatedExplosion;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ExplosiveShieldEffect {

    @SubscribeEvent
    public static void onShieldBreak(ShieldBreakEvent event) {
        Player player = event.getPlayer();

        if (!hasExplosiveShieldItem(player)) {
            return;
        }

        List<LivingEntity> effectCenters;
        if (ShieldTransferManager.hasTransferredShield(player.getUUID())) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = Config.EXPLOSIVE_SHIELD_RADIUS.get();
        double radius = baseRadius * shieldEffectRadius;

        double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
        double baseDamage = Config.EXPLOSIVE_SHIELD_DAMAGE.get();
        float damage = (float)(baseDamage * shieldEffect);

        for (LivingEntity effectCenter : effectCenters) {
            if (effectCenter == null || !effectCenter.isAlive()) {
                continue;
            }

            if (effectCenter.level() instanceof ServerLevel serverLevel) {
                NetworkHandler.sendExplosiveShieldFlashToAll(serverLevel, effectCenter);
            }

            DamageSource damageSource = effectCenter.damageSources().explosion(effectCenter, player);

            SimulatedExplosion.execute(
                    effectCenter.level(),
                    effectCenter.position(),
                    radius,
                    damage,
                    damageSource,
                    entity -> entity instanceof Mob mob && !mob.isDeadOrDying()
                            && HostileTargetManager.shouldAttackPlayer(mob, player),
                    false,
                    player
            );
        }
    }

    private static boolean hasExplosiveShieldItem(Player player) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(player.getUUID(), stack) && Config.isExplosiveShieldItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}