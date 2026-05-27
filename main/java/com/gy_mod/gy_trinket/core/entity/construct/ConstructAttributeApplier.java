package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.attribute.AttributeDefinition;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.event.PlayerLightPointStoreChangedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ConstructAttributeApplier {

    private static final UUID CONSTRUCT_HEALTH_MODIFIER_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901234");
    private static final UUID CONSTRUCT_HEALTH_PERCENT_MODIFIER_UUID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-456789012345");
    private static final UUID CONSTRUCT_DAMAGE_MODIFIER_UUID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-567890123456");
    private static final UUID CONSTRUCT_DAMAGE_PERCENT_MODIFIER_UUID = UUID.fromString("f6a7b8c9-d0e1-2345-fabc-678901234567");

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

        removeModifier(healthAttr, CONSTRUCT_HEALTH_MODIFIER_UUID);
        removeModifier(healthAttr, CONSTRUCT_HEALTH_PERCENT_MODIFIER_UUID);

        double baseHealth = drone.getBaseMaxHealth();
        double addition = targetMaxHealth - baseHealth;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(
                    CONSTRUCT_HEALTH_MODIFIER_UUID,
                    "construct_health_addition",
                    addition,
                    AttributeModifier.Operation.ADDITION
            );
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

        removeModifier(damageAttr, CONSTRUCT_DAMAGE_MODIFIER_UUID);
        removeModifier(damageAttr, CONSTRUCT_DAMAGE_PERCENT_MODIFIER_UUID);

        double baseDamage = drone.getBaseAttackDamage();
        double addition = targetDamage - baseDamage;
        if (addition != 0) {
            AttributeModifier addModifier = new AttributeModifier(
                    CONSTRUCT_DAMAGE_MODIFIER_UUID,
                    "construct_damage_addition",
                    addition,
                    AttributeModifier.Operation.ADDITION
            );
            damageAttr.addPermanentModifier(addModifier);
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
