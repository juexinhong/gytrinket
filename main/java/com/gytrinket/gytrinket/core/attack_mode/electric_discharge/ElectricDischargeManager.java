package com.gytrinket.gytrinket.core.attack_mode.electric_discharge;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.core.modifier.player.attack.AttackSpeedManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 电能释放管理器
 * <p>
 * 负责处理电能释放相关的逻辑，包括：
 * <ul>
 *   <li>释放分形闪电</li>
 *   <li>对目标施加灼烧</li>
 *   <li>受攻击速度和护盾属性影响</li>
 * </ul>
 */
public class ElectricDischargeManager {

    /** 最大目标数量 */
    private static final int MAX_TARGETS = 6;

    /** 存储正在进行灼烧的闪电目标 */
    private static final Map<UUID, List<LightningBurnTarget>> LIGHTNING_TARGETS = new HashMap<>();

    /**
     * 闪电灼烧目标记录
     */
    private static class LightningBurnTarget {
        final LivingEntity entity;
        final Player attacker;
        int remainingTicks;
        boolean igniteApplied;

        LightningBurnTarget(LivingEntity entity, Player attacker, int burnDuration) {
            this.entity = entity;
            this.attacker = attacker;
            this.remainingTicks = burnDuration;
            this.igniteApplied = false;
        }
    }

