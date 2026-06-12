package com.gy_mod.gy_trinket.core.attack_mode.electric_discharge;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.burn.BurnManager;
import com.gy_mod.gy_trinket.core.burn.IBurnSource;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.ignite.IIgniteSource;
import com.gy_mod.gy_trinket.core.ignite.IgniteManager;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    /** 搜索敌对目标的半径 */
    private static final double SEARCH_RADIUS = 6.0;
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

        boolean hasElectricItem = false;
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty() && Config.isElectricDischargeItem(stack.getItem())
                        && !DisableSystem.isItemDisabled(player.getUUID(), stack)) {
                    hasElectricItem = true;
                    break;
                }
            }
        }
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
     * <p>
     * 主要步骤：
     * <ol>
     *   <li>生成分形闪电线段</li>
     *   <li>检测命中的目标</li>
     *   <li>记录目标进行持续灼烧</li>
     *   <li>发送闪电数据到客户端</li>
     * </ol>
     *
     * @param player          释放闪电的玩家
     * @param playerPos       闪电起点位置
     * @param targetPoint     闪电朝向点
     * @param enableBranches  是否生成分支
     * @param attackSpeed     玩家攻击速度
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
     * <p>
     * 主要功能：
     * <ul>
     *   <li>对命中目标施加灼烧</li>
     *   <li>对命中目标施加点燃</li>
     *   <li>清理已完成的闪电效果</li>
     * </ul>
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
                IgniteManager.applyIgnite(target, new ElectricIgniteSource(attacker), "electric_burn", true);
            }
        }

        // 应用灼烧效果
        for (Map.Entry<LivingEntity, Float> entry : burnChargeMap.entrySet()) {
            Player attacker = burnAttackerMap.get(entry.getKey());
            if (attacker != null) {
                BurnManager.applyBurnCharge(entry.getKey(), entry.getValue(), new ElectricBurnSource(attacker));
            }
        }

        // 清理已完成的闪电效果
        for (UUID uuid : lightningToRemove) {
            LIGHTNING_TARGETS.remove(uuid);
        }
    }

    /**
     * 从闪电线段的折点处查找目标
     * <p>
     * 在每个折点周围创建搜索区域，检测范围内的敌对目标
     *
     * @param player   释放闪电的玩家
     * @param segments 闪电线段列表
     * @return 命中的目标集合
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
     *
     * @param player       玩家
     * @param playerPos    搜索中心位置
     * @param searchRadius 搜索半径（跟随闪电长度）
     * @return 敌对目标列表（按距离排序）
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
     *
     * @param entity 待检查实体
     * @param player 玩家
     * @return 是否为有效目标
     */
    private static boolean isValidTarget(LivingEntity entity, Player player) {
        return HostileTargetManager.shouldAttackPlayer(entity, player);
    }

    /**
     * 计算闪电朝向点
     * <p>
     * 如果有目标，朝向最近的目标；如果没有目标，朝向玩家视线方向（使用闪电长度作为默认距离）
     *
     * @param player         玩家
     * @param playerPos      玩家位置
     * @param targets        目标列表
     * @param lightningLength 闪电长度（用于无目标时的默认搜索距离）
     * @return 闪电朝向点
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
     * <p>
     * 主要步骤：
     * <ol>
     *   <li>计算闪电长度（受攻击速度和护盾效果半径属性影响）</li>
     *   <li>生成主闪电路径</li>
     *   <li>在主路径上生成分支</li>
     * </ol>
     *
     * @param player             释放闪电的玩家
     * @param start              起点位置
     * @param targetDirectionPoint 朝向点
     * @param enableBranches     是否生成分支
     * @param attackSpeed        玩家攻击速度
     * @return 闪电线段列表
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

        List<Vec3> mainPath = generateMainLightningPath(start, direction, totalLength, random);

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
     * <p>
     * 分支概率随距离增加而增大
     *
     * @param distanceFromStart 距离起点的距离
     * @param totalLength       总长度
     * @return 分支概率 (0-1)
     */
    private static double calculateBranchChance(double distanceFromStart, double totalLength) {
        double progress = distanceFromStart / totalLength;
        double baseChance = Math.pow(progress, 1.5) * 0.9;
        return Math.max(baseChance, 0.5) * 0.5;
    }

    /**
     * 生成主闪电路径
     * <p>
     * 使用分形算法生成一条带有随机偏移的路径
     *
     * @param start       起点
     * @param direction   主方向
     * @param totalLength 总长度
     * @param random      随机数生成器
     * @return 路径点列表
     */
    private static List<Vec3> generateMainLightningPath(Vec3 start, Vec3 direction, double totalLength, Random random) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);

        Vec3 current = start;
        double distanceTraveled = 0;

        double defaultLength = 5.0;
        double scaleRatio = totalLength / defaultLength;
        if (scaleRatio < 1) scaleRatio = 1;
        while (distanceTraveled < totalLength) {
            double progress = distanceTraveled / totalLength;
            double baseMinLength = (0.4 - progress * 0.3) * scaleRatio;
            double baseMaxLength = (0.8 - progress * 0.5) * (1 + (scaleRatio - 1) * 0.5);
            double segmentLength = baseMinLength + random.nextDouble() * (baseMaxLength - baseMinLength);

            if (distanceTraveled + segmentLength > totalLength) {
                segmentLength = totalLength - distanceTraveled;
            }

            // 弯曲因子：越往末端，弯曲程度越大
            double bendFactor = 0.4 + progress * 0.8;

            // 随机角度偏移
            double angleX = (random.nextDouble() - 0.5) * 1.4 * bendFactor;
            double angleY = (random.nextDouble() - 0.5) * 1.1 * bendFactor;
            double angleZ = (random.nextDouble() - 0.5) * 1.4 * bendFactor;

            // 计算垂直方向
            Vec3 perpX = new Vec3(0, direction.z(), -direction.y()).normalize();
            Vec3 perpY = direction.cross(perpX).normalize();

            // 计算偏移后的方向
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
     * 生成闪电路径（用于分支）
     * <p>
     * 与主路径类似，但使用不同的参数
     *
     * @param start       起点
     * @param direction   主方向
     * @param totalLength 总长度
     * @param random      随机数生成器
     * @return 路径点列表
     */
    private static List<Vec3> generateBranchPath(Vec3 start, Vec3 direction, double totalLength, Random random) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);

        Vec3 current = start;
        double distanceTraveled = 0;
        double defaultLength = 5.0;
        double scaleRatio = totalLength / defaultLength;
        if (scaleRatio < 1) scaleRatio = 1;

        while (distanceTraveled < totalLength) {
            double progress = distanceTraveled / totalLength;
            double baseMinLength = (0.2 - progress * 0.1) * scaleRatio;
            double baseMaxLength = (0.5 - progress * 0.4) * (1 + (scaleRatio - 1) * 0.5);
            double segmentLength = baseMinLength + random.nextDouble() * (baseMaxLength - baseMinLength);

            if (distanceTraveled + segmentLength > totalLength) {
                segmentLength = totalLength - distanceTraveled;
            }

            double bendFactor = 0.5 + progress * 0.5;

            double angleX = (random.nextDouble() - 0.5) * 1.2 * bendFactor;
            double angleY = (random.nextDouble() - 0.5) * 1.0 * bendFactor;
            double angleZ = (random.nextDouble() - 0.5) * 1.2 * bendFactor;

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
     * <p>
     * 递归生成子分支，具有以下特点：
     * <ul>
     *   <li>有最大深度限制</li>
     *   <li>有最小长度限制</li>
     *   <li>分支概率随深度递减</li>
     * </ul>
     *
     * @param start              起点
     * @param maxLength          最大长度
     * @param distanceFromOrigin 距离起点的距离
     * @param segments           闪电线段列表
     * @param random             随机数生成器
     * @param depth              当前深度
     * @param branchProbability  分支概率
     * @param mainDirection      主方向（可选）
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

            List<Vec3> branchPath = generateBranchPath(start, branchDir, branchLength, random);

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
     *
     * @param level    服务器世界
     * @param segments 闪电线段列表
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
     * 电能释放灼烧来源
     */
    public static class ElectricBurnSource implements IBurnSource {
        private final Player player;

        public ElectricBurnSource(Player player) {
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
    }

    /**
     * 电能释放点燃来源
     */
    public static class ElectricIgniteSource implements IIgniteSource {
        private final Player player;

        public ElectricIgniteSource(Player player) {
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
    }
}
