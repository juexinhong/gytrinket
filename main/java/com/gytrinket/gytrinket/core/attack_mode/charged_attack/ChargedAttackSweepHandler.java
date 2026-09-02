package com.gytrinket.gytrinket.core.attack_mode.charged_attack;

import com.gytrinket.gytrinket.core.entity.construct.IConstructEntity;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 充能攻击横扫增强处理
 * <p>
 * 充能释放时使用剑类物品：
 * 1. 必定触发横扫攻击（无视冲刺、移动等原版限制）
 * 2. 横扫伤害根据充能值提升（每点充能值+10%，最高100%加成）
 * 3. 横扫范围根据充能值扩大（每点+10%，无上限）
 * <p>
 * 不使用Mixin注入，而是在服务端直接执行自定义扇形范围伤害。
 */
public class ChargedAttackSweepHandler {

    private ChargedAttackSweepHandler() {}

    /** 充能攻击光束判定：在实体交互距离基础上额外增加的长度（格） */
    public static final double CHARGE_EXTRA_REACH = 0.5D;
    /** 充能攻击光束判定宽度（格）：以视线为轴 ±半宽，容差优于原「实体盒膨胀 0.5 + 中心线裁剪」 */
    public static final double CHARGE_BEAM_WIDTH = 1.5D;
    /** 充能攻击光束判定高度（格）：以视线为轴 ±半高 */
    public static final double CHARGE_BEAM_HEIGHT = 1.5D;

    /**
     * 判断物品是否支持横扫动作（剑类）
     */
    public static boolean isSwordItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    /**
     * 计算充能横扫伤害倍率
     * 每点充能值提升10%横扫伤害，最高100%加成
     *
     * @param chargeValue 充能值
     * @return 横扫伤害倍率（1.0 = 无加成，2.0 = 100%加成）
     */
    public static float getSweepDamageMultiplier(double chargeValue) {
        float bonus = (float) Math.min(chargeValue * 0.1, 1.0);
        return 1.0F + bonus;
    }

    /**
     * 计算充能横扫范围倍率
     * 每点充能值提升10%横扫范围，无上限
     *
     * @param chargeValue 充能值
     * @return 横扫范围倍率（1.0 = 无扩大）
     */
    public static float getSweepRangeMultiplier(double chargeValue) {
        return 1.0F + (float) (chargeValue * 0.1);
    }

    /**
     * 执行充能横扫攻击（替代原版attack+补伤机制）
     * <p>
     * 不使用Mixin注入，而是在服务端直接执行自定义扇形范围伤害：
     * 1. 对主要命中目标施加全额充能伤害
     * 2. 对扇形范围内的其他实体施加横扫伤害
     * 3. 发送自定义横扫粒子
     * 4. 处理击退效果
     */
    public static void executeChargedSweepAttack(ServerPlayer player, double chargeValue) {
        if (chargeValue <= 0) {
            return;
        }

        // 查找准星对准的主要目标（仅LivingEntity）
        Entity primaryTarget = findTargetInCrosshair(player, true);
        if (primaryTarget == null) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 对主要目标施加全额充能伤害
        float baseDamage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float chargedDamage = baseDamage * (1.0F + (float) chargeValue);

        // 移除无敌时间确保伤害生效
        if (primaryTarget instanceof LivingEntity livingTarget) {
            livingTarget.invulnerableTime = 0;
        }

        boolean hitLiving = primaryTarget.hurt(player.damageSources().playerAttack(player), chargedDamage);

        // 计算横扫参数
        float rangeMultiplier = getSweepRangeMultiplier(chargeValue);
        float sweepDamageMultiplier = getSweepDamageMultiplier(chargeValue);
        float sweepDamage = baseDamage * 0.15F * sweepDamageMultiplier;

        // 扇形范围搜索
        double entityReach = player.entityInteractionRange();
        double expandedDist = entityReach * rangeMultiplier;
        AABB searchBox = player.getBoundingBox().inflate(expandedDist + 1.0);
        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox);

        // 玩家视线方向
        Vec3 lookVec = player.getLookAngle();
        double expandedDistLimit = expandedDist * expandedDist + 1.0;