    /**
     * 释放电能
     * <p>
     * 主要逻辑：
     * <ol>
     *   <li>检查护盾是否有值</li>
     *   <li>计算并应用护盾自伤（受攻击速度影响）</li>
     *   <li>根据护盾是否移植决定闪电释放位置</li>
     *   <li>生成并发送闪电到客户端</li>
     * </ol>
     *
     * @param player 释放电能的玩家
     */
    public static void releaseElectric(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        boolean hasElectricItem = PlayerStoreUtils.hasActiveItem(player, Config::isElectricDischargeItem);
        if (!hasElectricItem) {
            return;
        }

        // 检查护盾值不为0
        double currentShield = ShieldManager.getCurrentShield(player.getUUID());
        if (currentShield <= 0) {
            return;
        }

        // 计算护盾自伤（受攻击速度影响，攻击速度越快，伤害越低）
        // 使用不含模组修正的基础攻击速度
        double attackSpeed = AttackSpeedManager.getBaseAttackSpeed(player);
        double attackSpeedMultiplier = attackSpeed;
        if (attackSpeedMultiplier < 0.01) {
            attackSpeedMultiplier = 0.01;
        }
        // 确保伤害不会变成负数（攻击速度过快时伤害为0）
        float shieldSelfDamage = (float) (1.0 / (attackSpeedMultiplier));

        // 应用护盾自伤
        DamageSource shieldSelfDamageSource = ModDamageTypes.getShieldSelfDamageSource(player.level());
        player.hurt(shieldSelfDamageSource, shieldSelfDamage);

        // 计算闪电长度（与 generateFractalLightning 保持一致）
        double speedRatio = 1 + (attackSpeed - 1) * 0.5;
        double baseLength = (5.0 + Math.random() * 2.0) / speedRatio;
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double lightningLength = baseLength * shieldEffectRadius;

        Random random = new Random();
        boolean generateSecondLightning = random.nextDouble() < 0.5;

        if (ShieldTransferManager.hasTransferredShield(player.getUUID())) {
            // 护盾移植时：在每个被保护实体位置触发
            List<LivingEntity> protectedEntities = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());

            for (LivingEntity entity : protectedEntities) {
                if (entity == null || !entity.isAlive()) {
                    continue;
                }

                Vec3 entityPos = entity.position().add(0, 0.6 * entity.getBbHeight(), 0);
                // 使用闪电长度作为索敌半径
                List<LivingEntity> targets = findHostileTargets(player, entityPos, lightningLength);

                // 选择距离实体最近的危险目标作为朝向点
                Vec3 targetPoint = calculateTargetPoint(player, entityPos, targets, lightningLength);

                generateAndSendLightning(player, entityPos, targetPoint, true, attackSpeed);

                if (generateSecondLightning) {
                    generateAndSendLightning(player, entityPos, targetPoint, false, attackSpeed);
                }
            }
        } else {
            // 未移植时：在玩家位置触发
            Vec3 playerPos = player.position().add(0, 0.6 * player.getBbHeight(), 0);
            // 使用闪电长度作为索敌半径
            List<LivingEntity> targets = findHostileTargets(player, playerPos, lightningLength);
            Vec3 targetPoint = calculateTargetPoint(player, playerPos, targets, lightningLength);

            generateAndSendLightning(player, playerPos, targetPoint, true, attackSpeed);

            if (generateSecondLightning) {
                generateAndSendLightning(player, playerPos, targetPoint, false, attackSpeed);
            }
        }
    }

    /**
     * 生成并发送闪电
     */
    private static void generateAndSendLightning(Player player, Vec3 playerPos, Vec3 targetPoint, boolean enableBranches, double attackSpeed) {
        List<LightningSegment> lightningSegments = generateFractalLightning(player, playerPos, targetPoint, enableBranches, attackSpeed);
        Set<LivingEntity> hitEntities = findTargetsFromBendPoints(player, lightningSegments);

        UUID lightningUuid = UUID.randomUUID();
        List<LightningBurnTarget> burnTargets = new ArrayList<>();
        int burnDuration = Config.getElectricDischargeBurnDuration();

        for (LivingEntity entity : hitEntities) {
            burnTargets.add(new LightningBurnTarget(entity, player, burnDuration));
        }

        LIGHTNING_TARGETS.put(lightningUuid, burnTargets);
        sendLightningToClients((ServerLevel) player.level(), lightningSegments);
    }

    /**
     * 每 tick 更新
     */
    public static void tick() {
        if (LIGHTNING_TARGETS.isEmpty()) {
            return;
        }

        List<UUID> lightningToRemove = new ArrayList<>();

        Map<LivingEntity, Float> burnChargeMap = new HashMap<>();
        Map<LivingEntity, Player> burnAttackerMap = new HashMap<>();
        Set<LivingEntity> igniteTargets = new HashSet<>();

        for (Map.Entry<UUID, List<LightningBurnTarget>> entry : LIGHTNING_TARGETS.entrySet()) {
            List<LightningBurnTarget> targets = entry.getValue();
            boolean allTargetsComplete = true;

            for (LightningBurnTarget target : targets) {
                if (!target.entity.isAlive()) {
                    continue;
                }

                // 第一 tick 应用点燃效果
                if (!target.igniteApplied) {
                    igniteTargets.add(target.entity);
                    target.igniteApplied = true;
                }

                // 计算灼烧充能
                float baseBurnCharge = (float) Config.getElectricDischargeBurnCharge();
                // 计算灼烧充能（使用不含模组修正的基础攻击速度）
                double attackSpeedBase = AttackSpeedManager.getBaseAttackSpeed(target.attacker);
                double attackSpeedMultiplier = attackSpeedBase;
                if (attackSpeedMultiplier < 0.01) {
                    attackSpeedMultiplier = 0.01;
                }
                // 护盾效果属性组影响灼烧施加量
                double shieldEffect = AttributeManager.getGroupAttribute(target.attacker.getUUID(), "shield_effect");
                float burnCharge = (float) (baseBurnCharge / attackSpeedMultiplier * shieldEffect);

                burnChargeMap.merge(target.entity, burnCharge, Float::sum);
                burnAttackerMap.put(target.entity, target.attacker);

                target.remainingTicks--;

                if (target.remainingTicks > 0) {
                    allTargetsComplete = false;
                }
            }

            if (allTargetsComplete) {
                lightningToRemove.add(entry.getKey());
            }
        }

        // 应用点燃效果
        for (LivingEntity target : igniteTargets) {
            Player attacker = burnAttackerMap.get(target);
            if (attacker != null) {
                IgniteManager.applyIgnite(target, new ElectricDischargeSource(attacker), "electric_burn", true);
            }
        }

        // 应用灼烧效果
        for (Map.Entry<LivingEntity, Float> entry : burnChargeMap.entrySet()) {
            Player attacker = burnAttackerMap.get(entry.getKey());
            if (attacker != null) {
                BurnManager.applyBurnCharge(entry.getKey(), entry.getValue(), new ElectricDischargeSource(attacker));
            }
        }

        // 清理已完成的闪电效果
        for (UUID uuid : lightningToRemove) {
            LIGHTNING_TARGETS.remove(uuid);
        }
    }

    /**
     * 从闪电线段的折点处查找目标
     */
    private static Set<LivingEntity> findTargetsFromBendPoints(Player player, List<LightningSegment> segments) {
        Set<LivingEntity> hitEntities = new HashSet<>();
        Set<Vec3> processedPoints = new HashSet<>();

        for (LightningSegment segment : segments) {
            Vec3 start = segment.start();
            Vec3 end = segment.end();

            double segmentLength = start.distanceTo(end);
            double searchRadius = segmentLength / 2.0;

            for (Vec3 point : Arrays.asList(start, end)) {
                // 避免重复检测同一个点
                boolean isDuplicate = false;
                for (Vec3 processed : processedPoints) {
                    if (point.distanceTo(processed) < 0.1) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (isDuplicate) {
                    continue;
                }

                processedPoints.add(point);

                // 创建搜索区域
                AABB searchBox = new AABB(
                    point.x() - searchRadius,
                    point.y() - searchRadius,
                    point.z() - searchRadius,
                    point.x() + searchRadius,
                    point.y() + searchRadius,
                    point.z() + searchRadius
                );

                // 查找有效目标
                for (LivingEntity entity : ((ServerLevel) player.level()).getEntitiesOfClass(LivingEntity.class, searchBox)) {
                    if (!isValidTarget(entity, player)) continue;
                    hitEntities.add(entity);
                }
            }
        }

        return hitEntities;
    }

    /**
     * 查找附近的敌对目标
     */
    private static List<LivingEntity> findHostileTargets(Player player, Vec3 playerPos, double searchRadius) {
        ServerLevel level = (ServerLevel) player.level();
        AABB searchBox = new AABB(
            playerPos.x() - searchRadius,
            playerPos.y() - searchRadius,
            playerPos.z() - searchRadius,
            playerPos.x() + searchRadius,
            playerPos.y() + searchRadius,
            playerPos.z() + searchRadius
        );

        return level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
            if (!isValidTarget(entity, player)) return false;
            boolean isAttackingPlayer = false;
            if (entity instanceof Mob mob) {
                isAttackingPlayer = mob.getTarget() == player;
            }
            return isAttackingPlayer || entity.hasLineOfSight(player);
        }).stream()
            .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
            .limit(MAX_TARGETS)
            .collect(Collectors.toList());
    }

    /**
     * 检查是否为有效目标
     */
    private static boolean isValidTarget(LivingEntity entity, Player player) {
        return HostileTargetManager.shouldAttackPlayer(entity, player);
    }

    /**
     * 计算闪电朝向点
     */
    private static Vec3 calculateTargetPoint(Player player, Vec3 playerPos, List<LivingEntity> targets, double lightningLength) {
        if (targets.isEmpty()) {
            Vec3 lookDir = player.getLookAngle().normalize();
            return playerPos.add(lookDir.scale(lightningLength));
        }

        LivingEntity nearestTarget = targets.get(0);
        return nearestTarget.position().add(0, 0.6 * nearestTarget.getBbHeight(), 0);
    }

    /**
     * 生成分形闪电
     */
    private static List<LightningSegment> generateFractalLightning(Player player, Vec3 start, Vec3 targetDirectionPoint, boolean enableBranches, double attackSpeed) {
        List<LightningSegment> segments = new ArrayList<>();
        Random random = new Random();

        Vec3 direction = targetDirectionPoint.subtract(start).normalize();
        double speedRatio = 1 + (attackSpeed - 1) * 0.5;
        double baseLength = (5.0 + random.nextDouble() * 2.0) / speedRatio;

        // 护盾效果半径属性组影响闪电总体长度
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double totalLength = baseLength * shieldEffectRadius;

        List<Vec3> mainPath = generateLightningPath(start, direction, totalLength, random, true);

        // 添加主路径到闪电线段
        for (int i = 0; i < mainPath.size() - 1; i++) {
            segments.add(new LightningSegment(mainPath.get(i), mainPath.get(i + 1)));
        }

        // 生成分支
        if (enableBranches) {
            for (int i = 1; i < mainPath.size(); i++) {
                Vec3 point = mainPath.get(i);
                double distanceFromStart = point.distanceTo(start);

                // 起始部分不生成分支
                if (distanceFromStart < 1.5) {
                    continue;
                }

                double branchChance = calculateBranchChance(distanceFromStart, totalLength);

                if (random.nextDouble() < branchChance) {
                    double branchLength = totalLength * (0.1 + random.nextDouble() * 0.2);
                    double branchBranchProb = 0.2;
                    generateBranches(point, branchLength, distanceFromStart, segments, random, 0, branchBranchProb, direction);
                }
            }
        }

        return segments;
    }

    /**
     * 计算在指定距离处的分支概率
     */
    private static double calculateBranchChance(double distanceFromStart, double totalLength) {
        double progress = distanceFromStart / totalLength;
        double baseChance = Math.pow(progress, 1.5) * 0.9;
        return Math.max(baseChance, 0.5) * 0.5;
    }

    /**
     * 生成闪电路径
     */
    private static List<Vec3> generateLightningPath(Vec3 start, Vec3 direction, double totalLength, Random random, boolean isMain) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);

        Vec3 current = start;
        double distanceTraveled = 0;
        double defaultLength = 5.0;
        double scaleRatio = totalLength / defaultLength;
        if (scaleRatio < 1) scaleRatio = 1;

        while (distanceTraveled < totalLength) {
            double progress = distanceTraveled / totalLength;

            double baseMinLength, baseMaxLength, bendBase, bendProgress, angleXFactor, angleYFactor;
            if (isMain) {
                baseMinLength = (0.4 - progress * 0.3) * scaleRatio;
                baseMaxLength = (0.8 - progress * 0.5) * (1 + (scaleRatio - 1) * 0.5);
                bendBase = 0.4;
                bendProgress = 0.8;
                angleXFactor = 1.4;
                angleYFactor = 1.1;
            } else {
                baseMinLength = (0.2 - progress * 0.1) * scaleRatio;
                baseMaxLength = (0.5 - progress * 0.4) * (1 + (scaleRatio - 1) * 0.5);
                bendBase = 0.5;
                bendProgress = 0.5;
                angleXFactor = 1.2;
                angleYFactor = 1.0;
            }

            double segmentLength = baseMinLength + random.nextDouble() * (baseMaxLength - baseMinLength);

            if (distanceTraveled + segmentLength > totalLength) {
                segmentLength = totalLength - distanceTraveled;
            }

            double bendFactor = bendBase + progress * bendProgress;

            double angleX = (random.nextDouble() - 0.5) * angleXFactor * bendFactor;
            double angleY = (random.nextDouble() - 0.5) * angleYFactor * bendFactor;

            Vec3 perpX = new Vec3(0, direction.z(), -direction.y()).normalize();
            Vec3 perpY = direction.cross(perpX).normalize();

            Vec3 offsetDir = direction
                .add(perpX.scale(angleX))
                .add(perpY.scale(angleY))
                .normalize();

            Vec3 nextPoint = current.add(offsetDir.scale(segmentLength));

            path.add(nextPoint);
            current = nextPoint;
            distanceTraveled += segmentLength;
        }

        return path;
    }

    /**
     * 生成分支
     */
    private static void generateBranches(Vec3 start, double maxLength, double distanceFromOrigin, List<LightningSegment> segments, Random random, int depth, double branchProbability, Vec3 mainDirection) {
        if (depth >= 6 || maxLength < 0.35) {
            return;
        }

        int branchCount = depth == 0 ? 1 : 1 + random.nextInt(2);

        for (int i = 0; i < branchCount; i++) {
            double branchLength = maxLength * (0.35 + random.nextDouble() * 0.5);

            Vec3 branchDir;
            if (mainDirection != null) {
                // 有主方向时，偏向主方向
                double theta = random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * random.nextDouble() - 1);

                Vec3 randomDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.sin(phi) * Math.sin(theta),
                    Math.cos(phi)
                );

                branchDir = mainDirection.scale(0.7).add(randomDir.scale(0.3)).normalize();
            } else {
                // 无主方向时，完全随机
                double theta = random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * random.nextDouble() - 1);

                branchDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.sin(phi) * Math.sin(theta),
                    Math.cos(phi)
                );
            }

            List<Vec3> branchPath = generateLightningPath(start, branchDir, branchLength, random, false);

            // 添加分支到闪电线段
            for (int j = 0; j < branchPath.size() - 1; j++) {
                segments.add(new LightningSegment(branchPath.get(j), branchPath.get(j + 1)));
            }

            // 递归生成子分支
            double newBranchProb = branchProbability * (0.85 + random.nextDouble() * 0.1);
            if (random.nextDouble() < newBranchProb) {
                Vec3 endPoint = branchPath.get(branchPath.size() - 1);
                Vec3 branchDirection = branchPath.get(branchPath.size() - 1)
                    .subtract(branchPath.get(0)).normalize();
                generateBranches(endPoint, branchLength * 0.6, distanceFromOrigin + maxLength, segments, random, depth + 1, newBranchProb, branchDirection);
            }
        }
    }

    /**
     * 发送闪电线段到所有客户端
     */
    private static void sendLightningToClients(ServerLevel level, List<LightningSegment> segments) {
        NetworkHandler.sendLightningToAll(level, segments);
    }

    /**
     * 闪电线段记录
     *
     * @param start 起点
     * @param end   终点
     */
    public record LightningSegment(Vec3 start, Vec3 end) {}

    /**
     * 电能释放来源（同时实现灼烧和点燃接口）
     */
    public static class ElectricDischargeSource implements IBurnSource, IIgniteSource {
        private final Player player;

        public ElectricDischargeSource(Player player) {
            this.player = player;
        }

        @Override
        public Entity getInitiator() {
            return player;
        }

        @Override
        public String getName() {
            return "electric_discharge";
        }

        @Override
        public java.util.UUID getInitiatorUUID() {
            return player.getUUID();
        }
    }
}
