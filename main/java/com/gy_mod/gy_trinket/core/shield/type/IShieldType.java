package com.gy_mod.gy_trinket.core.shield.type;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public interface IShieldType {

    String getName();

    boolean isCompatible();

    default void onApplied(Player player, Collection<ShieldTypeData> activeTypes) {}

    default void onRemoved(Player player) {}

    default void onTick(Player player) {}

    default boolean shouldReflectProjectile(Player player, Projectile projectile) {
        return false;
    }

    default void onProjectileReflected(Player player, Projectile projectile, float damage) {}

    default float getReflectedProjectileDamageMultiplier(int projectileId) {
        return 1.0f;
    }

    default boolean isReflectedProjectile(int projectileId) {
        return false;
    }

    default void onReflectedProjectileHit(Player attacker, LivingEntity target, Projectile projectile) {}

    public record ShieldTypeData(IShieldType type, ItemStack source, boolean active) {
        public ShieldTypeData withActive(boolean active) {
            return new ShieldTypeData(this.type, this.source, active);
        }
    }

    interface ProjectileReflector {
        boolean shouldReflectProjectile(Player player, Projectile projectile);
        void onProjectileReflected(Player player, Projectile projectile, float damage);
        float getReflectedProjectileDamageMultiplier(int projectileId);
        boolean isReflectedProjectile(int projectileId);
        void onReflectedProjectileHit(Player attacker, LivingEntity target, Projectile projectile);
    }
}
