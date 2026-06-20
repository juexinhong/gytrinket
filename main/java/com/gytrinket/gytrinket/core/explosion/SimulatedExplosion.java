package com.gytrinket.gytrinket.core.explosion;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.execute.ExecuteToggleManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * 模拟爆炸系统
 * <p>
 * 处理不破坏方块的模拟爆炸，对范围内实体施加伤害和击退。
 * <p>
 * 爆炸属性增幅：
 * - 如果提供了owner（玩家），会自动乘以该玩家的 explosion_damage 和 explosion_radius 属性组
 * - explosion_damage_percent + explosion_damage_independent → 爆炸伤害乘区
 * - explosion_radius_percent + explosion_radius_independent → 爆炸半径乘区
 * <p>
 * 击退速度公式：
 * - 基础速度 = MAX_BASE_SPEED × radius / (radius + HALF_MAX_RADIUS)（反比递减，边际收益递减）
 * - 距离衰减 = 1 - 0.7 × (距离 / 半径)
 * - 最终速度 = 基础速度 × 距离衰减
 * <p>
 * 击退方向：
 * - 距离爆心越近，方向越向竖直方向靠近（最多修正70%，爆心处100%）
 * - 竖直方向由实体相对爆心的位置决定：上方则向上，下方则向下
 * - 水平方向视为竖直方向
 * - 爆心处纯竖直
 */
public class SimulatedExplosion {

    /** 基础速度的理论上限（格/tick） */
    private static final double MAX_BASE_SPEED = 3.0;
    /** 达到最大基础速度一半时的爆炸半径 */
    private static final double HALF_MAX_RADIUS = 6.0;
    /** 竖直方向修正的最大比例（爆心处除外） */
    private static final double MAX_VERTICAL_BIAS = 0.5;

    /**
     * 执行模拟爆炸（无玩家owner，不应用爆炸属性增幅）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable) {
        execute(level, center, radius, damage, damageSource, entityFilter, resetInvulnerable, null);
    }

    /**
     * 执行模拟爆炸：对范围内实体施加伤害和击退
     * <p>
     * 如果提供了owner，会自动乘以该玩家的爆炸属性组增幅
     *
     * @param level             世界
     * @param center            爆炸中心
     * @param radius            爆炸半径（应用属性增幅前）
     * @param damage            爆炸伤害（应用属性增幅前）
     * @param damageSource      伤害源
     * @param entityFilter      实体过滤器（返回true的实体会受到伤害和击退）
     * @param resetInvulnerable 是否在伤害前后重置无敌时间
     * @param owner             爆炸归属玩家，用于应用爆炸属性增幅（可为null）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner) {
        if (level.isClientSide) return;

        // 应用玩家爆炸属性增幅
        if (owner != null) {
            double explosionDamageMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_damage");
            double explosionRadiusMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_radius");
            damage = (float) (damage * explosionDamageMultiplier);
            radius = radius * explosionRadiusMultiplier;
        }

        AABB aabb = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb);

        for (LivingEntity entity : entities) {
            if (!entityFilter.test(entity)) continue;

            double distance = entity.position().distanceTo(center);
            if (distance > radius) continue;

            if (resetInvulnerable) {
                entity.invulnerableTime = 0;
            }

            // 斩杀归属逻辑：根据 ExecuteToggleManager 决定是否将击杀归属玩家
            DamageSource actualSource = damageSource;
            if (owner != null && damageSource.getEntity() != null) {
                if (damage >= entity.getHealth() && ExecuteToggleManager.isExecuteEnabled(owner)) {
                    // 斩杀归属启用 + 足够斩杀：使用带玩家归属的伤害源
                    actualSource = entity.damageSources().explosion(null, owner);
                } else {
                    // 斩杀归属禁用 或 不足以斩杀：使用无攻击者的爆炸伤害源
                    actualSource = entity.damageSources().explosion(null);
                }
            }

            entity.hurt(actualSource, damage);

            if (resetInvulnerable) {
                entity.invulnerableTime = 0;
            }

            applyKnockback(entity, center, radius, distance);
        }
    }

    /**
     * 计算并施加击退速度
     * <p>
     * 速度公式：
     * - 基础速度 = MAX_BASE_SPEED × radius / (radius + HALF_MAX_RADIUS)（反比递减）
     * - 距离衰减 = 1 - 0.7 × (距离 / 半径)
     * - 最终速度 = 基础速度 × 距离衰减
     * <p>
     * 方向公式：
     * - 距离爆心越近，方向越向竖直方向靠近（最多修正70%，爆心处100%）
     * - 竖直方向由实体相对爆心位置决定：上方则向上，下方则向下
     * - 水平方向视为竖直方向
     * - 爆心处纯竖直
     */
    private static void applyKnockback(LivingEntity entity, Vec3 center, double radius, double distance) {
        Vec3 rawDirection;
        boolean atCenter = distance < 0.01;
        if (atCenter) {
            // 实体在爆心位置，根据实体相对爆心的竖直位置决定方向
            double yDiff = entity.getY() - center.y;
            rawDirection = new Vec3(0, yDiff >= 0 ? 1 : -1, 0);
        } else {
            rawDirection = entity.position().subtract(center).normalize();
            // 若方向水平（Y分量接近0），根据实体相对爆心的竖直位置决定竖直方向
            if (Math.abs(rawDirection.y) < 0.1) {
                double yDiff = entity.getY() - center.y;
                rawDirection = new Vec3(rawDirection.x, yDiff >= 0 ? 1.0 : -1.0, rawDirection.z).normalize();
            }
        }

        // 距离爆心越近，方向越向竖直方向靠近；爆心处100%，其余最多70%
        double distanceRatio = distance / radius;
        double rawBias = 1.0 - distanceRatio;
        double upBias = atCenter ? 1.0 : Math.min(rawBias, MAX_VERTICAL_BIAS);

        // 竖直方向：实体在爆心上方则向上，下方则向下
        double verticalSign = (entity.getY() - center.y) >= 0 ? 1.0 : -1.0;

        Vec3 direction = new Vec3(
                rawDirection.x * (1 - upBias),
                rawDirection.y * (1 - upBias) + verticalSign * upBias,
                rawDirection.z * (1 - upBias)
        ).normalize();

        // 基础速度使用反比公式，边际收益递减
        double baseSpeed = MAX_BASE_SPEED * radius / (radius + HALF_MAX_RADIUS);
        double speedFactor = 1.0 - 0.7 * distanceRatio;
        double speed = baseSpeed * speedFactor;

        Vec3 knockback = direction.scale(speed);
        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
        entity.hurtMarked = true;
    }
}
