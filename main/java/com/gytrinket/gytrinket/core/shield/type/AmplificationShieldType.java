package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * 3. 每个威胁实体提供固定伤害加成（5%），并按敌人最大生命提供额外加成（每点+1%）
 * 4. 攻击伤害加成上限为100%
 * 5. 当有护盾值时，提供移动速度独立乘区加成（受护盾效果影响，不受威胁数量影响）
 * <p>
 * 属性影响：
 * - shield_effect 属性组：影响基础加成、上限和移动速度加成
 * - shield_effect_radius 属性组：影响检测半径
 * <p>
 * 伤害加成施加方式：
 * - 玩家：通过 AttributeManager 动态属性（类似幽灵机身的伤害修改机制），
 *   与其他动态属性（如幽灵机身）按独立乘区叠加
 * - 护盾移植时：被保护实体为玩家时同样使用动态属性；非玩家实体（构造体/其他生物）
 *   回退为原版 ATTACK_DAMAGE 修饰符
 * <p>
 * 护盾移植支持：
 * - 当护盾移植时，在被保护实体位置检测威胁
 * - 攻击伤害通过动态属性/修饰符施加，移动速度修饰符直接施加在被保护实体上
 */
public class AmplificationShieldType implements IShieldType {

    /** 动态属性命名空间（用于 AttributeManager 动态属性，与其他系统独立叠加） */
    private static final String NAMESPACE = "amplification_shield";

    /** 攻击伤害独立乘区属性名（与幽灵机身共用同一属性，按乘区叠加） */
    private static final String ATTR_DAMAGE_INDEPENDENT = "attack_damage_independent";

    /** 移动速度独立乘区属性名（玩家经属性池施加，面板可显示；非玩家实体回退原版修饰符） */
    private static final String ATTR_MOVEMENT_SPEED_INDEPENDENT = "movement_speed_independent";

    /** 追踪的威胁实体：玩家UUID -> 威胁实体集合 */
    private static final Map<UUID, Set<Entity>> TRACKED_THREAT_ENTITIES = new HashMap<>();
    
    /** 计时器：玩家UUID -> 刻数 */
    private static final Map<UUID, Integer> TICK_COUNTER = new HashMap<>();

    /** 增幅进度（0~1）：客户端渲染亮度的驱动值，0=无危险物/仅基础增幅，1=达到增幅上限 */
    private static final Map<UUID, Double> AMPLIFICATION_PROGRESS = new HashMap<>();
    
    /** 威胁检测间隔（刻） */
    private static final int CHECK_INTERVAL = 5;
    
