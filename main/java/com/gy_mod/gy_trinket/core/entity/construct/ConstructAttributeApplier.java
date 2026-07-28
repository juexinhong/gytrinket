package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeDefinition;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeType;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.MothershipManager;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 构造体属性应用器
 * <p>
 * 统一管理所有构造体类型的属性计算和应用逻辑。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>Config 定义构造体属性（属性名以 construct_ 开头，包含标签字段）</li>
 *   <li>属性运算系统（AttributeManager）进行初步运算</li>
 *   <li>属性重算完毕后（PlayerAttributesCalculatedEvent），本器筛选 construct_ 属性，
 *       按属性名中的标签字段匹配对应构造体，并施加属性</li>
 *   <li>构造体构建完毕、玩家重登恢复、待机恢复时，构造体主动获取自身属性</li>
 * </ol>
 * <p>
 * 使用 LOW 优先级监听 {@link PlayerAttributesCalculatedEvent}，
 * 确保动态属性提供者（EvolutionManager、MothershipManager 等）先于本器执行。
 * <p>
 * 新增构造体类型只需在 Config 中定义 construct_{type}_* 属性，即可自动接入属性系统。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ConstructAttributeApplier {

    private static final UUID CONSTRUCT_HEALTH_MODIFIER_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901234");
    private static final UUID CONSTRUCT_DAMAGE_MODIFIER_UUID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-567890123456");

    private static final Map<UUID, Map<String, Double>> PLAYER_CONSTRUCT_ATTR_CACHE = new ConcurrentHashMap<>();

    // ===== 事件监听（LOW 优先级：确保动态属性提供者先执行） =====

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        UUID playerUUID = player.getUUID();

        if (event.isFullRecalculation()) {
            // 全量重算：刷新所有构造体
            refreshForPlayer(playerUUID, player);
        } else {
            // 局部重算：仅刷新受影响的构造体类型
            partialRefreshForPlayer(playerUUID, player, event.getDirtyAttributes());
        }
    }

    // ===== 核心刷新 =====

    /**
     * 全量刷新：重算所有构造体属性并应用到所有活跃构造体。
     */
    public static void refreshForPlayer(UUID playerUUID, ServerPlayer player) {
        Map<String, Double> constructAttrs = computeConstructAttributes(playerUUID);
        PLAYER_CONSTRUCT_ATTR_CACHE.put(playerUUID, constructAttrs);
        applyAttributesToConstructs(playerUUID, player, constructAttrs);
    }

    /**
     * 局部刷新：根据脏属性集合，仅刷新受影响的构造体类型。
     * <p>
     * 解析脏属性名中的类型标识，只对这些类型的构造体重新应用属性。
     * 例如脏属性 construct_wingman_evolution_health_percent 只刷新 wingman 类型构造体。
     */
    private static void partialRefreshForPlayer(UUID playerUUID, ServerPlayer player, Set<String> dirtyAttributes) {
        // 更新缓存
        Map<String, Double> constructAttrs = computeConstructAttributes(playerUUID);
        PLAYER_CONSTRUCT_ATTR_CACHE.put(playerUUID, constructAttrs);

        // 从脏属性中提取受影响的构造体类型
        Set<String> affectedTypes = new HashSet<>();
        for (String attrName : dirtyAttributes) {
            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed != null) {
                affectedTypes.addAll(parsed.getConstructTypes());
            }
        }

        // 如果无法确定类型或脏属性含非构造体属性，刷新所有构造体
        if (affectedTypes.isEmpty() || !dirtyAttributes.stream().allMatch(ConstructAttributeNameParser::isConstructAttribute)) {
            applyAttributesToConstructs(playerUUID, player, constructAttrs);
            return;
        }

        // 仅刷新受影响的构造体类型
        for (String typeId : affectedTypes) {
            Map<UUID, Entity> entities = ConstructManager.getInstance()
                    .getActiveConstructEntities(playerUUID, typeId);

            for (Entity entity : entities.values()) {
                if (entity instanceof IConstructEntity constructEntity && entity.isAlive()) {
                    applyAttributesToConstruct(playerUUID, constructEntity, (LivingEntity) entity, constructAttrs);
                }
            }
        }
    }

    /**
     * 构造体主动获取自身属性（三种场景：构建完毕、玩家重登恢复、待机恢复）。
     * <p>
     * 构造体通过此方法获取当前已计算的属性并应用到自身。
     * 如果缓存中没有属性数据，则从 AttributeManager 实时计算。
     */
    public static void fetchAttributesForConstruct(IConstructEntity construct, LivingEntity livingEntity) {
        UUID ownerUUID = construct.getOwnerUUID();
        if (ownerUUID == null) return;

        Map<String, Double> constructAttrs = PLAYER_CONSTRUCT_ATTR_CACHE.get(ownerUUID);
        if (constructAttrs == null) {
            constructAttrs = computeConstructAttributes(ownerUUID);
            PLAYER_CONSTRUCT_ATTR_CACHE.put(ownerUUID, constructAttrs);
        }

        applyAttributesToConstruct(ownerUUID, construct, livingEntity, constructAttrs);
    }

    // ===== 属性计算 =====

    /**
     * 计算玩家的所有构造体属性。
     * <p>
     * 从 AttributeManager 获取所有属性，筛选以 construct_ 开头的属性。
     */
    public static Map<String, Double> computeConstructAttributes(UUID playerUUID) {
        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        Map<String, Double> result = new HashMap<>();

        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            if (ConstructAttributeNameParser.isConstructAttribute(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    // ===== 通用属性应用（基于 ConstructAttributeNameParser） =====

    /**
     * 将属性应用到玩家所有活跃的构造体实体。
     */
    private static void applyAttributesToConstructs(UUID playerUUID, ServerPlayer player, Map<String, Double> constructAttrs) {
        for (String typeId : ConstructManager.getInstance().getAllConstructTypeIds()) {
            Map<UUID, Entity> entities = ConstructManager.getInstance()
                    .getActiveConstructEntities(playerUUID, typeId);

            for (Entity entity : entities.values()) {
                if (entity instanceof IConstructEntity constructEntity && entity.isAlive()) {
                    applyAttributesToConstruct(playerUUID, constructEntity, (LivingEntity) entity, constructAttrs);
                }
            }
        }
    }

    /**
     * 通用属性应用方法：基于属性名解析匹配并应用属性到构造体实体。
     * <p>
     * 流程：
     * 1. 遍历所有 construct_ 属性
     * 2. 解析属性名，提取类型/效果/值类型/标签等匹配条件
     * 3. 检查是否匹配当前构造体
     * 4. 按效果类型累加属性值
     * 5. 计算最终值并应用
     */
    public static void applyAttributesToConstruct(UUID playerUUID, IConstructEntity construct,
                                                   LivingEntity livingEntity, Map<String, Double> constructAttrs) {
        String typeId = construct.getConstructTypeId();
        ConstructType type = ConstructManager.getInstance().getConstructType(typeId);
        Set<String> instanceTags = construct.getInstanceTags();

        // 累加各效果类型的属性值
        double healthBase = 0, healthPercent = 1.0, healthIndependent = 1.0;
        double damageBase = 0, damagePercent = 1.0, damageIndependent = 1.0;
        double attackSpeedPercent = 1.0, attackSpeedIndependent = 1.0;
        double weaponAttackSpeedPercent = 1.0, weaponAttackSpeedIndependent = 1.0;

        for (Map.Entry<String, Double> entry : constructAttrs.entrySet()) {
            String attrName = entry.getKey();
            double value = entry.getValue();

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || type == null || !parsed.matches(typeId, type, instanceTags)) {
                continue;
            }

            AttributeType valueType = parsed.getValueType();
            if (valueType == null || parsed.getEffectType() == null) continue;

            switch (parsed.getEffectType()) {
                case HEALTH -> {
                    switch (valueType) {
                        case BASE -> healthBase += value;
                        case PERCENT -> healthPercent *= value;
                        case INDEPENDENT_MULTIPLY -> healthIndependent *= value;
                    }
                }
                case DAMAGE -> {
                    switch (valueType) {
                        case BASE -> damageBase += value;
                        case PERCENT -> damagePercent *= value;
                        case INDEPENDENT_MULTIPLY -> damageIndependent *= value;
                    }
                }
                case ATTACK_SPEED -> {
                    switch (valueType) {
                        case BASE -> {} // 攻击速度无 BASE 类型
                        case PERCENT -> attackSpeedPercent *= value;
                        case INDEPENDENT_MULTIPLY -> attackSpeedIndependent *= value;
                    }
                }
                case WEAPON_ATTACK_SPEED -> {
                    switch (valueType) {
                        case BASE -> {} // 武器攻击速度无 BASE 类型
                        case PERCENT -> weaponAttackSpeedPercent *= value;
                        case INDEPENDENT_MULTIPLY -> weaponAttackSpeedIndependent *= value;
                    }
                }
                default -> {} // MAX_COUNT, BUILD_SPEED 不应用到实体属性
            }
        }

        double finalMaxHealth = (construct.getBaseMaxHealth() + healthBase) * healthPercent * healthIndependent;
        double finalAttackDamage = (construct.getBaseAttackDamage() + damageBase) * damagePercent * damageIndependent;
        double finalAttackSpeedMultiplier = attackSpeedPercent * attackSpeedIndependent;
        double finalWeaponAttackSpeedMultiplier = weaponAttackSpeedPercent * weaponAttackSpeedIndependent;

        applyHealthModifier(livingEntity, construct, finalMaxHealth);
        applyDamageModifier(livingEntity, construct, finalAttackDamage);
        construct.setAttackSpeedMultiplier(finalAttackSpeedMultiplier);
        construct.setWeaponAttackSpeedMultiplier(finalWeaponAttackSpeedMultiplier);
    }

    // ===== 通用属性修饰器 =====

    private static void applyHealthModifier(LivingEntity entity, IConstructEntity construct, double targetMaxHealth) {
        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        double oldMaxHealth = entity.getMaxHealth();
        float currentHealth = entity.getHealth();
        float healthRatio = oldMaxHealth > 0 ? currentHealth / (float) oldMaxHealth : 1.0f;

        removeModifier(healthAttr, CONSTRUCT_HEALTH_MODIFIER_UUID);

        double addition = targetMaxHealth - construct.getBaseMaxHealth();
        if (addition != 0) {
            healthAttr.addPermanentModifier(new AttributeModifier(
                    CONSTRUCT_HEALTH_MODIFIER_UUID,
                    "construct_health_addition",
                    addition,
                    AttributeModifier.Operation.ADDITION
            ));
        }

        double newMaxHealth = entity.getMaxHealth();
        float newHealth = (float) (newMaxHealth * healthRatio);
        if (newHealth > newMaxHealth) {
            newHealth = (float) newMaxHealth;
        }
        entity.setHealth(newHealth);
    }

    private static void applyDamageModifier(LivingEntity entity, IConstructEntity construct, double targetDamage) {
        AttributeInstance damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr == null) return;

        removeModifier(damageAttr, CONSTRUCT_DAMAGE_MODIFIER_UUID);

        double addition = targetDamage - construct.getBaseAttackDamage();
        if (addition != 0) {
            damageAttr.addPermanentModifier(new AttributeModifier(
                    CONSTRUCT_DAMAGE_MODIFIER_UUID,
                    "construct_damage_addition",
                    addition,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    private static void removeModifier(AttributeInstance attribute, UUID modifierUuid) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(modifierUuid)) {
                attribute.removeModifier(modifier);
                break;
            }
        }
    }

    // ===== 数量与建造速度计算（基于属性名解析） =====

    public static double getCachedAttribute(UUID playerUUID, String attributeName) {
        Map<String, Double> cached = PLAYER_CONSTRUCT_ATTR_CACHE.get(playerUUID);
        if (cached == null) {
            return 0.0;
        }
        return cached.getOrDefault(attributeName, 0.0);
    }

    /**
     * 获取有效最大数量（含蜂群溢出截断）
     */
    public static double getEffectiveMaxCount(UUID playerUUID, ConstructType type) {
        double rawCount = computeRawMaxCount(playerUUID, type);

        int swarmLimit = Config.getSwarmCountLimit();
        if (swarmLimit > 0 && SwarmConstructTypes.SWARM.equals(type.getId()) && rawCount > swarmLimit) {
            // 蜂群溢出：通过 MothershipManager 设置独立乘区属性
            double overflowMultiplier = rawCount / swarmLimit;
            MothershipManager.setOverflowMultiplier(playerUUID, overflowMultiplier);
            return swarmLimit;
        } else {
            MothershipManager.setOverflowMultiplier(playerUUID, 1.0);
        }

        return rawCount;
    }

    /**
     * 计算原始最大数量（基于属性名解析匹配）
     */
    private static double computeRawMaxCount(UUID playerUUID, ConstructType type) {
        int baseCount = type.getMaxCount();
        double baseBonus = 0;
        double percent = 1.0;
        double independent = 1.0;

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.MAX_COUNT) continue;

            String typeId = type.getId();
            if (!parsed.matches(typeId, type, Collections.emptySet())) continue;

            AttributeType valueType = parsed.getValueType();
            if (valueType == null) continue;

            double value = entry.getValue();
            switch (valueType) {
                case BASE -> baseBonus += value;
                case PERCENT -> percent *= value;
                case INDEPENDENT_MULTIPLY -> independent *= value;
            }
        }

        return Math.floor((baseCount + baseBonus) * percent * independent);
    }

    /**
     * 获取有效建造速度（基于属性名解析匹配）
     */
    public static double getEffectiveBuildSpeed(UUID playerUUID, ConstructType type) {
        double percent = 1.0;
        double independent = 1.0;

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.BUILD_SPEED) continue;

            String typeId = type.getId();
            if (!parsed.matches(typeId, type, Collections.emptySet())) continue;

            AttributeType valueType = parsed.getValueType();
            if (valueType == null) continue;

            double value = entry.getValue();
            switch (valueType) {
                case PERCENT -> percent *= value;
                case INDEPENDENT_MULTIPLY -> independent *= value;
                default -> {}
            }
        }

        return percent * independent;
    }

    /**
     * 获取有效爆破弹数量（基于属性名解析匹配）
     * 基础值来自 Config，加上 construct_wingman_explosive_count_base 属性的加成
     */
    public static int getEffectiveExplosiveCount(UUID playerUUID, ConstructType type) {
        int baseCount = Config.getWingmanExplosiveCount();
        double baseBonus = 0;

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.EXPLOSIVE_COUNT) continue;

            String typeId = type.getId();
            if (!parsed.matches(typeId, type, Collections.emptySet())) continue;

            AttributeType valueType = parsed.getValueType();
            if (valueType == null) continue;

            double value = entry.getValue();
            if (valueType == AttributeType.BASE) {
                baseBonus += value;
            }
        }

        return Math.max(1, (int) Math.floor(baseCount + baseBonus));
    }

    public static void clearPlayerCache(UUID playerUUID) {
        PLAYER_CONSTRUCT_ATTR_CACHE.remove(playerUUID);
    }
}
