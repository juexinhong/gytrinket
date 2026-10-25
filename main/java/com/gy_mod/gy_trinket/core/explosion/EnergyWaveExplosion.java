package com.gy_mod.gy_trinket.core.explosion;

import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.damage.DamageAttributionWindow;
import com.gy_mod.gy_trinket.core.damage.SecondaryDamageMerger;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.core.projectile.MultipartHitScan;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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

        // 多体节：体节不注册进 level 实体列表，只查父实体会漏掉伸出的头/颈/翅/尾/节段
        // （娜迦父实体只有头部 2×2，身体节段拖出 20+ 格）；每个父实体只结算一次，
        // 命中箱取离波心最近的那个（父实体 AABB 与原版爆炸一致恒参与）
        List<MultipartHitScan.Hit> hits = MultipartHitScan.collectInRegion(level, aabb, center,
                e -> entityFilter.test((LivingEntity) e),
                box -> isInEnergyWave(box, center, direction, effectiveLength, baseHalfWidth, behindLength),
                false);

        boolean[] hitAnyRef = {false};
        for (MultipartHitScan.Hit hit : hits) {
            LivingEntity target = hit.target();
            Entity hitbox = hit.hitbox();
            // 体节命中时合并键带上体节 ID，保证合并批次与施加时命中的碰撞箱一致
            String waveMergeType = mergeType == null || hitbox == target
                    ? mergeType
                    : mergeType + ":part:" + hitbox.getId();

            if (waveMergeType == null) {
                hitAnyRef[0] |= applyWaveHit(hitbox, target, center, direction, effectiveLength, baseHalfWidth, behindLength,
                        damage, damageSource, owner, resetInvulnerable, postHitCallback);
            } else {
                SecondaryDamageMerger.accumulate(target, waveMergeType, damage, (t, mergedDamage) -> {
                    if (applyWaveHit(hitbox, t, center, direction, effectiveLength, baseHalfWidth, behindLength,
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
     * 对单个实体施加能量波命中：重置无敌时间、归属窗口、伤害
     * <p>
     * {@code hitbox} 是实际命中箱（体节命中时为体节，否则为父实体），伤害经它结算以
     * 保留各模组的体节倍率；{@code target} 是父实体，用于无敌帧、归属窗口与回调。
     *
     * @return 是否造成了伤害
     */
    private static boolean applyWaveHit(Entity hitbox, LivingEntity entity, Vec3 center, Vec3 direction, double effectiveLength,
                                         double baseHalfWidth, double behindLength,
                                         float damage, DamageSource damageSource, Player owner,
                                         boolean resetInvulnerable, Consumer<LivingEntity> postHitCallback) {
        if (resetInvulnerable) {
            entity.invulnerableTime = 0;
        }

        // 伤害源恒不带玩家（避免非致死时对玩家触发仇恨）：
        // 预估致死时由 ExecuteDamageHandler 取消原伤害并改用归属玩家的
        // 斩杀伤害源（execute_damage）完成最后一击
        boolean windowed = owner != null && damageSource.getEntity() != null;
        if (windowed) {
            DamageAttributionWindow.mark(entity, owner);
        }

        // 能量波爆炸不产生击退（参照灼烧系统实现）
        KnockbackManager.markNoKnockback(entity.getUUID());

        try {
            hitbox.hurt(damageSource, damage);
        } finally {
            if (windowed) {
                DamageAttributionWindow.unmark(entity);
            }
        }

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
     * @param entityBox       目标碰撞箱（体节命中时为体节碰撞箱）
     * @param center          原点（长方形起点）
     * @param direction       溅射方向（归一化）
     * @param effectiveLength 有效溅射长度（长方形长度）
     * @param baseHalfWidth   长方形恒定半宽
     * @param behindLength    身后判定距离（格）
     */
    private static boolean isInEnergyWave(AABB entityBox, Vec3 center, Vec3 direction,
                                          double effectiveLength, double baseHalfWidth, double behindLength) {
        if (effectiveLength <= 0) return false;

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

