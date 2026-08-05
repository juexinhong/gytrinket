package com.gytrinket.gytrinket.core.entity.construct;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.swarm.MothershipManager;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.PlayerLightPointStoreChangedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ConstructAttributeApplier {

    private static final ResourceLocation CONSTRUCT_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "construct_health_addition");
    private static final ResourceLocation CONSTRUCT_HEALTH_PERCENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "construct_health_percent");
    private static final ResourceLocation CONSTRUCT_DAMAGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "construct_damage_addition");
    private static final ResourceLocation CONSTRUCT_DAMAGE_PERCENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "construct_damage_percent");

    private static final Map<UUID, Map<String, Double>> PLAYER_CONSTRUCT_ATTR_CACHE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLightPointStoreChanged(PlayerLightPointStoreChangedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
                ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID)
                : null;
        if (player == null) {
            return;
        }

        refreshForPlayer(playerUUID, player);
    }

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

    public static void refreshForPlayer(UUID playerUUID, ServerPlayer player) {
        Map<String, Double> constructAttrs = computeConstructAttributes(playerUUID);
        PLAYER_CONSTRUCT_ATTR_CACHE.put(playerUUID, constructAttrs);
        applyAttributesToConstructs(playerUUID, player, constructAttrs);

        // 属性重算后更新蜂群溢出倍率（基于数量上限，非当前数量）
        updateSwarmOverflowMultiplier(playerUUID);
    }

    /**
     * 计算蜂群数量上限的溢出倍率并存储到 MothershipManager。
     * <p>
     * 仅在属性重算时调用：溢出倍率跟随数量上限变化，而非当前蜂群数量。
     * 当原始上限 > 极限值时，溢出倍率 = 原始上限 / 极限值，用于放大每只蜂群的属性和易伤值。
     */
    private static void updateSwarmOverflowMultiplier(UUID playerUUID) {
        int swarmLimit = Config.getSwarmCountLimit();
        if (swarmLimit <= 0) {
            MothershipManager.setOverflowMultiplier(playerUUID, 1.0);
            return;
        }

        ConstructType swarmType = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
        if (swarmType == null) {
            MothershipManager.setOverflowMultiplier(playerUUID, 1.0);
            return;
        }

        double rawCount = computeRawMaxCount(playerUUID, swarmType);

        if (rawCount > swarmLimit) {
            double overflowMultiplier = rawCount / swarmLimit;
            MothershipManager.setOverflowMultiplier(playerUUID, overflowMultiplier);
        } else {
            MothershipManager.setOverflowMultiplier(playerUUID, 1.0);
        }
    }

    /**
     * 局部刷新：根据脏属性集合，仅刷新受影响的构造体类型。
     * <p>
     * 解析脏属性的目标标签，只对这些类型的构造体重新应用属性。
     * 如果脏属性含非构造体属性或无法确定类型，刷新所有构造体。
     */
    private static void partialRefreshForPlayer(UUID playerUUID, ServerPlayer player, Set<String> dirtyAttributes) {
        // 更新缓存
        Map<String, Double> constructAttrs = computeConstructAttributes(playerUUID);
        PLAYER_CONSTRUCT_ATTR_CACHE.put(playerUUID, constructAttrs);

        // 如果脏属性中含非构造体属性，刷新所有构造体
        if (!dirtyAttributes.stream().allMatch(ConstructAttributeNameParser::isConstructAttribute)) {
            applyAttributesToConstructs(playerUUID, player, constructAttrs);
            return;
        }

        // 从脏属性中解析受影响的构造体类型
        Set<String> affectedTypeIds = new HashSet<>();
        boolean affectsAll = false;
        for (String attrName : dirtyAttributes) {
            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null) continue;

            // 无类型限制 = 影响所有构造体类型
            if (parsed.getConstructTypes().isEmpty()) {
                affectsAll = true;
                break;
            }
            affectedTypeIds.addAll(parsed.getConstructTypes());
        }

        // 如果无法确定类型或影响所有类型，刷新所有构造体
        if (affectsAll || affectedTypeIds.isEmpty()) {
            applyAttributesToConstructs(playerUUID, player, constructAttrs);
            return;
        }

        // 仅刷新受影响的构造体类型
        for (String typeId : affectedTypeIds) {
            if (DroneConstructTypes.DRONE.equals(typeId)) {
                applyToType(playerUUID, typeId, DroneConstructEntity.class,
                        ConstructAttributeApplier::collectDroneTags, constructAttrs);
            } else if (WingmanConstructTypes.WINGMAN.equals(typeId)) {
                applyToType(playerUUID, typeId, WingmanConstructEntity.class,
                        ConstructAttributeApplier::collectWingmanTags, constructAttrs);
            } else if (SwarmConstructTypes.SWARM.equals(typeId)) {
                applyToType(playerUUID, typeId, SwarmConstructEntity.class,
                        ConstructAttributeApplier::collectSwarmTags, constructAttrs);
            }
        }
    }

    /**
     * 构造体主动获取自身属性（三种场景：构建完毕、玩家重登恢复、待机恢复）。
     * <p>
     * 构造体通过此方法获取当前已计算的属性并应用到自身。
     * 如果缓存中没有属性数据，则从 AttributeManager 实时计算。
     */
    public static void fetchAttributesForConstruct(AbstractConstructEntity construct, LivingEntity livingEntity) {
        UUID ownerUUID = construct.getOwnerUUID();
        if (ownerUUID == null) return;

        Map<String, Double> constructAttrs = PLAYER_CONSTRUCT_ATTR_CACHE.get(ownerUUID);
        if (constructAttrs == null) {
            constructAttrs = computeConstructAttributes(ownerUUID);
            PLAYER_CONSTRUCT_ATTR_CACHE.put(ownerUUID, constructAttrs);
        }

        String typeId = construct.getConstructTypeId();
        ConstructType type = ConstructManager.getInstance().getConstructType(typeId);
        Set<String> instanceTags = construct.getInstanceTags();
        applyAttributesToConstruct(construct, instanceTags, type, constructAttrs);
    }

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

    /**
     * 将缓存的构造体属性应用到玩家所有活跃的构造体实体（无人机/僚机/蜂群）。
     */
    private static void applyAttributesToConstructs(UUID playerUUID, ServerPlayer player, Map<String, Double> constructAttrs) {
        applyToType(playerUUID, DroneConstructTypes.DRONE, DroneConstructEntity.class,
                ConstructAttributeApplier::collectDroneTags, constructAttrs);
        applyToType(playerUUID, WingmanConstructTypes.WINGMAN, WingmanConstructEntity.class,
                ConstructAttributeApplier::collectWingmanTags, constructAttrs);
        applyToType(playerUUID, SwarmConstructTypes.SWARM, SwarmConstructEntity.class,
                ConstructAttributeApplier::collectSwarmTags, constructAttrs);
    }

    /**
     * 通用：对指定类型的所有活跃实体应用属性。
     *
     * @param typeId        构造体类型 ID
     * @param entityClass   实体类
     * @param tagsCollector 从实体收集 instanceTags 的回调
     */
    private static <T extends Entity & IConstructEntity> void applyToType(
            UUID playerUUID,
            String typeId,
            Class<T> entityClass,
            java.util.function.Function<T, Set<String>> tagsCollector,
            Map<String, Double> constructAttrs) {
        Map<UUID, Entity> entities = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, typeId);
        if (entities == null || entities.isEmpty()) {
            return;
        }
        ConstructType type = ConstructManager.getInstance().getConstructType(typeId);
        for (Entity entity : entities.values()) {
            if (!entityClass.isInstance(entity)) continue;
            T constructEntity = entityClass.cast(entity);
            if (!constructEntity.isAlive()) continue;
            Set<String> instanceTags = tagsCollector.apply(constructEntity);
            applyAttributesToConstruct(constructEntity, instanceTags, type, constructAttrs);
        }
    }

    private static Set<String> collectDroneTags(DroneConstructEntity drone) {
        Set<String> tags = new HashSet<>();
        if (drone.getDroneConstruct() != null) {
            tags.addAll(drone.getDroneConstruct().getCurrentTags());
        }
        if (drone.isCommander()) {
            tags.add("commander");
        }
        return tags;
    }

    private static Set<String> collectWingmanTags(WingmanConstructEntity wingman) {
        Set<String> tags = new HashSet<>();
        if (wingman.getWingmanConstruct() != null) {
            tags.addAll(wingman.getWingmanConstruct().getCurrentTags());
        }
        return tags;
    }

    private static Set<String> collectSwarmTags(SwarmConstructEntity swarm) {
        Set<String> tags = new HashSet<>();
        if (swarm.getSwarmConstruct() != null) {
            tags.addAll(swarm.getSwarmConstruct().getCurrentTags());
        }
        return tags;
    }

    /**
     * 通用属性应用：基于 {@link IConstructEntity} 接口统一处理所有构造体类型。
     * <p>
     * 计算 HEALTH / DAMAGE / ATTACK_SPEED 三类属性的 BASE/PERCENT/INDEPENDENT_MULTIPLY 加成，
     * 然后通过修饰器应用到实体，并按比例保留当前生命值。
     */
    public static void applyAttributesToConstruct(IConstructEntity entity, Set<String> instanceTags,
                                                   ConstructType type, Map<String, Double> constructAttrs) {
        String typeId = entity.getConstructTypeId();
        double healthBase = 0;
        double healthPercent = 1.0;
        double healthIndependent = 1.0;
        double damageBase = 0;
        double damagePercent = 1.0;
        double damageIndependent = 1.0;
        double attackSpeedPercent = 1.0;
        double attackSpeedIndependent = 1.0;
        double weaponAttackSpeedPercent = 1.0;
        double weaponAttackSpeedIndependent = 1.0;
        double moveSpeedPercent = 1.0;
        double orbitSpeedPercent = 1.0;
        double rotationSpeedPercent = 1.0;

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
                case MOVE_SPEED -> {
                    switch (valueType) {
                        case BASE -> {}
                        case PERCENT -> moveSpeedPercent *= value;
                        case INDEPENDENT_MULTIPLY -> {}
                    }
                }
                case ORBIT_SPEED -> {
                    switch (valueType) {
                        case BASE -> {}
                        case PERCENT -> orbitSpeedPercent *= value;
                        case INDEPENDENT_MULTIPLY -> {}
                    }
                }
                case ROTATION_SPEED -> {
                    switch (valueType) {
                        case BASE -> {}
                        case PERCENT -> rotationSpeedPercent *= value;
                        case INDEPENDENT_MULTIPLY -> {}
                    }
                }
                default -> {} // MAX_COUNT, BUILD_SPEED, EXPLOSIVE_COUNT 不应用到实体属性
            }
        }

        double baseMaxHealth = entity.getBaseMaxHealth();
        double finalMaxHealth = (baseMaxHealth + healthBase) * healthPercent * healthIndependent;
        double baseAttackDamage = entity.getBaseAttackDamage();
        double finalAttackDamage = (baseAttackDamage + damageBase) * damagePercent * damageIndependent;
        double finalAttackSpeedMultiplier = attackSpeedPercent * attackSpeedIndependent;
        double finalWeaponAttackSpeedMultiplier = weaponAttackSpeedPercent * weaponAttackSpeedIndependent;

        LivingEntity livingEntity = (LivingEntity) entity;
        applyHealthModifier(livingEntity, baseMaxHealth, finalMaxHealth);
        applyDamageModifier(livingEntity, baseAttackDamage, finalAttackDamage);
        entity.setAttackSpeedMultiplier(finalAttackSpeedMultiplier);
        entity.setWeaponAttackSpeedMultiplier(finalWeaponAttackSpeedMultiplier);
        entity.setMoveSpeedMultiplier(moveSpeedPercent);
        entity.setOrbitSpeedMultiplier(orbitSpeedPercent);
        entity.setRotationSpeedMultiplier(rotationSpeedPercent);
        // 重置低血量攻速独立乘区（炉心融解模块动态施加，属性重算时清除）
        entity.setLowHpAttackSpeedMultiplier(1.0);
    }

    /**
     * 通用生命值修饰器应用：移除旧修饰器，添加新加成修饰器，并按比例保留当前生命值。
     */
    private static void applyHealthModifier(LivingEntity entity, double baseHealth, double targetMaxHealth) {
        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }

        double oldMaxHealth = entity.getMaxHealth();
        float currentHealth = entity.getHealth();
        float healthRatio = oldMaxHealth > 0 ? currentHealth / (float) oldMaxHealth : 1.0f;

        ModifierHelper.removeModifier(healthAttr, CONSTRUCT_HEALTH_MODIFIER_ID);
        ModifierHelper.removeModifier(healthAttr, CONSTRUCT_HEALTH_PERCENT_MODIFIER_ID);

        double addition = targetMaxHealth - baseHealth;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(CONSTRUCT_HEALTH_MODIFIER_ID, addition, AttributeModifier.Operation.ADD_VALUE);
            healthAttr.addPermanentModifier(addModifier);
        }

        double newMaxHealth = entity.getMaxHealth();
        float newHealth = (float) (newMaxHealth * healthRatio);
        if (newHealth > newMaxHealth) {
            newHealth = (float) newMaxHealth;
        }
        entity.setHealth(newHealth);
    }

    /**
     * 通用伤害修饰器应用：移除旧修饰器，添加新加成修饰器。
     */
    private static void applyDamageModifier(LivingEntity entity, double baseDamage, double targetDamage) {
        AttributeInstance damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr == null) {
            return;
        }

        ModifierHelper.removeModifier(damageAttr, CONSTRUCT_DAMAGE_MODIFIER_ID);
        ModifierHelper.removeModifier(damageAttr, CONSTRUCT_DAMAGE_PERCENT_MODIFIER_ID);

        double addition = targetDamage - baseDamage;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(CONSTRUCT_DAMAGE_MODIFIER_ID, addition, AttributeModifier.Operation.ADD_VALUE);
            damageAttr.addPermanentModifier(addModifier);
        }
    }

    // ===== 类型特定的薄包装方法（保留以兼容现有调用方） =====

    public static void applyAttributesToDrone(UUID playerUUID, DroneConstructEntity drone, Map<String, Double> constructAttrs) {
        ConstructType type = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
        applyAttributesToConstruct(drone, collectDroneTags(drone), type, constructAttrs);
    }

    public static void applyAttributesToWingman(UUID playerUUID, WingmanConstructEntity wingman, Map<String, Double> constructAttrs) {
        ConstructType type = ConstructManager.getInstance().getConstructType(WingmanConstructTypes.WINGMAN);
        applyAttributesToConstruct(wingman, collectWingmanTags(wingman), type, constructAttrs);
    }

    public static void applyAttributesToSwarm(UUID playerUUID, SwarmConstructEntity swarm, Map<String, Double> constructAttrs) {
        ConstructType type = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
        applyAttributesToConstruct(swarm, collectSwarmTags(swarm), type, constructAttrs);
    }

    public static double getCachedAttribute(UUID playerUUID, String attributeName) {
        Map<String, Double> cached = PLAYER_CONSTRUCT_ATTR_CACHE.get(playerUUID);
        if (cached == null) {
            return 0.0;
        }
        return cached.getOrDefault(attributeName, 0.0);
    }

    public static double getEffectiveMaxCount(UUID playerUUID, ConstructType type) {
        double rawCount = computeRawMaxCount(playerUUID, type);

        // 蜂群数量极限值截断（溢出倍率在 refreshForPlayer 中计算，不在此处设置副作用）
        int swarmLimit = Config.getSwarmCountLimit();
        if (swarmLimit > 0 && SwarmConstructTypes.SWARM.equals(type.getId()) && rawCount > swarmLimit) {
            return swarmLimit;
        }

        return rawCount;
    }

    /**
     * 计算构造体数量上限的原始值（不应用极限值截断）。
     */
    private static double computeRawMaxCount(UUID playerUUID, ConstructType type) {
        int baseCount = type.getMaxCount();
        double baseBonus = 0;
        double percent = 1.0;
        double independent = 1.0;
        String typeId = type.getId();

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.MAX_COUNT) continue;
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

        // 向下取整：小数部分累积到整数时才生效，避免半值向上取整导致超量
        return Math.floor((baseCount + baseBonus) * percent * independent);
    }

    public static double getEffectiveBuildSpeed(UUID playerUUID, ConstructType type) {
        double percent = 1.0;
        double independent = 1.0;
        String typeId = type.getId();

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.BUILD_SPEED) continue;
            if (!parsed.matches(typeId, type, Collections.emptySet())) continue;

            AttributeType valueType = parsed.getValueType();
            if (valueType == null) continue;

            double value = entry.getValue();
            switch (valueType) {
                case PERCENT -> percent *= value;
                case INDEPENDENT_MULTIPLY -> independent *= value;
            }
        }

        return percent * independent;
    }

    /**
     * 获取有效爆破弹数量
     * 基础值来自 Config，加上 wingman_explosive_count 属性的加成
     */
    public static int getEffectiveExplosiveCount(UUID playerUUID, ConstructType type) {
        int baseCount = Config.getWingmanExplosiveCount();
        double baseBonus = 0;
        String typeId = type.getId();

        Map<String, Double> allAttrs = AttributeManager.getPlayerAttributes(playerUUID);
        for (Map.Entry<String, Double> entry : allAttrs.entrySet()) {
            String attrName = entry.getKey();
            if (!ConstructAttributeNameParser.isConstructAttribute(attrName)) continue;

            ConstructAttributeNameParser.ParsedAttribute parsed = ConstructAttributeNameParser.parse(attrName);
            if (parsed == null || parsed.getEffectType() != ConstructAttributeNameParser.EffectType.EXPLOSIVE_COUNT) continue;
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
