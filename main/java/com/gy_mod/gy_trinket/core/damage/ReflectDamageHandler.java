package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.burn.BurnManager;
import com.gy_mod.gy_trinket.core.burn.IBurnSource;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.ignite.IIgniteSource;
import com.gy_mod.gy_trinket.core.ignite.IgniteManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 反射伤害处理器
 * <p>
 * 当玩家装备反射护盾模块时，受到攻击会向攻击者方向发射伤害射线
 * 射线命中的敌人会受到反射伤害和灼烧效果
 */
public class ReflectDamageHandler implements DamageHandler {

    private static final int PRIORITY = 201;
    private static final int BURN_DURATION_TICKS = 4;

    /** 反射目标列表：Map<反射UUID, 目标列表> */
    private static final Map<UUID, List<ReflectBurnTarget>> REFLECT_TARGETS = new HashMap<>();

    /**
     * 反射灼烧目标数据类
     * 存储被反射伤害影响的实体信息
     */
    private static class ReflectBurnTarget {
        final LivingEntity entity;      // 目标实体
        final Player attacker;          // 反射伤害的发起者（玩家）
        final double damageMultiplier;  // 伤害倍率
        int remainingTicks;             // 剩余持续时间
        boolean igniteApplied;          // 是否已应用点燃效果

        /**
         * 构造函数
         * @param entity 目标实体
         * @param attacker 发起反射的玩家
         * @param damageMultiplier 伤害倍率
         */
        ReflectBurnTarget(LivingEntity entity, Player attacker, double damageMultiplier) {
            this.entity = entity;
            this.attacker = attacker;
            this.damageMultiplier = damageMultiplier;
            this.remainingTicks = BURN_DURATION_TICKS;
            this.igniteApplied = false;
        }
    }

    /**
     * 反射灼烧源实现
     * 用于向BurnManager标识灼烧来源
     */
    private static class ReflectBurnSource implements IBurnSource {
        private final Player player;

        public ReflectBurnSource(Player player) {
            this.player = player;
        }

        @Override
        public Entity getInitiator() {
            return player;
        }

        @Override
        public String getName() {
            return "ReflectShield";
        }
    }

    /**
     * 反射点燃源实现
     * 用于向IgniteManager标识点燃来源
     */
    private static class ReflectIgniteSource implements IIgniteSource {
        private final Player player;

        public ReflectIgniteSource(Player player) {
            this.player = player;
        }

        @Override
        public Entity getInitiator() {
            return player;
        }

        @Override
        public String getName() {
            return "ReflectShield";
        }
    }

    /**
     * 每刻处理反射目标
     * 负责更新所有反射目标的状态，应用灼烧和点燃效果
     */
    public static void tick() {
        if (REFLECT_TARGETS.isEmpty()) {
            return;
        }

        List<UUID> reflectToRemove = new ArrayList<>();
        
        // 累积每个实体的灼烧充能（支持多个反射叠加）
        Map<LivingEntity, Float> burnChargeMap = new HashMap<>();
        // 需要点燃的目标集合（去重）
        Set<LivingEntity> igniteTargets = new HashSet<>();

        // 遍历所有反射记录
        for (Map.Entry<UUID, List<ReflectBurnTarget>> entry : REFLECT_TARGETS.entrySet()) {
            List<ReflectBurnTarget> targets = entry.getValue();
            boolean allTargetsComplete = true;

            for (ReflectBurnTarget target : targets) {
                // 跳过已死亡的实体
                if (!target.entity.isAlive()) {
                    continue;
                }

                // 应用点燃效果（只应用一次）
                if (!target.igniteApplied) {
                    igniteTargets.add(target.entity);
                    target.igniteApplied = true;
                }

                // 计算灼烧充能并累积（支持叠加）
                double shieldEffect = AttributeManager.getGroupAttribute(target.attacker.getUUID(), "shield_effect");
                double baseDamage = Config.REFLECT_DAMAGE_BASE_DAMAGE.get();
                float burnCharge = (float)(baseDamage * shieldEffect * target.damageMultiplier);
                burnChargeMap.merge(target.entity, burnCharge, Float::sum);

                target.remainingTicks--;

                if (target.remainingTicks > 0) {
                    allTargetsComplete = false;
                }
            }

            // 如果所有目标都已完成，标记移除
            if (allTargetsComplete) {
                reflectToRemove.add(entry.getKey());
            }
        }

        // 批量应用点燃效果
        for (LivingEntity target : igniteTargets) {
            Player attacker = findAttackerForTarget(target);
            if (attacker != null && target != attacker) {
                if (HostileTargetManager.shouldAttackPlayer(target, attacker)) {
                    IgniteManager.applyIgnite(target, new ReflectIgniteSource(attacker), "reflect_burn", false);
                }
            }
        }

        // 批量应用灼烧充能（合并后）
        for (Map.Entry<LivingEntity, Float> entry : burnChargeMap.entrySet()) {
            Player attacker = findAttackerForTarget(entry.getKey());
            if (attacker != null && entry.getKey() != attacker) {
                if (HostileTargetManager.shouldAttackPlayer(entry.getKey(), attacker)) {
                    BurnManager.applyBurnCharge(entry.getKey(), entry.getValue(), new ReflectBurnSource(attacker));
                }
            }
        }

        // 移除已完成的反射记录
        for (UUID uuid : reflectToRemove) {
            REFLECT_TARGETS.remove(uuid);
        }
    }

