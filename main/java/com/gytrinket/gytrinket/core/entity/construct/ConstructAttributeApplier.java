package com.gytrinket.gytrinket.core.entity.construct;

import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.PlayerLightPointStoreChangedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    public static void refreshForPlayer(UUID playerUUID, ServerPlayer player) {
        Map<String, Double> constructAttrs = computeConstructAttributes(playerUUID);
        PLAYER_CONSTRUCT_ATTR_CACHE.put(playerUUID, constructAttrs);
        applyAttributesToConstructs(playerUUID, player, constructAttrs);
    }

    public static Map<String, Double> computeConstructAttributes(UUID playerUUID) {
        Map<String, Double> result = new HashMap<>();
        Map<String, ConstructAttributeTarget> targets = ConstructAttributeRegistry.getAllTargets();

        for (Map.Entry<String, ConstructAttributeTarget> entry : targets.entrySet()) {
            String attrName = entry.getKey();
            double value = AttributeManager.getPlayerAttribute(playerUUID, attrName);
            result.put(attrName, value);
        }

        return result;
    }

    private static void applyAttributesToConstructs(UUID playerUUID, ServerPlayer player, Map<String, Double> constructAttrs) {
        Map<UUID, Entity> droneEntities = ConstructManager.getInstance()
                .getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

        for (Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity droneEntity && droneEntity.isAlive()) {
                applyAttributesToDrone(playerUUID, droneEntity, constructAttrs);
            }
        }
    }

    public static void applyAttributesToDrone(UUID playerUUID, DroneConstructEntity drone, Map<String, Double> constructAttrs) {
        Set<String> instanceTags = new HashSet<>();
        if (drone.getDroneConstruct() != null) {
            instanceTags.addAll(drone.getDroneConstruct().getCurrentTags());
        }
        if (drone.isCommander()) {
            instanceTags.add("commander");
        }

        ConstructType type = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);

        double healthBase = 0;
        double healthPercent = 1.0;
        double healthIndependent = 1.0;
        double damageBase = 0;
        double damagePercent = 1.0;
        double damageIndependent = 1.0;
        double attackSpeedPercent = 1.0;
        double attackSpeedIndependent = 1.0;

        for (Map.Entry<String, Double> entry : constructAttrs.entrySet()) {
            String attrName = entry.getKey();
            double value = entry.getValue();

            ConstructAttributeTarget target = ConstructAttributeRegistry.getTarget(attrName);
            if (target == null) {
                continue;
            }

            if (type == null || !target.matches(type, instanceTags)) {
                continue;
            }

            switch (target.getEffectType()) {
                case HEALTH -> {
                    AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
                    if (def != null) {
                        switch (def.getType()) {
                            case BASE -> healthBase += value;
                            case PERCENT -> healthPercent *= value;
                            case INDEPENDENT_MULTIPLY -> healthIndependent *= value;
                        }
                    }
                }
                case DAMAGE -> {
                    AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
                    if (def != null) {
                        switch (def.getType()) {
                            case BASE -> damageBase += value;
                            case PERCENT -> damagePercent *= value;
                            case INDEPENDENT_MULTIPLY -> damageIndependent *= value;
                        }
                    }
                }
                case ATTACK_SPEED -> {
                    AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
                    if (def != null) {
                        switch (def.getType()) {
                            case PERCENT -> attackSpeedPercent *= value;
                            case INDEPENDENT_MULTIPLY -> attackSpeedIndependent *= value;
                        }
                    }
                }
            }
        }

        double baseMaxHealth = drone.getBaseMaxHealth();
        double finalMaxHealth = (baseMaxHealth + healthBase) * healthPercent * healthIndependent;
        double baseAttackDamage = drone.getBaseAttackDamage();
        double finalAttackDamage = (baseAttackDamage + damageBase) * damagePercent * damageIndependent;
        double finalAttackSpeedMultiplier = attackSpeedPercent * attackSpeedIndependent;

        applyHealthModifier(drone, finalMaxHealth);
        applyDamageModifier(drone, finalAttackDamage);
        drone.setAttackSpeedMultiplier(finalAttackSpeedMultiplier);
    }

    private static void applyHealthModifier(DroneConstructEntity drone, double targetMaxHealth) {
        AttributeInstance healthAttr = drone.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }

        double oldMaxHealth = drone.getMaxHealth();
        float currentHealth = drone.getHealth();
        float healthRatio = oldMaxHealth > 0 ? currentHealth / (float) oldMaxHealth : 1.0f;

        ModifierHelper.removeModifier(healthAttr, CONSTRUCT_HEALTH_MODIFIER_ID);
        ModifierHelper.removeModifier(healthAttr, CONSTRUCT_HEALTH_PERCENT_MODIFIER_ID);

        double baseHealth = drone.getBaseMaxHealth();
        double addition = targetMaxHealth - baseHealth;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(CONSTRUCT_HEALTH_MODIFIER_ID, addition, AttributeModifier.Operation.ADD_VALUE);
            healthAttr.addPermanentModifier(addModifier);
        }

        double newMaxHealth = drone.getMaxHealth();
        float newHealth = (float) (newMaxHealth * healthRatio);
        if (newHealth > newMaxHealth) {
            newHealth = (float) newMaxHealth;
        }
        drone.setHealth(newHealth);
    }

    private static void applyDamageModifier(DroneConstructEntity drone, double targetDamage) {
        AttributeInstance damageAttr = drone.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr == null) {
            return;
        }

        ModifierHelper.removeModifier(damageAttr, CONSTRUCT_DAMAGE_MODIFIER_ID);
        ModifierHelper.removeModifier(damageAttr, CONSTRUCT_DAMAGE_PERCENT_MODIFIER_ID);

        double baseDamage = drone.getBaseAttackDamage();
        double addition = targetDamage - baseDamage;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(CONSTRUCT_DAMAGE_MODIFIER_ID, addition, AttributeModifier.Operation.ADD_VALUE);
            damageAttr.addPermanentModifier(addModifier);
        }
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation modifierId) {
        ModifierHelper.removeModifier(attribute, modifierId);
    }

    public static double getCachedAttribute(UUID playerUUID, String attributeName) {
        Map<String, Double> cached = PLAYER_CONSTRUCT_ATTR_CACHE.get(playerUUID);
        if (cached == null) {
            return 0.0;
        }
        return cached.getOrDefault(attributeName, 0.0);
    }

    public static double getEffectiveMaxCount(UUID playerUUID, ConstructType type) {
        int baseCount = type.getMaxCount();
        double baseBonus = 0;
        double percent = 1.0;
        double independent = 1.0;

        Set<String> countAttrs = ConstructAttributeRegistry.getAttributesByEffectType(ConstructAttributeTarget.EffectType.MAX_COUNT);
        for (String attrName : countAttrs) {
            ConstructAttributeTarget target = ConstructAttributeRegistry.getTarget(attrName);
            if (target == null || !target.matches(type)) {
                continue;
            }
            double value = AttributeManager.getPlayerAttribute(playerUUID, attrName);
            AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
            if (def == null) {
                continue;
            }
            switch (def.getType()) {
                case BASE -> baseBonus += value;
                case PERCENT -> percent *= value;
                case INDEPENDENT_MULTIPLY -> independent *= value;
            }
        }

        return Math.round((baseCount + baseBonus) * percent * independent);
    }

    public static double getEffectiveBuildSpeed(UUID playerUUID, ConstructType type) {
        double percent = 1.0;
        double independent = 1.0;

        Set<String> buildAttrs = ConstructAttributeRegistry.getAttributesByEffectType(ConstructAttributeTarget.EffectType.BUILD_SPEED);
        for (String attrName : buildAttrs) {
            ConstructAttributeTarget target = ConstructAttributeRegistry.getTarget(attrName);
            if (target != null && target.matches(type)) {
                double value = AttributeManager.getPlayerAttribute(playerUUID, attrName);
                AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
                if (def != null) {
                    switch (def.getType()) {
                        case PERCENT -> percent *= value;
                        case INDEPENDENT_MULTIPLY -> independent *= value;
                    }
                }
            }
        }

        return percent * independent;
    }

    public static void clearPlayerCache(UUID playerUUID) {
        PLAYER_CONSTRUCT_ATTR_CACHE.remove(playerUUID);
    }
}
