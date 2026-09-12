package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.explosion.EnergyWaveExplosion;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 反射伤害处理器
 * <p>
 * 当玩家装备反射护盾模块（reflect_damage_items 特殊机制）时，受到攻击会向攻击者方向发射能量波爆炸。
 * <p>
 * 实例模式（仿护盾类型）：每件提供该机制的物品都是独立实例——各自触发、各自解析参数；
 * 参数支持物品级定义（配置面板按物品编辑），未覆盖时取下方默认值，多实例间不合并。
 * <p>
 * 爆炸参数（每实例独立）：
 * - 初始溅射长度：1格
 * - 爆心：被攻击实体身高一半处，朝攻击方向延伸1格
 * - 朝向：攻击者方向
 * - 初始爆炸伤害：1
 * - 受到伤害大于阈值（1）时，每超出1点伤害，提高0.3点爆炸伤害和0.3格溅射长度
 * <p>
 * 反射粒子（每实例独立）：
 * - 初始10个粒子，朝向攻击者方向，带随机偏移
 * - 每超出1点伤害增加5个粒子
 * - 最大偏移角度：初始30度，每超出1点伤害提高2度（上限180度）
 * - 粒子速度：每超出1点伤害提高8%（上限3倍）
 */
public class ReflectDamageHandler implements DamageHandler {

    private static final int PRIORITY = 201;

    /** 反射护盾特殊机制集合名 */
    public static final String MECHANIC_SET = "reflect_damage_items";

    /** 初始爆炸伤害 */
    public static final float BASE_EXPLOSION_DAMAGE = 1.0f;
    /** 初始溅射长度（格） */
    public static final double BASE_SPUTTER_LENGTH = 1.0;
    /** 伤害阈值，超出此值才开始增加爆炸参数 */
    public static final float DAMAGE_THRESHOLD = 1.0f;
    /** 每超出1点伤害增加的爆炸伤害 */
    public static final double DAMAGE_BONUS_PER_EXCESS = 0.3;
    /** 每超出1点伤害增加的溅射长度（格） */
    public static final double LENGTH_BONUS_PER_EXCESS = 0.3;
    /** 初始粒子数量 */
    public static final int BASE_PARTICLE_COUNT = 10;
    /** 每超出1点伤害增加的粒子数 */
    public static final double PARTICLE_COUNT_PER_EXCESS = 5.0;
    /** 每超出1点伤害增加的最大偏移角度（度） */
    public static final double ANGLE_PER_EXCESS = 2.0;
    /** 最大偏移角度上限（度） */
    public static final double MAX_ANGLE_CAP = 180.0;
    /** 初始散射角度（度），无超出伤害时也有基础散射 */
    public static final double BASE_ANGLE = 30.0;
    /** 每超出1点伤害的速度倍率 */
    public static final double SPEED_MULTIPLIER_PER_EXCESS = 1.08;
    /** 速度倍率上限 */
    public static final double MAX_SPEED_MULTIPLIER = 3.0;

