package com.gy_mod.gy_trinket.core.explosion;

import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.damage.SecondaryDamageMerger;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 能量波爆炸
 * <p>
 * 以爆心为基点，沿溅射方向形成长方形伤害区域（与能量波视觉几何一致）。
 * 长方形参数：长度 = 有效溅射长度（极限 {@link #SPLASH_LENGTH_CAP} 格），
 * 宽度 = 长度/16（宽度极限 = 长度/12），恒定半宽，含身后判定。
 * <p>
 * 长度极限机制：攻击范围极限 20 格，同时也是能量波长度极限。
 * <p>
 * 爆炸属性增幅（如果提供了owner）：
 * - explosion_damage 属性组 → 爆炸伤害乘区
 * - explosion_radius 属性组 → 溅射长度乘区
 */
public class EnergyWaveExplosion {

    /** 攻击范围/能量波长度极限（格） */
    private static final double SPLASH_LENGTH_CAP = 20.0;

    /**
     * 执行能量波爆炸（无玩家owner，不应用爆炸属性增幅，显示视觉特效）
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                                float damage, DamageSource damageSource,
                                Predicate<LivingEntity> entityFilter,
                                boolean resetInvulnerable) {
        return execute(level, center, splashDirection, splashLength, damage, damageSource,
                entityFilter, resetInvulnerable, null, null, true, 0.0, null);
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
                entityFilter, resetInvulnerable, owner, null, true, 0.0, null);
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
                entityFilter, resetInvulnerable, owner, postHitCallback, true, 0.0, null);
    }

    /**
     * 执行能量波爆炸（带身后判定）
     *
     * @param behindLength    爆心身后判定距离（格），沿溅射反方向延伸的圆柱检测范围
     * @return 是否击中了任何实体
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                                float damage, DamageSource damageSource,
                                Predicate<LivingEntity> entityFilter,
                                boolean resetInvulnerable, Player owner,
                                Consumer<LivingEntity> postHitCallback,
                                boolean showVisual, double behindLength) {
        return execute(level, center, splashDirection, splashLength, damage, damageSource,
                entityFilter, resetInvulnerable, owner, postHitCallback, showVisual, behindLength, null);
    }

    /**
     * 执行能量波爆炸（带合并类型；mergeType 非 null 时同类型伤害在时间窗口内累积合并）
     */
    public static boolean execute(Level level, Vec3 center, Vec3 splashDirection, double splashLength,
                                float damage, DamageSource damageSource,
                                Predicate<LivingEntity> entityFilter,
                                boolean resetInvulnerable, Player owner,
                                Consumer<LivingEntity> postHitCallback,
                                boolean showVisual, double behindLength, String mergeType) {
        if (level.isClientSide) return false;

        // 应用玩家爆炸属性增幅
        if (owner != null) {
            double explosionDamageMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_damage");
            double explosionRadiusMultiplier = AttributeManager.getGroupAttribute(owner.getUUID(), "explosion_radius");
            damage = (float) (damage * explosionDamageMultiplier);
            splashLength = splashLength * explosionRadiusMultiplier;
        }

        Vec3 direction = splashDirection.normalize();

        // 长度极限机制：攻击范围/能量波长度上限20格
        double effectiveLength = Math.min(splashLength, SPLASH_LENGTH_CAP);

        Vec3 rayEnd = center.add(direction.scale(effectiveLength));

        // 长方形半宽：宽度 = 长度/16（宽度极限 = 长度/12，与能量波机制一致）
        double baseHalfWidth = EnergyWaveVisualManager.waveWidth(effectiveLength);

        // 身后起点（沿溅射反方向延伸behindLength格）
        Vec3 behindStart = center.subtract(direction.scale(behindLength));

        // AABB用于初始实体查询，膨胀量覆盖长方形最大半宽+实体体积
        double inflation = baseHalfWidth + 1.0;
        AABB aabb = new AABB(
                Math.min(behindStart.x, rayEnd.x) - inflation,
                Math.min(behindStart.y, rayEnd.y) - inflation,
                Math.min(behindStart.z, rayEnd.z) - inflation,
                Math.max(behindStart.x, rayEnd.x) + inflation,
                Math.max(behindStart.y, rayEnd.y) + inflation,
                Math.max(behindStart.z, rayEnd.z) + inflation
        );

        List<LivingEntity> entities = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, aabb));

        boolean[] hitAnyRef = {false};
        for (LivingEntity entity : entities) {
            if (!entityFilter.test(entity)) continue;

            if (!isInEnergyWave(entity, center, direction, effectiveLength, baseHalfWidth, behindLength)) continue;

            if (mergeType == null) {
                hitAnyRef[0] |= applyWaveHit(entity, center, direction, effectiveLength, baseHalfWidth, behindLength,
                        damage, damageSource, owner, resetInvulnerable, postHitCallback);
            } else {
                SecondaryDamageMerger.accumulate(entity, mergeType, damage, (target, mergedDamage) -> {
                    if (applyWaveHit(target, center, direction, effectiveLength, baseHalfWidth, behindLength,
                            mergedDamage, damageSource, owner, resetInvulnerable, postHitCallback)) {
                        hitAnyRef[0] = true;
                    }
                });
            }
        }

        // 触发能量波爆炸视觉特效
        if (showVisual && level instanceof ServerLevel serverLevel) {
            NetworkHandler.sendEnergyWaveExplosionToAll(serverLevel, center, direction, splashLength);
        }

        return hitAnyRef[0];
    }

    /**
     * 对单个实体施加能量波命中：重置无敌时间、斩杀归属、伤害
     *
     * @return 是否造成了伤害
     */
    private static boolean applyWaveHit(LivingEntity entity, Vec3 center, Vec3 direction, double effectiveLength,
                                         double baseHalfWidth, double behindLength,
                                         float damage, DamageSource damageSource, Player owner,
                                         boolean resetInvulnerable, Consumer<LivingEntity> postHitCallback) {
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

        return true;
    }

    /**
     * 判断实体是否在能量波范围内
     * <p>
     * 长方形包围盒（跟随溅射方向），恒定半宽，与能量波视觉几何一致。
     * 正方向（沿溅射方向）与反方向（身后）均为恒定半宽。
     * <p>
     * 长方形半宽 = 长度/16（宽度极限 = 长度/12），长度 = 有效溅射长度。
     * 身后延伸behindLength格。
     * <p>
     * 判定方法：将实体AABB中心投影到长方形轴线上，若沿方向处于
     * [-behindLength, effectiveLength] 且实体中心到轴线的垂直距离小于半宽+AABB半径，则命中。
     * AABB半径使用半对角线长度，确保不会漏掉部分在长方形内的实体。
     *
     * @param entity          目标实体
     * @param center          原点（长方形起点）
     * @param direction       溅射方向（归一化）
     * @param effectiveLength 有效溅射长度（长方形长度）
     * @param baseHalfWidth   长方形恒定半宽
     * @param behindLength    身后判定距离（格）
     */
    private static boolean isInEnergyWave(LivingEntity entity, Vec3 center, Vec3 direction,
                                          double effectiveLength, double baseHalfWidth, double behindLength) {
        if (effectiveLength <= 0) return false;

        AABB entityBox = entity.getBoundingBox();
        Vec3 entityCenter = entityBox.getCenter();

        // 实体中心到原点的向量
        Vec3 toEntity = entityCenter.subtract(center);
        double alongRay = toEntity.dot(direction);

        // AABB半对角线（保守的各方向半径估计）
        double halfX = entityBox.getXsize() / 2;
        double halfY = entityBox.getYsize() / 2;
        double halfZ = entityBox.getZsize() / 2;
        double aabbRadius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

        // 沿方向：实体必须在身后判定与长方形长度范围内（考虑AABB半径）
        if (alongRay + aabbRadius < -behindLength || alongRay - aabbRadius > effectiveLength) {
            return false;
        }

        // 长方形包围盒：恒定半宽，实体中心到轴线的垂直距离
        double perpDistSq = toEntity.lengthSqr() - alongRay * alongRay;
        double perpDist = Math.sqrt(Math.max(0, perpDistSq));

        // 垂直距离在恒定半宽内（加上AABB半径容差，确保不漏判）
        return perpDist < baseHalfWidth + aabbRadius;
    }
}

