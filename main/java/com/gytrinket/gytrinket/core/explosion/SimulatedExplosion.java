package com.gytrinket.gytrinket.core.explosion;

import com.gytrinket.gytrinket.core.damage.DamageSourcesCompat;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.damage.DamageAttributionWindow;
import com.gytrinket.gytrinket.core.damage.SecondaryDamageMerger;
import com.gytrinket.gytrinket.core.projectile.MultipartHitScan;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * - 最终速度 = 基础速度 × 距离衰减 × KNOCKBACK_MULTIPLIER
 * <p>
 * 击退方向：
 * - 距离爆心越近，方向越向竖直方向靠近（最多修正50%，爆心处100%）
 * - 竖直方向由实体相对爆心的位置决定：上方则向上，下方则向下
 * - 水平方向视为竖直方向
 * - 爆心处纯竖直
 */
public class SimulatedExplosion {

    /** 基础速度的理论上限（格/tick） */
    private static final double MAX_BASE_SPEED = 3.0;
    /** 达到最大基础速度一半时的爆炸半径 */
    private static final double HALF_MAX_RADIUS = 4.0;
    /** 竖直方向修正的最大比例（爆心处除外） */
    private static final double MAX_VERTICAL_BIAS = 0.5;
    /** 击退力倍率 */
    private static final double KNOCKBACK_MULTIPLIER = 0.5;

    /**
     * 执行模拟爆炸（无玩家owner，不应用爆炸属性增幅）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable) {
        execute(level, center, radius, damage, damageSource, entityFilter, resetInvulnerable, null);
    }

    /**
     * 执行模拟爆炸（不应用爆炸属性增幅，自定义斥力倍率）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner) {
        execute(level, center, radius, damage, damageSource, entityFilter, resetInvulnerable, owner, -1.0);
    }

    /**
     * 执行模拟爆炸：对范围内实体施加伤害和击退
     * <p>
     * 如果提供了owner，会自动乘以该玩家的爆炸属性组增幅
     *
     * @param level                      世界
     * @param center                     爆炸中心
     * @param radius                     爆炸半径（应用属性增幅前）
     * @param damage                     爆炸伤害（应用属性增幅前）
     * @param damageSource               伤害源
     * @param entityFilter               实体过滤器（返回true的实体会受到伤害和击退）
     * @param resetInvulnerable          是否在伤害前后重置无敌时间
     * @param owner                      爆炸归属玩家，用于应用爆炸属性增幅（可为null）
     * @param knockbackMultiplierOverride 额外斥力倍率修正（<0表示使用默认值KNOCKBACK_MULTIPLIER）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner, double knockbackMultiplierOverride) {
        execute(level, center, radius, damage, damageSource, entityFilter, resetInvulnerable, owner, knockbackMultiplierOverride, null);
    }

    /**
     * 执行模拟爆炸（带次级伤害合并类型）
     *
     * @param mergeType 合并类型 ID（null = 不合并，立即施加；非 null 时同类型伤害在时间窗口内累积合并）
     */
    public static void execute(Level level, Vec3 center, double radius, float damage,
                               DamageSource damageSource, Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner, double knockbackMultiplierOverride,
                               String mergeType) {
        if (level.isClientSide) return;

        // 应用玩家爆炸属性增幅
        if (owner != null) {
            double explosionDamageMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_damage");
            double explosionRadiusMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_radius");
            damage = (float) (damage * explosionDamageMultiplier);
            radius = radius * explosionRadiusMultiplier;
        }

        // 统一广播模拟爆炸贴图特效（替代原版爆炸粒子）
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.gytrinket.gytrinket.network.NetworkHandler.sendSimulatedExplosionFX(serverLevel, center, radius);
        }

        AABB aabb = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        // 多体节：体节不注册进 level 实体列表，只查父实体会漏掉伸出的头/颈/翅/尾/节段
        // （娜迦父实体只有头部 2×2，身体节段拖出 20+ 格）；每个父实体只结算一次，
        // 命中箱取离爆心最近的那个（父实体 AABB 与原版爆炸一致恒参与）
        final double queryRadius = radius;
        List<MultipartHitScan.Hit> hits = MultipartHitScan.collectInRegion(level, aabb, center,
                e -> entityFilter.test((LivingEntity) e),
                box -> box.getCenter().distanceToSqr(center) <= queryRadius * queryRadius
                        || intersectsExplosionCircle(box, center, queryRadius),
                false);

        // radius 可能被属性增幅重赋值，lambda 中使用 final 副本
        final double effectiveRadius = radius;

