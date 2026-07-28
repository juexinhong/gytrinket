package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.explosion.EnergyWaveExplosion;
import com.gy_mod.gy_trinket.core.entity.construct.HostileTargetManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 反射伤害处理器
 * <p>
 * 当玩家装备反射护盾模块时，受到攻击会向攻击者方向发射能量波爆炸
 * <p>
 * 爆炸参数：
 * - 初始溅射长度：1格
 * - 爆心：被攻击实体身高一半处
 * - 朝向：攻击者方向
 * - 初始爆炸伤害：1
 * - 受到伤害大于2时，每超出1点伤害，等量提高1点爆炸伤害和1格溅射长度
 * <p>
 * 反射粒子：
 * - 初始7个粒子，朝向攻击者方向，带随机偏移
 * - 每超出1点伤害（超过2），增加3个粒子
 * - 最大偏移角度：每超出1点伤害提高2度（上限90度）
 * - 粒子速度：每超出1点伤害增加10%
 */
public class ReflectDamageHandler implements DamageHandler {

    private static final int PRIORITY = 201;

    /** 初始爆炸伤害 */
    private static final float BASE_EXPLOSION_DAMAGE = 1.0f;
    /** 初始溅射长度（格） */
    private static final double BASE_SPUTTER_LENGTH = 1.0;
    /** 伤害阈值，超出此值才开始增加爆炸参数 */
    private static final float DAMAGE_THRESHOLD = 2.0f;
    /** 初始粒子数量 */
    private static final int BASE_PARTICLE_COUNT = 10;
    /** 每超出1点伤害增加的粒子数 */
    private static final double PARTICLE_COUNT_PER_EXCESS = 5.0;
    /** 每超出1点伤害增加的最大偏移角度（度） */
    private static final double ANGLE_PER_EXCESS = 2.0;
    /** 最大偏移角度上限（度） */
    private static final double MAX_ANGLE_CAP = 90.0;
    /** 初始散射角度（度），无超出伤害时也有基础散射 */
    private static final double BASE_ANGLE = 30.0;
    /** 每超出1点伤害的速度增加5% */
    private static final double SPEED_MULTIPLIER_PER_EXCESS = 1.08;
    /** 速度倍率上限200% */
    private static final double MAX_SPEED_MULTIPLIER = 3.0;

    @Override
    public void handle(DamageContext context) {
        if (context.isAnySelfDamage()) {
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
            if (!ShieldTransferManager.isEntityProtected(shieldOwnerUUID, attackedEntity.getUUID())) {
                return;
            }
        }

        float originalDamage = context.getOriginalDamage();

        // 计算超出伤害（超过阈值的伤害量）
        double excessDamage = Math.max(0, originalDamage - DAMAGE_THRESHOLD);

        // 计算爆炸伤害和溅射长度（伤害转化比降低为0.5）
        float explosionDamage = (float)(BASE_EXPLOSION_DAMAGE + excessDamage * 0.5);
        double sputterLength = BASE_SPUTTER_LENGTH + excessDamage;

        // 护盾效果属性增强爆炸伤害
        double shieldEffect = AttributeManager.getGroupAttribute(shieldOwnerUUID, "shield_effect");
        explosionDamage *= shieldEffect;

        // 护盾效果半径属性增强溅射长度
        double shieldEffectRadius = AttributeManager.getGroupAttribute(shieldOwnerUUID, "shield_effect_radius");
        sputterLength *= shieldEffectRadius;

        // 爆心：被攻击实体身高一半处
        Vec3 center = new Vec3(
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
            direction = attackerPos.subtract(center).normalize();
        } else {
            direction = attackedEntity.getLookAngle().normalize();
        }

        // 执行能量波爆炸（不显示默认特效，使用位置同步特效）
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
            false
        );

        // 发送带位置同步的能量波爆炸特效（护盾移植时跟随被保护实体，否则跟随玩家位置，保持初始方向）
        if (shieldOwner.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            int positionSyncId = ShieldTransferManager.hasTransferredShield(shieldOwnerUUID)
                ? attackedEntity.getId()
                : shieldOwner.getId();
            NetworkHandler.sendEnergyWaveExplosionToAll(serverLevel, center, direction, sputterLength, positionSyncId, 1);
        }

        // 计算粒子参数
        int particleCount = BASE_PARTICLE_COUNT + (int)(excessDamage * PARTICLE_COUNT_PER_EXCESS);
        double maxAngleDegrees = Math.min(MAX_ANGLE_CAP, BASE_ANGLE + excessDamage * ANGLE_PER_EXCESS);
        double speedMultiplier = Math.min(MAX_SPEED_MULTIPLIER, Math.pow(SPEED_MULTIPLIER_PER_EXCESS, excessDamage));

        // 发送粒子效果给客户端
        if (shieldOwner instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendReflectParticlesToPlayer(serverPlayer,
                center.x(), center.y(), center.z(),
                direction.x(), direction.y(), direction.z(),
                particleCount, maxAngleDegrees, speedMultiplier);
        }
    }

    /**
     * 检查玩家是否装备反射伤害模块
     */
    private boolean hasReflectItem(Player player) {
        return PlayerStoreUtils.hasActiveItem(player, Config::isReflectDamageItem);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
