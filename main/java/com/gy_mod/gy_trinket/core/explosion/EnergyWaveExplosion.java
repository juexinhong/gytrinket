package com.gy_mod.gy_trinket.core.explosion;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 能量波爆炸
 * <p>
 * 以爆心为基点，沿溅射方向形成锥形伤害区域（与视觉外焰层几何一致）。
 * 锥形参数：长度 = 有效溅射长度，底部半宽 = 长度 × 外焰层宽长比 × (1 + 容差提升)。
 * 外焰层宽长比 = 5 / (12 × 0.7)，与视觉渲染比例一致。
 * <p>
 * 长度极限机制：
 * - 射线检测长度上限为 {@link #SPLASH_LENGTH_CAP} 格
 * - 超出极限值后，宽度容差百分比提升：每超出1格提升10%，最多100%
 * <p>
 * 爆炸属性增幅（如果提供了owner）：
 * - explosion_damage 属性组 → 爆炸伤害乘区
 * - explosion_radius 属性组 → 溅射长度乘区
 */
public class EnergyWaveExplosion {

    /** 射线检测长度极限（格） */
    private static final double SPLASH_LENGTH_CAP = 15.0;
    /** 超出极限后每格容差提升比例 */
    private static final double TOLERANCE_BONUS_PER_BLOCK = 0.05;
    /** 容差提升上限 */
    private static final double MAX_TOLERANCE_BONUS = 0.5;
    /** 外焰层宽长比，与视觉渲染比例一致：outerHW / outerLen = (centerHW*5) / (centerLen*0.7) = (1/12*5) / 0.7 = 5/8.4 */
    private static final double OUTER_RATIO = 5.0 / (12.0 * 0.7);

    /**
     * 执行能量波爆炸（无玩家owner，不应用爆炸属性增幅，显示视觉特效）
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                               float damage, DamageSource damageSource,
                               Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable) {
        return execute(level, center, splashDirection, splashLength, damage, damageSource,
                entityFilter, resetInvulnerable, null, null, true);
    }

    /**
     * 执行能量波爆炸（显示视觉特效）
     *
     * @param level           世界
     * @param center          爆心
     * @param splashDirection 溅射方向（无需归一化）
     * @param splashLength    溅射长度（应用属性增幅前）
     * @param damage          爆炸伤害（应用属性增幅前）
     * @param damageSource    伤害源
     * @param entityFilter    实体过滤器（返回true的实体会受到伤害）
     * @param resetInvulnerable 是否在伤害前后重置无敌时间
     * @param owner           爆炸归属玩家，用于应用爆炸属性增幅（可为null）
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                               float damage, DamageSource damageSource,
                               Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner) {
        return execute(level, center, splashDirection, splashLength, damage, damageSource,
                entityFilter, resetInvulnerable, owner, null, true);
    }

    /**
     * 执行能量波爆炸（带击中后回调，显示视觉特效）
     *
     * @param postHitCallback  每个被击中的实体伤害后调用（可为null）
     * @return 是否击中了任何实体
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                               float damage, DamageSource damageSource,
                               Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner,
                               Consumer<LivingEntity> postHitCallback) {
        return execute(level, center, splashDirection, splashLength, damage, damageSource,
                entityFilter, resetInvulnerable, owner, postHitCallback, true);
    }

    /**
     * 执行能量波爆炸（完整参数）
     *
     * @param showVisual      是否触发能量波爆炸视觉特效（蜂群自带能量波时应传false避免叠加）
     * @param postHitCallback  每个被击中的实体伤害后调用（可为null）
     * @return 是否击中了任何实体
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                               float damage, DamageSource damageSource,
                               Predicate<LivingEntity> entityFilter,
                               boolean resetInvulnerable, Player owner,
                               Consumer<LivingEntity> postHitCallback,
                               boolean showVisual) {
        if (level.isClientSide) return false;

        // 应用玩家爆炸属性增幅
        if (owner != null) {
            double explosionDamageMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_damage");
            double explosionRadiusMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_radius");
            damage = (float) (damage * explosionDamageMultiplier);
            splashLength = splashLength * explosionRadiusMultiplier;
        }

        Vec3 direction = splashDirection.normalize();

        // 长度极限机制：射线检测长度上限为15格
        double effectiveLength = Math.min(splashLength, SPLASH_LENGTH_CAP);
        // 超出极限值后宽度容差百分比提升：每超出1格提升10%，最多100%
        double excessLength = Math.max(0, splashLength - SPLASH_LENGTH_CAP);
        double toleranceBonus = Math.min(MAX_TOLERANCE_BONUS, excessLength * TOLERANCE_BONUS_PER_BLOCK);

        Vec3 rayEnd = center.add(direction.scale(effectiveLength));

        // 锥形底部半宽（与视觉外焰层比例一致）
        double baseHalfWidth = effectiveLength * OUTER_RATIO * (1 + toleranceBonus);

        // AABB用于初始实体查询，膨胀量覆盖锥形最大半宽+实体体积
        double inflation = baseHalfWidth + 1.0;
        AABB aabb = new AABB(
                Math.min(center.x, rayEnd.x) - inflation,
                Math.min(center.y, rayEnd.y) - inflation,
                Math.min(center.z, rayEnd.z) - inflation,
                Math.max(center.x, rayEnd.x) + inflation,
                Math.max(center.y, rayEnd.y) + inflation,
                Math.max(center.z, rayEnd.z) + inflation
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb);

        boolean hitAny = false;
        for (LivingEntity entity : entities) {
            if (!entityFilter.test(entity)) continue;

            if (!isInEnergyWave(entity, center, direction, effectiveLength, baseHalfWidth)) continue;

            if (resetInvulnerable) {
                entity.invulnerableTime = 0;
            }

            // 伤害归属逻辑：
            // - 默认：使用原始 damageSource（蜂群攻击时 getEntity()=蜂群 → 仇恨归蜂群）
            // - 斩杀时（伤害>=生命值 且 斩杀模式开启）：切换为玩家归属（玩家获得击杀判定）
            DamageSource actualSource = damageSource;
            if (owner != null && damageSource.getEntity() != null
                    && damage >= entity.getHealth() && ExecuteToggleManager.isExecuteEnabled(owner)) {
                actualSource = entity.damageSources().explosion(null, owner);
            }

            // 能量波爆炸不产生击退（参照灼烧系统实现）
            KnockbackManager.markNoKnockback(entity.getUUID());

            entity.hurt(actualSource, damage);

            if (resetInvulnerable) {
                entity.invulnerableTime = 0;
            }

            if (postHitCallback != null) {
                postHitCallback.accept(entity);
            }

            hitAny = true;
            // 能量波爆炸不产生击退
        }

        // 触发能量波爆炸视觉特效
        if (showVisual && level instanceof ServerLevel serverLevel) {
            NetworkHandler.sendEnergyWaveExplosionToAll(serverLevel, center, direction, splashLength);
        }

        return hitAny;
    }

    /**
     * 判断实体是否在能量波锥形范围内
     * <p>
     * 使用外焰层锥形几何体作为检测范围，与视觉渲染形状一致。
     * 锥形底部半宽 = effectiveLength × OUTER_RATIO × (1 + toleranceBonus)，
     * 锥形半径沿射线方向线性递减至0。
     * <p>
     * 判定方法：将实体AABB中心投影到锥形轴线上，计算该处的锥形半径，
     * 若实体中心到轴线的垂直距离小于锥形半径+AABB半径，则命中。
     * AABB半径使用半对角线长度，确保不会漏掉部分在锥形内的实体。
     *
     * @param entity          目标实体
     * @param center          爆心（锥形顶点）
     * @param direction       溅射方向（归一化）
     * @param effectiveLength 有效溅射长度（锥形长度）
     * @param baseHalfWidth   锥形底部半宽（已含容差提升）
     */
    private static boolean isInEnergyWave(LivingEntity entity, Vec3 center, Vec3 direction,
                                          double effectiveLength, double baseHalfWidth) {
        if (effectiveLength <= 0) return false;

        AABB entityBox = entity.getBoundingBox();
        Vec3 entityCenter = entityBox.getCenter();

        // 实体中心到爆心的向量
        Vec3 toEntity = entityCenter.subtract(center);
        double alongRay = toEntity.dot(direction);

        // AABB半对角线（保守的各方向半径估计）
        double halfX = entityBox.getXsize() / 2;
        double halfY = entityBox.getYsize() / 2;
        double halfZ = entityBox.getZsize() / 2;
        double aabbRadius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

        // 沿射线方向：实体必须在锥形范围内（考虑AABB半径）
        if (alongRay + aabbRadius < 0 || alongRay - aabbRadius > effectiveLength) {
            return false;
        }

        // 锥形在沿射线距离t处的半径：baseHalfWidth * (1 - t / effectiveLength)
        double clampedAlong = Math.max(0, Math.min(alongRay, effectiveLength));
        double coneRadius = baseHalfWidth * (1 - clampedAlong / effectiveLength);

        // 实体中心到轴线的垂直距离
        double perpDistSq = toEntity.lengthSqr() - alongRay * alongRay;
        double perpDist = Math.sqrt(Math.max(0, perpDistSq));

        // 垂直距离在锥形半径内（加上AABB半径容差，确保不漏判）
        return perpDist < coneRadius + aabbRadius;
    }
}
