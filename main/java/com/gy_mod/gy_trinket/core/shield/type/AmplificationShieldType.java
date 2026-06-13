package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 增幅护盾类型
 * <p>
 * 功能：
 * 1. 当玩家有护盾值时，提供基础攻击伤害加成（20%独立乘区）
 * 2. 每5刻检测玩家或被保护实体周围的威胁实体（敌对生物、危险物）
 * 3. 每个威胁实体额外增加50%攻击伤害加成
 * 4. 攻击伤害加成上限为100%
 * 5. 当有护盾值时，提供移动速度独立乘区加成（受护盾效果影响，不受威胁数量影响）
 * <p>
 * 属性影响：
 * - shield_effect 属性组：影响基础加成、上限和移动速度加成
 * - shield_effect_radius 属性组：影响检测半径
 * <p>
 * 护盾移植支持：
 * - 当护盾移植时，在被保护实体位置检测威胁
 * - 攻击伤害和移动速度修饰符直接施加在被保护实体上
 */
public class AmplificationShieldType implements IShieldType {

    /** 追踪的威胁实体：玩家UUID -> 威胁实体集合 */
    private static final Map<UUID, Set<Entity>> TRACKED_THREAT_ENTITIES = new HashMap<>();
    
    /** 计时器：玩家UUID -> 刻数 */
    private static final Map<UUID, Integer> TICK_COUNTER = new HashMap<>();
    
    /** 威胁检测间隔（刻） */
    private static final int CHECK_INTERVAL = 5;
    
    /** 攻击伤害修饰符UUID */
    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("f5a6b7c8-d9e0-1234-5678-9abcdef01234");

    /** 攻击伤害修饰符名称 */
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "amplification_shield_attack_damage_modifier";

    /** 移动速度修饰符UUID */
    private static final UUID MOVEMENT_SPEED_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456789");

    /** 移动速度修饰符名称 */
    private static final String MOVEMENT_SPEED_MODIFIER_NAME = "amplification_shield_movement_speed_modifier";
    
    /** 获取基础增幅值 */
    private static double getBaseAmplification() {
        return Config.getAmplificationBaseAmplification();
    }
    
    /** 获取每个威胁增加的增幅值 */
    private static double getThreatAmplification() {
        return Config.getAmplificationThreatAmplification();
    }
    
    /** 获取最大增幅值 */
    private static double getMaxAmplification() {
        return Config.getAmplificationMaxAmplification();
    }
    
    /** 获取基础检测半径 */
    private static double getBaseRadius() {
        return Config.getAmplificationCheckRadius();
    }

    @Override
    public String getName() {
        return "amplification";
    }

    @Override
    public boolean isCompatible() {
        return false;
    }

    /**
     * 护盾类型被移除时调用
     * 清理修饰符和追踪数据
     */
    @Override
    public void onRemoved(Player player) {
        UUID playerUUID = player.getUUID();
        removeAttackDamageModifier(player);
        removeMovementSpeedModifier(player);
        
        // 移除被保护实体上的修饰符
        for (LivingEntity protectedEntity : ShieldTransferManager.getProtectedEntities(playerUUID, player.level())) {
            removeAttackDamageModifier(protectedEntity);
            removeMovementSpeedModifier(protectedEntity);
        }
        
        TRACKED_THREAT_ENTITIES.remove(playerUUID);
        TICK_COUNTER.remove(playerUUID);
    }

    /**
     * 每刻更新
     * 1. 检查护盾值，无护盾时清理修饰符
     * 2. 每5刻检测威胁实体（在玩家或被保护实体位置）
     * 3. 更新攻击伤害加成（施加在玩家或被保护实体上）
     */
    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        UUID playerUUID = player.getUUID();
        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        
        if (currentShield <= 0) {
            // 无护盾时清理所有修饰符
            removeAttackDamageModifier(player);
            removeMovementSpeedModifier(player);
            for (LivingEntity protectedEntity : ShieldTransferManager.getProtectedEntities(playerUUID, player.level())) {
                removeAttackDamageModifier(protectedEntity);
                removeMovementSpeedModifier(protectedEntity);
            }
            TRACKED_THREAT_ENTITIES.remove(playerUUID);
            return;
        }
        
        int tickCounter = TICK_COUNTER.getOrDefault(playerUUID, 0);
        tickCounter++;
        TICK_COUNTER.put(playerUUID, tickCounter);

        if (tickCounter >= CHECK_INTERVAL) {
            TICK_COUNTER.put(playerUUID, 0);
            updateThreatEntities(player);
        }