    /** 攻击伤害修饰符ID（仅用于非玩家实体回退） */
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "amplification_shield_attack_damage");

    /** 移动速度修饰符ID */
    public static final ResourceLocation MOVEMENT_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "amplification_shield_movement_speed");
    
    /** 获取基础增幅值 */
    private static double getBaseAmplification() {
        return Config.getAmplificationBaseAmplification();
    }
    
    /** 获取每个威胁的固定增幅值 */
    private static double getThreatAmplification() {
        return Config.getAmplificationThreatAmplification();
    }
    
    /** 获取每个威胁按最大生命提供的增幅值（每点） */
    private static double getHealthAmplificationPerPoint() {
        return Config.getAmplificationHealthAmplificationPerPoint();
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
        AMPLIFICATION_PROGRESS.put(playerUUID, 0.0);
        AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
        AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_MOVEMENT_SPEED_INDEPENDENT);
        removeAttackDamageModifier(player);
        removeMovementSpeedModifier(player);
        
        // 移除被保护实体上的动态属性/修饰符
        for (LivingEntity protectedEntity : ShieldTransferManager.getProtectedEntities(playerUUID, player.level())) {
            if (protectedEntity instanceof Player targetPlayer) {
                AttributeManager.removeDynamicAttribute(targetPlayer.getUUID(), NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
            } else {
                removeAttackDamageModifier(protectedEntity);
            }
            removeMovementSpeedModifier(protectedEntity);
        }
        
        TRACKED_THREAT_ENTITIES.remove(playerUUID);
        TICK_COUNTER.remove(playerUUID);

        // 同步失活状态到客户端，隐藏渲染贴图
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(playerUUID), ShieldManager.getMaxShield(playerUUID));
        }
    }

    /**
     * 每刻更新
     * 1. 检查护盾值，无护盾时清理修饰符
     * 2. 每5刻检测威胁实体（在玩家或被保护实体位置）
     * 3. 更新攻击伤害加成（施加在玩家或被保护实体上）
     * 4. 移动速度加成与威胁检测同频（每5刻）更新，避免属性频繁抖动
     */
    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        UUID playerUUID = player.getUUID();
        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        
        if (currentShield <= 0) {
            // 无护盾时清理所有动态属性/修饰符
            AMPLIFICATION_PROGRESS.put(playerUUID, 0.0);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_MOVEMENT_SPEED_INDEPENDENT);
            removeAttackDamageModifier(player);
            removeMovementSpeedModifier(player);
            for (LivingEntity protectedEntity : ShieldTransferManager.getProtectedEntities(playerUUID, player.level())) {
                if (protectedEntity instanceof Player targetPlayer) {
                    AttributeManager.removeDynamicAttribute(targetPlayer.getUUID(), NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
                    AttributeManager.removeDynamicAttribute(targetPlayer.getUUID(), NAMESPACE, ATTR_MOVEMENT_SPEED_INDEPENDENT);
                } else {
                    removeAttackDamageModifier(protectedEntity);
                }
                removeMovementSpeedModifier(protectedEntity);
            }
            TRACKED_THREAT_ENTITIES.remove(playerUUID);
            // 同步失活状态到客户端，隐藏渲染贴图
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                    ShieldManager.getCurrentShield(playerUUID), ShieldManager.getMaxShield(playerUUID));
            }
            return;
        }
        
        int tickCounter = TICK_COUNTER.getOrDefault(playerUUID, 0);
        tickCounter++;
        TICK_COUNTER.put(playerUUID, tickCounter);

        boolean isCheckTick = tickCounter >= CHECK_INTERVAL;
        if (isCheckTick) {
            TICK_COUNTER.put(playerUUID, 0);
            updateThreatEntities(player);
            // 周期性同步增幅进度到客户端（客户端10刻确认超时，间隔5刻 < 10刻保持贴图可见）
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                    ShieldManager.getCurrentShield(playerUUID), ShieldManager.getMaxShield(playerUUID));
            }
        }

        // 移动速度加成与危险物检查同频更新（仅检查时刻重新施加）
        updateAttackDamageBonus(player, isCheckTick);
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
     * 攻击伤害：基础加成 + 威胁加成，不超过上限（每刻更新，威胁列表每5刻刷新）
     * 威胁加成：每个敌人固定5%，并按敌人最大生命额外加成（每点+1%）
     * 移动速度：基础加成 × 护盾效果（不受威胁数量影响），仅检查时刻（每5刻）重新施加
     * 玩家通过动态属性施加伤害加成（类似幽灵机身），非玩家实体回退为原版修饰符
     */
    private void updateAttackDamageBonus(Player player, boolean applyMovementSpeed) {
        UUID playerUUID = player.getUUID();
        
        double shieldEffect = AttributeManager.getGroupAttribute(playerUUID, "shield_effect");
        
        // 计算基础加成和上限（受护盾效果属性影响）
        double baseBonus = getBaseAmplification() * shieldEffect;
        double maxBonus = getMaxAmplification() * shieldEffect;

        // 计算威胁加成：每个敌人固定5% + 按最大生命每点1%，总量不能超出上限
        Set<Entity> threats = TRACKED_THREAT_ENTITIES.getOrDefault(playerUUID, Collections.emptySet());
        double threatBonus = 0;
        for (Entity threat : threats) {
            double perThreat = getThreatAmplification();
            if (threat instanceof LivingEntity living) {
                perThreat += living.getMaxHealth() * getHealthAmplificationPerPoint();
            }
            threatBonus += perThreat;
        }

        // 计算总加成，不超过上限
        double totalBonus = baseBonus * (1 + threatBonus);
        totalBonus = Math.min(totalBonus, maxBonus);

        // 计算增幅进度（0~1）：基础增幅视为0%，达到上限视为100%，用于客户端渲染亮度
        double progress = 0;
        double progressDenominator = maxBonus - baseBonus;
        if (progressDenominator > 0.0001) {
            progress = (totalBonus - baseBonus) / progressDenominator;
            progress = Math.max(0.0, Math.min(1.0, progress));
        }
        AMPLIFICATION_PROGRESS.put(playerUUID, progress);

        // 计算移动速度加成（受护盾效果影响，不受威胁数量影响）
        double movementSpeedBonus = Config.getAmplificationMovementSpeedBonus() * shieldEffect;

        // 获取需要施加伤害加成的实体
        List<LivingEntity> targetEntities = new ArrayList<>();
        
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            targetEntities.addAll(ShieldTransferManager.getProtectedEntities(playerUUID, player.level()));
            // 玩家自身不再获得伤害/移速加成，移除其动态属性与旧修饰符
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_DAMAGE_INDEPENDENT);
            removeAttackDamageModifier(player);
            removeMovementSpeedModifier(player);
        } else {
            targetEntities.add(player);
        }

        // 给目标实体施加伤害加成：玩家用动态属性，非玩家回退原版修饰符
        for (LivingEntity targetEntity : targetEntities) {
            if (targetEntity == null || !targetEntity.isAlive()) {
                continue;
            }
            if (targetEntity instanceof Player targetPlayer) {
                AttributeManager.setDynamicAttribute(targetPlayer.getUUID(), NAMESPACE, ATTR_DAMAGE_INDEPENDENT, totalBonus);
                // 玩家移动速度同样走属性池（面板显示 + 统一施加）；移除旧直接修饰符避免叠加
                removeMovementSpeedModifier(targetPlayer);
                if (applyMovementSpeed) {
                    AttributeManager.setDynamicAttribute(targetPlayer.getUUID(), NAMESPACE, ATTR_MOVEMENT_SPEED_INDEPENDENT, movementSpeedBonus);
                }
            } else {
                addAttackDamageModifier(targetEntity, totalBonus);
                // 移动速度加成与危险物检查同频：仅在检查时刻重新施加
                if (applyMovementSpeed) {
                    addMovementSpeedModifier(targetEntity, movementSpeedBonus);
                }
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
            ATTACK_DAMAGE_MODIFIER_ID,
            bonus,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
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

        attribute.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
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

        // 数值未变化且修饰符已存在时跳过（避免每刻重复移除/添加造成属性抖动）
        AttributeModifier existing = attribute.getModifier(MOVEMENT_SPEED_MODIFIER_ID);
        if (existing != null && existing.amount() == bonus) {
            return;
        }

        removeMovementSpeedModifier(entity);

        AttributeModifier modifier = new AttributeModifier(
            MOVEMENT_SPEED_MODIFIER_ID,
            bonus,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
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

        attribute.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
    }

    /**
     * 查询增幅护盾的增幅进度（0~1），用于客户端渲染亮度
     * 0 = 无危险物/仅基础增幅，1 = 达到增幅上限
     * @param playerUUID 玩家UUID
     */
    public static double getProgress(UUID playerUUID) {
        return AMPLIFICATION_PROGRESS.getOrDefault(playerUUID, 0.0);
    }

    /**
     * 清理玩家数据
     * @param playerUUID 玩家UUID
     */
    public static void clearPlayerData(UUID playerUUID) {
        TRACKED_THREAT_ENTITIES.remove(playerUUID);
        TICK_COUNTER.remove(playerUUID);
        AMPLIFICATION_PROGRESS.remove(playerUUID);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        TRACKED_THREAT_ENTITIES.clear();
        TICK_COUNTER.clear();
        AMPLIFICATION_PROGRESS.clear();
    }
}