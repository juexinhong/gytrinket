package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.gytrinket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConstructAttributeRegistry {

    private static final Map<String, ConstructAttributeTarget> TARGETS = new ConcurrentHashMap<>();

    private static final Map<ConstructAttributeTarget.EffectType, Set<String>> EFFECT_TYPE_INDEX = new EnumMap<>(ConstructAttributeTarget.EffectType.class);

    static {
        for (ConstructAttributeTarget.EffectType type : ConstructAttributeTarget.EffectType.values()) {
            EFFECT_TYPE_INDEX.put(type, new HashSet<>());
        }
    }

    public static void register(String attributeName, ConstructAttributeTarget target) {
        TARGETS.put(attributeName, target);
        EFFECT_TYPE_INDEX.get(target.getEffectType()).add(attributeName);
        gytrinket.LOGGER.info("注册构造体属性目标: {} -> 效果类型: {}, 类别: {}, 标签: {}",
                attributeName, target.getEffectType(), target.getCategories(), target.getTags());
    }

    public static ConstructAttributeTarget getTarget(String attributeName) {
        return TARGETS.get(attributeName);
    }

    public static boolean isConstructAttribute(String attributeName) {
        return TARGETS.containsKey(attributeName);
    }

    public static Set<String> getAttributesByEffectType(ConstructAttributeTarget.EffectType effectType) {
        return Collections.unmodifiableSet(EFFECT_TYPE_INDEX.getOrDefault(effectType, Collections.emptySet()));
    }

    public static Map<String, ConstructAttributeTarget> getAllTargets() {
        return Collections.unmodifiableMap(TARGETS);
    }

    public static void registerDefaults() {
        register("construct_base_count",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.BASIC)
                        .build());

        register("construct_standard_count",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.STANDARD)
                        .build());

        register("construct_standard_count_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.STANDARD)
                        .build());

        register("construct_standard_count_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.STANDARD)
                        .build());

        register("construct_advanced_count",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.ADVANCED)
                        .build());

        register("construct_advanced_count_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.ADVANCED)
                        .build());

        register("construct_advanced_count_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.ADVANCED)
                        .build());

        register("construct_standard_non_weapon_count_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .category(ConstructCategory.STANDARD)
                        .category(ConstructCategory.OTHER)
                        .build());

        register("construct_basic_non_weapon_count_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .category(ConstructCategory.BASIC)
                        .category(ConstructCategory.OTHER)
                        .build());

        register("drone_count",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_count_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_count_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_damage",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_damage_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_damage_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_health",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_health_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("drone_health_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("drone")
                        .build());

        register("construct_health",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_health_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_health_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_damage",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_damage_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_damage_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_attack_speed_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.ATTACK_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_attack_speed_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.ATTACK_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_build_speed_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.BUILD_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("construct_build_speed_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.BUILD_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .build());

        register("drone_assault_attack_speed_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.ATTACK_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("assault")
                        .build());

        register("drone_assault_attack_speed_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.ATTACK_SPEED)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("assault")
                        .build());

        register("drone_defense_health",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("defense")
                        .build());

        register("drone_defense_health_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("defense")
                        .build());

        register("drone_defense_health_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("defense")
                        .build());

        register("commander_health",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("commander_health_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("commander_health_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.HEALTH)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("commander_damage",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("commander_damage_percent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("commander_damage_independent",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.DAMAGE)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("commander")
                        .build());

        register("swarm_count_mothership",
                ConstructAttributeTarget.builder(ConstructAttributeTarget.EffectType.MAX_COUNT)
                        .category(ConstructCategory.CONSTRUCT)
                        .tag("swarm")
                        .build());
    }
}