        updateAttackDamageBonus(player);
    }

    /**
     * 更新威胁实体列表
     * 检测玩家或被保护实体周围的敌对生物和危险物
     */
    private void updateThreatEntities(Player player) {
        UUID playerUUID = player.getUUID();
        Level level = player.level();
        
        double shieldEffectRadius = AttributeManager.getGroupAttribute(playerUUID, "shield_effect_radius");
        double radius = getBaseRadius() * shieldEffectRadius;

        Set<Entity> newThreats = new HashSet<>();
        
        // 获取需要检测威胁的实体（玩家或被保护实体）
        List<LivingEntity> targetEntities = new ArrayList<>();
        
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            targetEntities.addAll(ShieldTransferManager.getProtectedEntities(playerUUID, level));
        } else {
            targetEntities.add(player);
        }
        
        // 在每个目标实体周围检测威胁
        for (LivingEntity targetEntity : targetEntities) {
            if (targetEntity == null || !targetEntity.isAlive()) {
                continue;
            }
            
            AABB boundingBox = targetEntity.getBoundingBox().inflate(radius);
            
            List<Entity> entities = level.getEntities(targetEntity, boundingBox, 
                entity -> HostileTargetManager.shouldAttackPlayer(entity, player)
            );
            
            newThreats.addAll(entities);
        }

        TRACKED_THREAT_ENTITIES.put(playerUUID, newThreats);
    }

    /**
     * 更新攻击伤害加成和移动速度加成
     * 攻击伤害：基础加成 + 威胁加成，不超过上限
     * 移动速度：基础加成 × 护盾效果（不受威胁数量影响）
     * 直接给生效的实体（玩家或被保护实体）施加修饰符
     */
    private void updateAttackDamageBonus(Player player) {
        UUID playerUUID = player.getUUID();
        
        double shieldEffect = AttributeManager.getGroupAttribute(playerUUID, "shield_effect");
        
        // 计算基础加成和上限（受护盾效果属性影响）
        double baseBonus = getBaseAmplification() * shieldEffect;
        double maxBonus = getMaxAmplification() * shieldEffect;

        // 获取威胁数量并计算威胁加成
        Set<Entity> threats = TRACKED_THREAT_ENTITIES.getOrDefault(playerUUID, Collections.emptySet());
        double threatBonus = threats.size() * getThreatAmplification();

        // 计算总加成，不超过上限
        double totalBonus = baseBonus * (1 + threatBonus);
        totalBonus = Math.min(totalBonus, maxBonus);

        // 计算移动速度加成（受护盾效果影响，不受威胁数量影响）
        double movementSpeedBonus = Config.getAmplificationMovementSpeedBonus() * shieldEffect;

        // 获取需要施加修饰符的实体
        List<LivingEntity> targetEntities = new ArrayList<>();
        
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            targetEntities.addAll(ShieldTransferManager.getProtectedEntities(playerUUID, player.level()));
            removeAttackDamageModifier(player);
            removeMovementSpeedModifier(player);
        } else {
            targetEntities.add(player);
        }
        
        // 给目标实体施加攻击伤害和移动速度修饰符
        for (LivingEntity targetEntity : targetEntities) {
            if (targetEntity != null && targetEntity.isAlive()) {
                addAttackDamageModifier(targetEntity, totalBonus);
                addMovementSpeedModifier(targetEntity, movementSpeedBonus);
            }
        }
    }

    /**
     * 给实体添加攻击伤害修饰符
     * @param entity 目标实体
     * @param bonus 加成值（独立乘区，如0.2表示+20%）
     */
    private void addAttackDamageModifier(LivingEntity entity, double bonus) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }

        // 先移除旧修饰符
        removeAttackDamageModifier(entity);

        // 添加新修饰符（使用MULTIPLY_TOTAL，值需要-1因为原版会自动+1）
        AttributeModifier modifier = new AttributeModifier(
            ATTACK_DAMAGE_MODIFIER_UUID,
            ATTACK_DAMAGE_MODIFIER_NAME,
            bonus,
            AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        attribute.addTransientModifier(modifier);
    }

    /**
     * 移除实体的攻击伤害修饰符
     * @param entity 目标实体
     */
    private void removeAttackDamageModifier(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(ATTACK_DAMAGE_MODIFIER_UUID);
    }

    /**
     * 给实体添加移动速度修饰符
     * @param entity 目标实体
     * @param bonus 加成值（独立乘区，如0.2表示+20%）
     */
    private void addMovementSpeedModifier(LivingEntity entity, double bonus) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        removeMovementSpeedModifier(entity);

        AttributeModifier modifier = new AttributeModifier(
            MOVEMENT_SPEED_MODIFIER_UUID,
            MOVEMENT_SPEED_MODIFIER_NAME,
            bonus,
            AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        attribute.addTransientModifier(modifier);
    }

    /**
     * 移除实体的移动速度修饰符
     * @param entity 目标实体
     */
    private void removeMovementSpeedModifier(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(MOVEMENT_SPEED_MODIFIER_UUID);
    }

    /**
     * 清理玩家数据
     * @param playerUUID 玩家UUID
     */
    public static void clearPlayerData(UUID playerUUID) {
        TRACKED_THREAT_ENTITIES.remove(playerUUID);
        TICK_COUNTER.remove(playerUUID);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        TRACKED_THREAT_ENTITIES.clear();
        TICK_COUNTER.clear();
    }
}