    /**
     * 查找目标实体对应的攻击者玩家
     * @param target 目标实体
     * @return 对应的玩家，如果未找到返回null
     */
    private static Player findAttackerForTarget(LivingEntity target) {
        for (List<ReflectBurnTarget> targets : REFLECT_TARGETS.values()) {
            for (ReflectBurnTarget t : targets) {
                if (t.entity == target) {
                    return t.attacker;
                }
            }
        }
        return null;
    }

    /**
     * 处理伤害事件
     * 当检测到伤害时，向攻击者方向发射反射射线
     */
    @Override
    public void handle(DamageContext context) {
        var damageTypeKey = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageTypeKey == ModDamageTypes.SHIELD_SELF_DAMAGE
            || damageTypeKey == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE
            || damageTypeKey == ModDamageTypes.PLAYER_SELF_DAMAGE
            || damageTypeKey == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return;
        }

        Player shieldOwner = context.getShieldOwner();
        LivingEntity attackedEntity = context.getAttackedEntity();
        UUID shieldOwnerUUID = shieldOwner.getUUID();

        // 检查玩家是否装备反射模块
        if (!hasReflectItem(shieldOwner)) {
            return;
        }

        // 检查护盾是否激活
        if (ShieldManager.getCurrentShield(shieldOwnerUUID) <= 0) {
            return;
        }

        // 原始伤害量为0时不触发反射
        if (context.getOriginalDamage() <= 0) {
            return;
        }

        // 如果玩家将护盾移植到其他实体，只有被移植保护的实体受到攻击时才触发反射
        if (ShieldTransferManager.hasTransferredShield(shieldOwnerUUID)) {
            // 被攻击的实体必须是被护盾移植保护的实体
            if (!ShieldTransferManager.isEntityProtected(shieldOwnerUUID, attackedEntity.getUUID())) {
                return;
            }
        }

        // 计算伤害倍率（基于原始伤害）
        float originalDamage = context.getOriginalDamage();
        double damageMultiplier = 1.0 + (originalDamage * 0.1);

        // 计算射线长度（受护盾效果半径和伤害倍率影响）
        double shieldEffectRadius = AttributeManager.getGroupAttribute(shieldOwner.getUUID(), "shield_effect_radius");
        double baseRayLength = Config.REFLECT_DAMAGE_RAY_LENGTH.get();
        double rayLength = baseRayLength * shieldEffectRadius * damageMultiplier;

        Entity attackerEntity = context.getAttacker();

        // 计算射线起点和方向
        Vec3 start = new Vec3(attackedEntity.getX(), attackedEntity.getY() + attackedEntity.getEyeHeight(), attackedEntity.getZ());
        Vec3 direction;

        // 如果有攻击者，朝向攻击者方向；否则朝向被攻击实体的视线方向
        if (attackerEntity != null && attackerEntity != attackedEntity && attackerEntity instanceof LivingEntity) {
            Vec3 attackerPos = new Vec3(attackerEntity.getX(), attackerEntity.getY() + attackerEntity.getEyeHeight(), attackerEntity.getZ());
            direction = attackerPos.subtract(start).normalize();
        } else {
            direction = attackedEntity.getLookAngle().normalize();
        }

        Vec3 end = start.add(direction.scale(rayLength));

        // 检测射线命中的目标
        Set<LivingEntity> hitEntities = detectTargetsByRay(shieldOwner, start, end);

