package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 无人机相关伤害来源
 */
public class ModDamageSources {

    public static DamageSource droneBullet(Level level, Entity bullet, @Nullable LivingEntity attacker) {
        return ModDamageTypes.getDroneBulletDamageSource(level, bullet, attacker);
    }
}