    @Override
    public void handle(DamageContext context) {
        if (context.isAnySelfDamage()) {
            return;
        }

        Player shieldOwner = context.getShieldOwner();
        LivingEntity attackedEntity = context.getAttackedEntity();
        UUID shieldOwnerUUID = shieldOwner.getUUID();

        // 检查护盾是否激活
        if (ShieldManager.getCurrentShield(shieldOwnerUUID) <= 0) {
            return;
        }

        // 原始伤害量为0时不触发反射
        if (context.getOriginalDamage() <= 0) {
            return;
        }

        // 玩家处于护盾移植模式（装备移植物品）时，玩家自身不触发反射；
        // 只有被移植保护的实体受到攻击时才触发反射（即使当前无受保护实体）
        if (ShieldTransferManager.isShieldTransferEnabled(shieldOwnerUUID)) {
            if (!ShieldTransferManager.isEntityProtected(shieldOwnerUUID, attackedEntity.getUUID())) {
                return;
            }
        }

        // 枚举提供反射护盾机制的物品实例（每件物品都是独立实例，空则不触发）
        MinecraftServer server = shieldOwner.getServer();
        List<ItemStack> stacks = DefsManager.getEquippedMechanicStacks(server, shieldOwnerUUID, MECHANIC_SET);
        if (stacks.isEmpty()) {
            return;
        }

        float originalDamage = context.getOriginalDamage();

        // 护盾效果属性增强爆炸伤害 / 护盾效果半径属性增强溅射长度（玩家级属性，全实例共用）
        double shieldEffect = AttributeManager.getGroupAttribute(shieldOwnerUUID, "shield_effect");
        double shieldEffectRadius = AttributeManager.getGroupAttribute(shieldOwnerUUID, "shield_effect_radius");

        // 基础位置：被攻击实体身高一半处
        Vec3 baseCenter = new Vec3(
            attackedEntity.getX(),
            attackedEntity.getY() + attackedEntity.getBbHeight() / 2.0,
            attackedEntity.getZ()
        );

        // 计算方向：朝向攻击者
        Entity attackerEntity = context.getAttacker();
        Vec3 direction;

        if (attackerEntity != null && attackerEntity != attackedEntity && attackerEntity instanceof LivingEntity) {
            Vec3 attackerPos = new Vec3(
                attackerEntity.getX(),
                attackerEntity.getY() + attackerEntity.getBbHeight() / 2.0,
                attackerEntity.getZ()
            );
            direction = attackerPos.subtract(baseCenter).normalize();
        } else {
            direction = attackedEntity.getLookAngle().normalize();
        }

        // 爆心：基础位置朝攻击方向延伸1格
        Vec3 center = baseCenter.add(direction.scale(1.0));

        // 每个物品实例独立触发一次反射（参数按物品解析，互不合并）
        for (ItemStack stack : stacks) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            triggerReflectInstance(server, itemId, shieldOwner, attackedEntity, originalDamage,
                    shieldEffect, shieldEffectRadius, center, direction);
        }
    }

    /**
     * 触发单个物品实例的反射：按物品独立解析参数并执行能量波爆炸与粒子特效
     */
    private void triggerReflectInstance(MinecraftServer server, String itemId, Player shieldOwner,
                                        LivingEntity attackedEntity, float originalDamage,
                                        double shieldEffect, double shieldEffectRadius,
                                        Vec3 center, Vec3 direction) {
        // 计算超出伤害（超过阈值的伤害量）
        double excessDamage = Math.max(0, originalDamage - param(server, itemId, "damage_threshold", DAMAGE_THRESHOLD));

        // 计算爆炸伤害和溅射长度（每点超出伤害按实例参数提升）
        float explosionDamage = (float)(param(server, itemId, "base_explosion_damage", BASE_EXPLOSION_DAMAGE)
                + excessDamage * param(server, itemId, "damage_bonus_per_excess", DAMAGE_BONUS_PER_EXCESS));
        explosionDamage *= shieldEffect;

        double sputterLength = param(server, itemId, "base_sputter_length", BASE_SPUTTER_LENGTH)
                + excessDamage * param(server, itemId, "length_bonus_per_excess", LENGTH_BONUS_PER_EXCESS);
        sputterLength *= shieldEffectRadius;

        // 执行能量波爆炸（不显示默认特效，使用位置同步特效，身后判定1格）
        EnergyWaveExplosion.execute(
            shieldOwner.level(),
            center,
            direction,
            sputterLength,
            explosionDamage,
            attackedEntity.damageSources().explosion(null, shieldOwner),
            entity -> entity != shieldOwner && entity != attackedEntity && HostileTargetManager.shouldAttackPlayer(entity, shieldOwner),
            true,
            shieldOwner,
            null,
            false,
            1.0
        );

        // 发送带位置同步的能量波爆炸特效（护盾移植时跟随被保护实体，否则跟随玩家位置，保持初始方向）
        // 特效长度与伤害范围一致：溅射长度 × 爆炸半径属性增幅
        if (shieldOwner.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            int positionSyncId = ShieldTransferManager.hasTransferredShield(shieldOwner.getUUID())
                ? attackedEntity.getId()
                : shieldOwner.getId();
            double explosionRadiusMultiplier = AttributeManager.getGroupAttribute(shieldOwner.getUUID(), "explosion_radius");
            NetworkHandler.sendEnergyWaveExplosionToAll(serverLevel, center, direction,
                    sputterLength * explosionRadiusMultiplier, positionSyncId, 1, 1.0);
        }

        // 计算粒子参数（实例独立）
        int particleCount = (int)(param(server, itemId, "base_particle_count", BASE_PARTICLE_COUNT)
                + excessDamage * param(server, itemId, "particle_count_per_excess", PARTICLE_COUNT_PER_EXCESS));
        double maxAngleDegrees = Math.min(
                param(server, itemId, "max_angle_cap", MAX_ANGLE_CAP),
                param(server, itemId, "base_angle", BASE_ANGLE)
                        + excessDamage * param(server, itemId, "angle_per_excess", ANGLE_PER_EXCESS));
        double speedMultiplier = Math.min(
                param(server, itemId, "max_speed_multiplier", MAX_SPEED_MULTIPLIER),
                Math.pow(param(server, itemId, "speed_multiplier_per_excess", SPEED_MULTIPLIER_PER_EXCESS), excessDamage));

        // 发送粒子效果给客户端
        if (shieldOwner instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendReflectParticlesToPlayer(serverPlayer,
                center.x(), center.y(), center.z(),
                direction.x(), direction.y(), direction.z(),
                particleCount, maxAngleDegrees, speedMultiplier);
        }
    }

    /** 按物品实例解析反射护盾机制参数（单物品直查，未覆盖时取默认值） */
    private static double param(MinecraftServer server, String itemId, String key, double fallback) {
        return DefsManager.resolveMechanicValueForItem(server, itemId, MECHANIC_SET, key, fallback);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