        if (!hitEntities.isEmpty()) {
            UUID reflectUuid = UUID.randomUUID();
            List<ReflectBurnTarget> burnTargets = new ArrayList<>();

            // 创建反射目标列表
            for (LivingEntity entity : hitEntities) {
                burnTargets.add(new ReflectBurnTarget(entity, shieldOwner, damageMultiplier));
            }

            REFLECT_TARGETS.put(reflectUuid, burnTargets);

            // 发送粒子效果给客户端
            if (shieldOwner instanceof ServerPlayer serverPlayer) {
                double particleRadius = Math.min(rayLength, 5.0);
                NetworkHandler.sendReflectParticlesToPlayer(serverPlayer,
                    attackedEntity.getX(),
                    attackedEntity.getY() + attackedEntity.getBbHeight() / 2.0,
                    attackedEntity.getZ(),
                    particleRadius,
                    direction.x(),
                    direction.y(),
                    direction.z());
            }
        }
    }

    /**
     * 检查玩家是否装备反射伤害模块
     * @param player 玩家
     * @return 是否装备反射模块
     */
    private boolean hasReflectItem(Player player) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store == null) {
            return false;
        }

        // 遍历玩家光点核心中的物品
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(player.getUUID(), stack) && Config.isReflectDamageItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 通过射线检测命中的目标
     * 使用AABB包围盒进行初步筛选，然后进行精确的射线检测
     * @param player 玩家
     * @param start 射线起点
     * @param end 射线终点
     * @return 命中的实体集合
     */
    private Set<LivingEntity> detectTargetsByRay(Player player, Vec3 start, Vec3 end) {
        Set<LivingEntity> hitEntities = new HashSet<>();

        // 创建射线的AABB包围盒（扩大1格以确保覆盖）
        AABB aabb = new AABB(
            Math.min(start.x(), end.x()) - 1.0,
            Math.min(start.y(), end.y()) - 1.0,
            Math.min(start.z(), end.z()) - 1.0,
            Math.max(start.x(), end.x()) + 1.0,
            Math.max(start.y(), end.y()) + 1.0,
            Math.max(start.z(), end.z()) + 1.0
        );

        // 遍历包围盒内的所有生物实体
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, aabb)) {
            // 使用统一的攻击过滤判断
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) {
                continue;
            }

            // 使用精确射线检测判断是否命中
            if (isEntityHitByRay(entity, start, end)) {
                hitEntities.add(entity);
            }
        }

        return hitEntities;
    }

    /**
     * 使用AABB线面相交检测判断实体是否被射线命中
     * 使用Slab法（Slab Method）进行射线-AABB相交检测
     * @param entity 目标实体
     * @param start 射线起点
     * @param end 射线终点
     * @return 是否命中
     */
    private boolean isEntityHitByRay(LivingEntity entity, Vec3 start, Vec3 end) {
        AABB aabb = entity.getBoundingBox();

        double[] tNear = {Double.NEGATIVE_INFINITY};
        double[] tFar = {Double.POSITIVE_INFINITY};

        // 在X轴上裁剪
        if (!clipLineToPlane(start.x(), end.x(), aabb.minX, aabb.maxX, tNear, tFar)) {
            return false;
        }

        // 在Y轴上裁剪
        if (!clipLineToPlane(start.y(), end.y(), aabb.minY, aabb.maxY, tNear, tFar)) {
            return false;
        }

        // 在Z轴上裁剪
        if (!clipLineToPlane(start.z(), end.z(), aabb.minZ, aabb.maxZ, tNear, tFar)) {
            return false;
        }

        // 检查交点是否有效
        if (tNear[0] > tFar[0]) {
            return false;
        }

        // 检查射线是否与AABB相交
        return tFar[0] >= 0 && tNear[0] <= 1;
    }

    /**
     * 将线段裁剪到平面（Slab法的核心）
     * @param start 线段起点坐标
     * @param end 线段终点坐标
     * @param min 平面最小值
     * @param max 平面最大值
     * @param tNear 近交点参数
     * @param tFar 远交点参数
     * @return 是否相交
     */
    private boolean clipLineToPlane(double start, double end, double min, double max,
                                   double[] tNear, double[] tFar) {
        double tMin, tMax;

        // 计算线段与平面的交点参数
        if (end - start > 1e-6) {
            tMin = (min - start) / (end - start);
            tMax = (max - start) / (end - start);
        } else if (start - end > 1e-6) {
            tMin = (max - start) / (end - start);
            tMax = (min - start) / (end - start);
        } else {
            // 线段垂直于当前轴，检查是否在平面内
            if (start < min || start > max) {
                return false;
            }
            return true;
        }

        // 更新近远交点参数
        if (tMin > tNear[0]) tNear[0] = tMin;
        if (tMax < tFar[0]) tFar[0] = tMax;

        // 检查是否有效
        return tNear[0] <= tFar[0];
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    // 注册每刻更新任务
    static {
        com.gy_mod.gy_trinket.core.TickScheduler.register("reflect_damage_tick", 1, ReflectDamageHandler::tick);
    }
}