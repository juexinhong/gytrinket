package com.gy_mod.gy_trinket.core.weapon;

/**
 * 武器基础属性聚合。
 * <p>
 * 统一从玩家属性读取武器的基础数据，供各类武器（焰矛等）使用，便于扩展。
 */
public record WeaponStats(
        double damage,
        double attackSpeed,
        double projectileSize,
        double projectileSpeed,
        int projectileCount,
        double spreadAngle,
        double homingAngle,
        double explosionRadius,
        double extraDamage
) {
}

