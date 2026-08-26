package com.gy_mod.gy_trinket.core.attack_mode.electric_discharge;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.burn.BurnManager;
import com.gy_mod.gy_trinket.core.burn.IBurnSource;
import com.gy_mod.gy_trinket.core.entity.construct.HostileTargetManager;
import com.gy_mod.gy_trinket.core.ignite.IIgniteSource;
import com.gy_mod.gy_trinket.core.ignite.IgniteManager;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
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

        if (ShieldTransferManager.hasTransferredShield(player.getUUID())) {
            // 护盾移植时：在每个被保护实体位置触发
            List<LivingEntity> protectedEntities = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());

            for (LivingEntity entity : protectedEntities) {
                if (entity == null || !entity.isAlive()) {
                    continue;
                }

                Vec3 entityPos = entity.position().add(0, 0.6 * entity.getBbHeight(), 0);
                // 使用闪电长度的 2/3 作为索敌半径
                List<LivingEntity> targets = findHostileTargets(player, entityPos, lightningLength * 2.0 / 3.0);

                // 选择距离实体最近的危险目标作为朝向点
                Vec3 targetPoint = calculateTargetPoint(player, entityPos, targets, lightningLength);

                generateAndSendLightning(player, entityPos, targetPoint, attackSpeed);
            }
        } else {
            // 未移植时：在玩家位置触发
            Vec3 playerPos = player.position().add(0, 0.6 * player.getBbHeight(), 0);
            // 使用闪电长度的 2/3 作为索敌半径
            List<LivingEntity> targets = findHostileTargets(player, playerPos, lightningLength * 2.0 / 3.0);
            Vec3 targetPoint = calculateTargetPoint(player, playerPos, targets, lightningLength);

            generateAndSendLightning(player, playerPos, targetPoint, attackSpeed);
        }
    }

    /**
     * 生成并发送闪电
     */
    private static void generateAndSendLightning(Player player, Vec3 playerPos, Vec3 targetPoint, double attackSpeed) {
        List<LightningSegment> lightningSegments = generateFractalLightning(player, playerPos, targetPoint, attackSpeed);
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
        // 目标点 = 目标身高一半处（碰撞中心）
        return nearestTarget.position().add(0, nearestTarget.getBbHeight() * 0.5, 0);
    }

    /**
     * 生成分形闪电
     * <p>
     * 主路径必定经过目标点：先以目标方向生成到目标（长度=目标距离，期间可任意弯曲），
     * 经过目标后继续延伸剩余长度（之后可任意旋转）。
     * 段长度规则：先生成的段（根部）长，末端短。
     * 分支规则：0~5之间随机数量，闪电越长分支概率越高；分支可在任意节点分叉。
     */
    private static List<LightningSegment> generateFractalLightning(Player player, Vec3 start, Vec3 targetPoint, double attackSpeed) {
        List<LightningSegment> segments = new ArrayList<>();
        Random random = new Random();

        double speedRatio = 1 + (attackSpeed - 1) * 0.5;
        double baseLength = (5.0 + random.nextDouble() * 2.0) / speedRatio;

        // 护盾效果半径属性组影响闪电总体长度
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double totalLength = baseLength * shieldEffectRadius;

        List<Vec3> mainPath = new ArrayList<>();

        double targetDist = start.distanceTo(targetPoint);
        Vec3 dirToTarget = targetDist > 1e-4 ? targetPoint.subtract(start).normalize() : new Vec3(0, 0, 1);

        if (targetDist > 0.3) {
            // 第一段：从起点到目标点（长度=目标距离），期间可任意弯曲
            List<Vec3> toTarget = generateLightningPath(start, dirToTarget, targetDist, random, true);
            if (!toTarget.isEmpty()) {
                // 强制末端节点为目标点，保证闪电必定经过索敌位置
                toTarget.set(toTarget.size() - 1, targetPoint);
            }
            mainPath.addAll(toTarget);
        } else {
            mainPath.add(start);
            mainPath.add(targetPoint);
        }

        // 第二段：经过目标点后继续延伸剩余长度，之后可任意旋转
        double remaining = totalLength - targetDist;
        if (remaining > 0.3) {
            Vec3 lastNode = mainPath.get(mainPath.size() - 1);
            Vec3 prevNode = mainPath.size() >= 2 ? mainPath.get(mainPath.size() - 2) : null;

            Vec3 continueDir;
            if (prevNode != null) {
                Vec3 forward = lastNode.subtract(prevNode).normalize();
                Vec3 randomDir = new Vec3(
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1
                ).normalize();
                // 允许任意旋转，但仍略偏向原前进方向，避免明显回头
                continueDir = forward.scale(0.4).add(randomDir.scale(0.6)).normalize();
            } else {
                continueDir = new Vec3(
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1
                ).normalize();
            }

            List<Vec3> after = generateLightningPath(lastNode, continueDir, remaining, random, true);
            // 跳过第一个节点（= lastNode，已存在）
            for (int i = 1; i < after.size(); i++) {
                mainPath.add(after.get(i));
            }
        }

        // 添加主路径到闪电线段
        for (int i = 0; i < mainPath.size() - 1; i++) {
            segments.add(new LightningSegment(mainPath.get(i), mainPath.get(i + 1)));
        }

        // 分支：0~5之间随机数量，闪电越长分支概率越高；分支可在任意节点分叉
        int maxBranches = Math.min(5, Math.max(0, (int) Math.floor(totalLength / 2.0)));
        int branchCount = maxBranches > 0 ? random.nextInt(maxBranches + 1) : 0;

        if (branchCount > 0 && mainPath.size() > 1) {
            // 从主路径节点中随机选取分支点（跳过根部）
            List<Integer> forkIndexes = new ArrayList<>();
            for (int i = 1; i < mainPath.size(); i++) {
                forkIndexes.add(i);
            }
            Collections.shuffle(forkIndexes, random);

            int generated = 0;
            for (Integer idx : forkIndexes) {
                if (generated >= branchCount) {
                    break;
                }
                // 分支点处主路径的局部方向
                Vec3 prev = idx > 0 ? mainPath.get(idx - 1) : mainPath.get(idx + 1);
                Vec3 next = idx < mainPath.size() - 1 ? mainPath.get(idx + 1) : mainPath.get(idx - 1);
                Vec3 forkDir = next.subtract(prev).normalize();
                generateBranch(mainPath.get(idx), totalLength, segments, random, forkDir);
                generated++;
            }
        }

        return segments;
    }

    /**
     * 生成闪电路径
     * <p>
     * 段长度按生成顺序递减：先生成的段（根部）长，末端短；
     * 闪电总长度增加时，所需段数随之增加（平均段长不变）。
     * 段与段之间朝向偏转较大，但整体保持朝向前进方向。
     */
    private static List<Vec3> generateLightningPath(Vec3 start, Vec3 direction, double totalLength, Random random, boolean isMain) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);

        Vec3 current = start;
        double distanceTraveled = 0;

        while (distanceTraveled < totalLength) {
            double progress = distanceTraveled / totalLength;

            // 段长度轮廓：根部段长，末端段短
            double maxLen, minLen;
            if (isMain) {
                maxLen = 1.7 - progress * 1.3;   // 1.7 -> 0.4
                minLen = 1.1 - progress * 0.8;   // 1.1 -> 0.3
            } else {
                maxLen = 1.2 - progress * 0.8;   // 1.2 -> 0.4
                minLen = 0.7 - progress * 0.45;  // 0.7 -> 0.25
            }

            double segmentLength = minLen + random.nextDouble() * (maxLen - minLen);

            if (distanceTraveled + segmentLength > totalLength) {
                segmentLength = totalLength - distanceTraveled;
            }

            // 朝向偏转：段间方向差异大，但整体保持朝向
            double bendBase, bendProgress, angleXFactor, angleYFactor;
            if (isMain) {
                bendBase = 0.6;
                bendProgress = 0.7;
                angleXFactor = 2.0;
                angleYFactor = 1.6;
            } else {
                bendBase = 0.5;
                bendProgress = 0.6;
                angleXFactor = 1.7;
                angleYFactor = 1.4;
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
     * 从指定节点生成一条分支（单级分支，无子分支）。
     * 分支长度约为闪电总长度的一半（随机），朝向偏向主方向但偏转较大。
     */
    private static void generateBranch(Vec3 forkPoint, double totalLength, List<LightningSegment> segments, Random random, Vec3 mainDirection) {
        // 分支长度：约为闪电总长度的一半（0.35~0.65 随机）
        double branchLength = totalLength * (0.35 + random.nextDouble() * 0.3);

        // 分支朝向：偏向主方向，带大偏转
        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2 * random.nextDouble() - 1);

        Vec3 randomDir = new Vec3(
            Math.sin(phi) * Math.cos(theta),
            Math.sin(phi) * Math.sin(theta),
            Math.cos(phi)
        );

        Vec3 branchDir = mainDirection.scale(0.6).add(randomDir.scale(0.4)).normalize();

        List<Vec3> branchPath = generateLightningPath(forkPoint, branchDir, branchLength, random, false);

        for (int j = 0; j < branchPath.size() - 1; j++) {
            segments.add(new LightningSegment(branchPath.get(j), branchPath.get(j + 1)));
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