        for (MultipartHitScan.Hit hit : hits) {
            LivingEntity entity = hit.target();
            Entity hitbox = hit.hitbox();
            // 命中父实体时沿用原「脚底坐标」距离（击退衰减与原实现一致）；
            // 命中体节时用体节碰撞箱中心
            Vec3 referencePos = hitbox == entity ? entity.position() : hitbox.getBoundingBox().getCenter();
            double distance = referencePos.distanceTo(center);

            // 仅由水平圆命中的目标 distance 可能超过 radius，钳制以免击退衰减计算出负值
            double effectiveDistance = Math.min(distance, radius);

            // 体节命中时合并键带上体节 ID，保证合并批次与施加时命中的碰撞箱一致
            String shellMergeType = mergeType == null || hitbox == entity
                    ? mergeType
                    : mergeType + ":part:" + hitbox.getId();

            if (shellMergeType == null) {
                applyExplosionHit(hitbox, entity, center, radius, effectiveDistance, damage, damageSource,
                        owner, resetInvulnerable, knockbackMultiplierOverride);
            } else {
                // 爆炸伤害：归属玩家时第一次立即结算，随后进入 0.5 秒收集期
                SecondaryDamageMerger.accumulateExplosion(entity, shellMergeType, damage, owner != null, (target, mergedDamage) ->
                        applyExplosionHit(hitbox, target, center, effectiveRadius, effectiveDistance, mergedDamage, damageSource,
                                owner, resetInvulnerable, knockbackMultiplierOverride));
            }
        }
    }

    /**
     * 范围修复：检查爆心高度处半径 radius 的水平圆是否与目标碰撞箱相交
     * <p>
     * 圆位于爆心高度的水平面（y = center.y）上，相交要求：
     * 1. 碰撞箱跨越爆心高度平面（box.minY ≤ center.y ≤ box.maxY）；
     * 2. 该平面内圆与碰撞箱截面（XZ 矩形）相交——采用"矩形上距圆心最近点"测试，
     * 将爆心水平坐标钳制到碰撞箱范围内得到最近点，最近点与爆心的水平距离 ≤ radius 即相交。
     * 垂直范围由调用处的 AABB 预筛选（爆心 ± radius 立方体）兜底，不会波及过远目标。
     */
    private static boolean intersectsExplosionCircle(AABB box, Vec3 center, double radius) {
        // 碰撞箱未跨越爆心高度平面，水平圆与之不相交
        if (center.y < box.minY || center.y > box.maxY) {
            return false;
        }
        double closestX = Math.max(box.minX, Math.min(center.x, box.maxX));
        double closestZ = Math.max(box.minZ, Math.min(center.z, box.maxZ));
        double dx = closestX - center.x;
        double dz = closestZ - center.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * 对单个实体施加爆炸命中：重置无敌时间、归属窗口、伤害、击退
     * <p>
     * {@code hitbox} 是实际命中箱（体节命中时为体节），伤害经它结算以保留体节倍率；
     * {@code entity} 是父实体，用于无敌帧、归属窗口与击退。
     */
    private static void applyExplosionHit(Entity hitbox, LivingEntity entity, Vec3 center, double radius, double distance,
                                          float damage, DamageSource damageSource, Player owner,
                                          boolean resetInvulnerable, double knockbackMultiplierOverride) {
        if (resetInvulnerable) {
            entity.invulnerableTime = 0;
        }

        // 伤害源恒不带玩家（避免非致死时对玩家触发仇恨）：
        // 预估致死时由 ExecuteDamageHandler 取消原伤害并改用归属玩家的
        // 斩杀伤害源（execute_damage）完成最后一击
        boolean windowed = owner != null && damageSource.getEntity() != null;
        DamageSource actualSource = windowed ? DamageSourcesCompat.explosion(null, null) : damageSource;
        if (windowed) {
            DamageAttributionWindow.mark(entity, owner);
        }
        try {
            hitbox.hurt(actualSource, damage);
        } finally {
            if (windowed) {
                DamageAttributionWindow.unmark(entity);
            }
        }

        if (resetInvulnerable) {
            entity.invulnerableTime = 0;
        }

        applyKnockback(entity, center, radius, distance, knockbackMultiplierOverride);
    }

    /**
     * 计算并施加击退速度
     * <p>
     * 速度公式：
     * - 基础速度 = MAX_BASE_SPEED × radius / (radius + HALF_MAX_RADIUS)（反比递减）
     * - 距离衰减 = 1 - 0.7 × (距离 / 半径)
     * - 最终速度 = 基础速度 × 距离衰减 × KNOCKBACK_MULTIPLIER
     * <p>
     * 方向公式：
     * - 距离爆心越近，方向越向竖直方向靠近（最多修正70%，爆心处100%）
     * - 竖直方向由实体相对爆心位置决定：上方则向上，下方则向下
     * - 水平方向视为竖直方向
     * - 爆心处纯竖直
     */
    private static void applyKnockback(LivingEntity entity, Vec3 center, double radius, double distance, double knockbackMultiplierOverride) {
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
        double upBias = atCenter ? 1.0 : rawBias * MAX_VERTICAL_BIAS;

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
        double effectiveMultiplier = knockbackMultiplierOverride >= 0 ? knockbackMultiplierOverride : KNOCKBACK_MULTIPLIER;
        double speed = baseSpeed * speedFactor * effectiveMultiplier;

        Vec3 knockback = direction.scale(speed);
        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
        entity.hurtMarked = true;
    }
}