        // 先收集横扫目标到集合，避免遍历时伤害导致并发修改
        List<LivingEntity> sweepTargets = new ArrayList<>();
        for (LivingEntity livingEntity : nearbyEntities) {
            if (livingEntity == primaryTarget || livingEntity == player) {
                continue;
            }
            if (player.isAlliedTo(livingEntity)) {
                continue;
            }
            // 过滤自己的构造体
            if (isOwnConstruct(livingEntity, player)) {
                continue;
            }

            // 距离检查
            double distSqr = player.distanceToSqr(livingEntity);
            if (distSqr > expandedDistLimit) {
                continue;
            }

            // 前方判定：实体必须在玩家面前
            Vec3 toEntity = livingEntity.position().subtract(player.position());
            if (toEntity.dot(lookVec) <= 0) {
                continue;
            }

            sweepTargets.add(livingEntity);
        }

        // 统一对横扫目标施加伤害和击退
        double kbX = primaryTarget.getX() - player.getX();
        double kbZ = primaryTarget.getZ() - player.getZ();
        for (LivingEntity livingEntity : sweepTargets) {
            // 移除无敌时间确保伤害生效
            livingEntity.invulnerableTime = 0;

            livingEntity.hurt(player.damageSources().playerAttack(player), sweepDamage);

            // 击退效果（横扫击退）
            livingEntity.knockback(0.4F, -kbX, -kbZ);
        }

        // 发送横扫粒子
        sendChargedSweepParticle(player, chargeValue, rangeMultiplier);

        // 消耗攻击强度
        player.resetAttackStrengthTicker();

