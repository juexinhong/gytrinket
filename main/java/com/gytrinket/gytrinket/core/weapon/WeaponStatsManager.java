package com.gytrinket.gytrinket.core.weapon;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;

import java.util.UUID;

/**
 * 武器属性管理器：从玩家属性聚合 {@link WeaponStats}。
 * <p>
 * 基础属性组：
 * <ul>
 *   <li>weapon_damage（BASE）— 武器伤害</li>
 *   <li>weapon_attack_speed（PERCENT）— 武器攻击速度</li>
 *   <li>weapon_projectile_size（PERCENT）— 武器子弹大小</li>
 *   <li>weapon_projectile_speed（PERCENT）— 武器子弹速度</li>
 *   <li>weapon_projectile_count（BASE）— 武器子弹数量</li>
 *   <li>weapon_spread_angle（PERCENT）— 武器子弹散射角度</li>
 *   <li>weapon_homing_angle（PERCENT）— 武器子弹追踪角度</li>
 *   <li>weapon_explosion_radius（PERCENT）— 武器爆炸半径</li>
 *   <li>weapon_extra_damage（BASE）— 武器子弹附加伤害</li>
 * </ul>
 */
public class WeaponStatsManager {

    private WeaponStatsManager() {}

    /**
     * 聚合玩家当前武器基础属性。
     */
    public static WeaponStats getWeaponStats(UUID playerUUID) {
        return new WeaponStats(
                AttributeManager.getGroupAttribute(playerUUID, "weapon_damage"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_attack_speed"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_projectile_size"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_projectile_speed"),
                (int) Math.max(1, Math.round(AttributeManager.getGroupAttribute(playerUUID, "weapon_projectile_count"))),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_spread_angle"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_homing_angle"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_explosion_radius"),
                AttributeManager.getGroupAttribute(playerUUID, "weapon_extra_damage")
        );
    }
}
