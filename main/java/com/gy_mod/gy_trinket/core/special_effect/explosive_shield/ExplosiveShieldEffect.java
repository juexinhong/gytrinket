package com.gy_mod.gy_trinket.core.special_effect.explosive_shield;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.event.ShieldBreakEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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

            AABB aabb = new AABB(
                effectCenter.getX() - radius, effectCenter.getY() - radius, effectCenter.getZ() - radius,
                effectCenter.getX() + radius, effectCenter.getY() + radius, effectCenter.getZ() + radius
            );

            List<Entity> entities = effectCenter.level().getEntities(effectCenter, aabb, entity ->
                entity instanceof Mob mob && !mob.isDeadOrDying()
            );

            DamageSource damageSource = effectCenter.damageSources().explosion(effectCenter, player);

            for (Entity entity : entities) {
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.hurt(damageSource, damage);
                }
            }
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