        // 耐久破坏：剑类充能释放成功攻击到主目标，对手持物品造成 1+充能值 耐久破坏
        // （非剑类走原版攻击，原版自带扣1点耐久）
        if (hitLiving) {
            damageHeldItemDurability(player, chargeValue);
        }
    }

    /**
     * 剑类充能释放：成功攻击到主目标时对手持物品执行耐久破坏（1 + 充能值）
     * <p>
     * 创造模式下hurtAndBreak内部不扣耐久（原版行为）。
     *
     * @param player      攻击玩家
     * @param chargeValue 释放时的充能值
     */
    public static void damageHeldItemDurability(Player player, double chargeValue) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        int breakAmount = Math.max(1, (int) (1.0 + chargeValue));
        held.hurtAndBreak(breakAmount, player, EquipmentSlot.MAINHAND);
    }

    /**
     * 查找玩家准星对准的目标（光束炮式矩形光束判定）
     * <p>
     * 判定方法与无人机光束炮一致：以视线线段为轴构造宽 {@link #CHARGE_BEAM_WIDTH} ×
     * 高 {@link #CHARGE_BEAM_HEIGHT} 的判定柱，长度 = 实体交互距离 + {@link #CHARGE_EXTRA_REACH}。
     * 多个命中时返回沿视线轴投影最近的一个。
     *
     * @param player     玩家
     * @param livingOnly true=仅LivingEntity（原版攻击过滤用），false=任意实体（含无生命实体，用于即时结算）
     * @return 准星对准的最近实体，或null
     */
    public static Entity findTargetInCrosshair(ServerPlayer player, boolean livingOnly) {
        double reachDistance = player.entityInteractionRange() + CHARGE_EXTRA_REACH;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0);
        Predicate<Entity> filter = livingOnly
                ? e -> e instanceof LivingEntity && e.isAlive() && e != player && !isOwnConstruct(e, player)
                : e -> e.isAlive() && e != player && !isOwnConstruct(e, player);
        List<Entity> entities = player.level().getEntities(player, searchBox, filter::test);

        Entity closestEntity = null;
        double closestDistance = reachDistance;

        for (Entity entity : entities) {
            if (!isEntityHitByBeam(entity, eyePos, endPos)) {
                continue;
            }
            // 距离排序：实体盒中心沿视线轴的投影长度（取最近）
            double alongAxis = entity.getBoundingBox().getCenter().subtract(eyePos).dot(lookVec);
            if (alongAxis < closestDistance) {
                closestDistance = alongAxis;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    /**
     * 光束炮式矩形光束判定（判定方法同无人机光束炮 DroneBeamProjectile）
     * <p>
     * 命中条件（任一）：
     * 1. 判定柱 12 条棱边线段与实体 AABB 相交
     * 2. 实体 AABB 中心位于判定柱内
     */
    public static boolean isEntityHitByBeam(Entity entity, Vec3 beamStart, Vec3 beamEnd) {
        return isHitByRectangularBeam(beamStart, beamEnd, entity.getBoundingBox());
    }

    private static boolean isHitByRectangularBeam(Vec3 beamStart, Vec3 beamEnd, AABB entityAABB) {
        Vec3 lookVec = beamEnd.subtract(beamStart).normalize();
        // 视线接近竖直时水平右侧向量退化，改用固定水平轴
        Vec3 right = lookVec.y > 0.999D || lookVec.y < -0.999D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-lookVec.z, 0.0D, lookVec.x).normalize();
        Vec3 up = lookVec.cross(right).normalize();

        Vec3 halfWidth = right.scale(CHARGE_BEAM_WIDTH / 2.0D);
        Vec3 halfHeight = up.scale(CHARGE_BEAM_HEIGHT / 2.0D);

        Vec3 topLeftStart = beamStart.add(halfHeight).subtract(halfWidth);
        Vec3 topRightStart = beamStart.add(halfHeight).add(halfWidth);
        Vec3 bottomLeftStart = beamStart.subtract(halfHeight).subtract(halfWidth);
        Vec3 bottomRightStart = beamStart.subtract(halfHeight).add(halfWidth);

        Vec3 topLeftEnd = beamEnd.add(halfHeight).subtract(halfWidth);
        Vec3 topRightEnd = beamEnd.add(halfHeight).add(halfWidth);
        Vec3 bottomLeftEnd = beamEnd.subtract(halfHeight).subtract(halfWidth);
        Vec3 bottomRightEnd = beamEnd.subtract(halfHeight).add(halfWidth);

        if (intersectsLineAABB(topLeftStart, topRightStart, entityAABB)) return true;
        if (intersectsLineAABB(topRightStart, topRightEnd, entityAABB)) return true;
        if (intersectsLineAABB(topRightEnd, topLeftEnd, entityAABB)) return true;
        if (intersectsLineAABB(topLeftEnd, topLeftStart, entityAABB)) return true;

        if (intersectsLineAABB(bottomLeftStart, bottomRightStart, entityAABB)) return true;
        if (intersectsLineAABB(bottomRightStart, bottomRightEnd, entityAABB)) return true;
        if (intersectsLineAABB(bottomRightEnd, bottomLeftEnd, entityAABB)) return true;
        if (intersectsLineAABB(bottomLeftEnd, bottomLeftStart, entityAABB)) return true;

        if (intersectsLineAABB(topLeftStart, bottomLeftStart, entityAABB)) return true;
        if (intersectsLineAABB(topLeftEnd, bottomLeftEnd, entityAABB)) return true;

        if (intersectsLineAABB(topRightStart, bottomRightStart, entityAABB)) return true;
        if (intersectsLineAABB(topRightEnd, bottomRightEnd, entityAABB)) return true;

        return isAABBInsideBeam(entityAABB, beamStart, beamEnd, halfWidth, halfHeight, lookVec);
    }

    private static boolean isAABBInsideBeam(AABB aabb, Vec3 beamStart, Vec3 beamEnd, Vec3 halfWidth, Vec3 halfHeight, Vec3 lookVec) {
        Vec3 center = aabb.getCenter();
        Vec3 centerToStart = center.subtract(beamStart);
        double widthDistance = Math.abs(centerToStart.dot(halfWidth.normalize()));
        if (widthDistance > halfWidth.length()) {
            return false;
        }

        double heightDistance = Math.abs(centerToStart.dot(halfHeight.normalize()));
        if (heightDistance > halfHeight.length()) {
            return false;
        }

        double lengthDistance = centerToStart.dot(lookVec);
        double beamLength = beamEnd.distanceTo(beamStart);
        return lengthDistance >= 0 && lengthDistance <= beamLength;
    }

    private static boolean intersectsLineAABB(Vec3 lineStart, Vec3 lineEnd, AABB aabb) {
        double[] tNear = {Double.NEGATIVE_INFINITY};
        double[] tFar = {Double.POSITIVE_INFINITY};

        if (!clipLineToPlane(lineStart.x, lineEnd.x, aabb.minX, aabb.maxX, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (!clipLineToPlane(lineStart.y, lineEnd.y, aabb.minY, aabb.maxY, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (!clipLineToPlane(lineStart.z, lineEnd.z, aabb.minZ, aabb.maxZ, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (tNear[0] > tFar[0]) {
            return false;
        }

        return tFar[0] >= 0 && tNear[0] <= 1;
    }

    private static boolean clipLineToPlane(double start, double end, double min, double max,
                                           Vec3 lineStart, Vec3 lineEnd, double[] tNear, double[] tFar) {
        double tMin, tMax;

        if (end - start > 1e-6) {
            tMin = (min - start) / (end - start);
            tMax = (max - start) / (end - start);
        } else if (start - end > 1e-6) {
            tMin = (max - start) / (end - start);
            tMax = (min - start) / (end - start);
        } else {
            if (start < min || start > max) {
                return false;
            }
            return true;
        }

        if (tMin > tNear[0]) tNear[0] = tMin;
        if (tMax < tFar[0]) tFar[0] = tMax;

        return tNear[0] <= tFar[0];
    }

    /**
     * 对射线上的所有无生命实体造成充能加成伤害
     * <p>
     * 充能攻击释放时调用，独立于有生命实体的攻击过滤逻辑。
     * 找到射线上所有非LivingEntity实体，施加充能加成伤害。
     *
     * @param player      攻击玩家
     * @param chargeValue 充能值
     */
    public static void damageNonLivingTargetsAlongRaycast(ServerPlayer player, double chargeValue) {
        if (chargeValue <= 0) {
            return;
        }

        double reachDistance = player.entityInteractionRange() + CHARGE_EXTRA_REACH;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0);
        // 排除掉落物与经验球：目标死亡瞬间生成的战利品就在射线上，会被穿透伤害销毁（ItemEntity/ExperienceOrb 仅 5 点 HP）
        List<Entity> entities = player.level().getEntities(player, searchBox,
                e -> e.isAlive() && !(e instanceof LivingEntity)
                        && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb)
                        && e != player && !isOwnConstruct(e, player));

        float baseDamage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float chargedDamage = baseDamage * (1.0F + (float) chargeValue);

        for (Entity entity : entities) {
            if (!isEntityHitByBeam(entity, eyePos, endPos)) {
                continue;
            }
            entity.hurt(player.damageSources().playerAttack(player), chargedDamage);
        }
    }

    /**
     * 判断实体是否为玩家自己的构造体
     */
    private static boolean isOwnConstruct(Entity entity, Player player) {
        if (entity instanceof IConstructEntity constructEntity) {
            UUID ownerUUID = constructEntity.getOwnerUUID();
            return ownerUUID != null && ownerUUID.equals(player.getUUID());
        }
        return false;
    }

    /**
     * 发送充能横扫粒子到所有可见此玩家的客户端
     */
    private static void sendChargedSweepParticle(ServerPlayer player, double chargeValue, float rangeMultiplier) {
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        float pitch = player.getXRot() * ((float) Math.PI / 180F);
        float cosPitch = Mth.cos(pitch);

        double forwardShift = rangeMultiplier > 1.0F ? (rangeMultiplier - 1.0F) * 0.5 : 0;

        double lookX = -Mth.sin(yaw) * cosPitch;
        double lookY = -Mth.sin(pitch);
        double lookZ = Mth.cos(yaw) * cosPitch;
        double distance = 1.5 + forwardShift;

        double baseX = player.getX() + lookX * distance;
        double baseY = player.getY() + player.getEyeHeight() - 0.5 + lookY * distance;
        double baseZ = player.getZ() + lookZ * distance;

        NetworkHandler.sendChargedSweepParticleToAll(
                player, baseX, baseY, baseZ,
                yaw, pitch, rangeMultiplier,
                player.level().getGameTime(), 4
        );
    }
}
