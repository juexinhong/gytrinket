package com.gytrinket.gytrinket;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * 模组配置类
 * 负责管理模组的配置项和属性注册
 */
@EventBusSubscriber(modid = gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== 1. 护盾基础属性 (attributes) =====
    public static final ModConfigSpec.ConfigValue<String> ATTRIBUTES_CONFIG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_ATTRIBUTES_CONFIG;

    // ===== 护盾类型系统 (shield_types) =====
    public static final ModConfigSpec.ConfigValue<String> SHIELD_TYPES_CONFIG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_SHIELD_TYPES_CONFIG;

    // ===== 机身类型系统 (body_types) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BODY_ITEMS_CONFIG;

    // ===== 禁用系统 (disable_system) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_DISABLE_TARGETS_CONFIG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_DEPENDENCIES_CONFIG;

    // ===== 1. 光环护盾 (aura_shield) =====
    public static final ModConfigSpec.DoubleValue AURA_RADIUS;
    public static final ModConfigSpec.DoubleValue AURA_DAMAGE;
    public static final ModConfigSpec.IntValue AURA_TRIGGER_FREQUENCY;
    public static final ModConfigSpec.DoubleValue AURA_SHIELD_COST;

    // ===== 2. 虹吸护盾 (siphon_shield) =====
    public static final ModConfigSpec.DoubleValue SIPHON_RADIUS;
    public static final ModConfigSpec.DoubleValue SIPHON_DAMAGE;
    public static final ModConfigSpec.IntValue SIPHON_TICK_INTERVAL;
    public static final ModConfigSpec.DoubleValue SIPHON_HEAL_RATIO;
    public static final ModConfigSpec.IntValue SIPHON_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue SIPHON_EFFECT_PER_STACK;
    public static final ModConfigSpec.DoubleValue SIPHON_MAX_EFFECT;
    public static final ModConfigSpec.DoubleValue SIPHON_DECAY_RATIO;

    // ===== 3. 反射护盾类型 (reflect_shield) =====
    public static final ModConfigSpec.DoubleValue REFLECT_RADIUS;
    public static final ModConfigSpec.DoubleValue REFLECT_SPEED_BASE_MODIFIER;
    public static final ModConfigSpec.DoubleValue REFLECT_SPEED_EXTRA_MODIFIER;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_EFFECT_MULTIPLIER;

    // ===== 4. 增幅护盾 (amplification_shield) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DANGEROUS_ENTITIES;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_BASE_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_THREAT_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_CHECK_RADIUS;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_MAX_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_MOVEMENT_SPEED_BONUS;

    // ===== 5. 跃传护盾 (warp_shield) =====
    public static final ModConfigSpec.IntValue WARP_SHIELD_INVINCIBLE_DURATION;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_WARP_DISTANCE;

    // ===== 6. 屏障 (barrier) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BARRIER_ITEMS;
    public static final ModConfigSpec.DoubleValue BARRIER_MAX_DAMAGE;

    // ===== 7. 反射护盾 (reflect_damage) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REFLECT_DAMAGE_ITEMS;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_RAY_LENGTH;

    // ===== 8. 易爆护盾 (explosive_shield) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXPLOSIVE_SHIELD_ITEMS;
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_SHIELD_DAMAGE;
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_SHIELD_RADIUS;

    // ===== 9. 电能释放 (electric_discharge) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ELECTRIC_DISCHARGE_ITEMS;
    public static final ModConfigSpec.DoubleValue ELECTRIC_DISCHARGE_BURN_CHARGE;
    public static final ModConfigSpec.IntValue ELECTRIC_DISCHARGE_BURN_DURATION;

    // ===== 10. 武器化护盾 (weaponized_shield) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WEAPONIZED_SHIELD_ITEMS;
    public static final ModConfigSpec.DoubleValue WEAPONIZED_SHIELD_VULNERABILITY;
    public static final ModConfigSpec.DoubleValue WEAPONIZED_SHIELD_RADIUS;

    // ===== 11. 镀层 (coating_system) =====
    public static final ModConfigSpec.DoubleValue COATING_REDUCTION_PER_LAYER;

    // ===== 12. 适应性装甲 (adaptive_armor) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADAPTIVE_ARMOR_ITEMS;
    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_DURATION;
    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT;
    public static final ModConfigSpec.DoubleValue ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEMS;

    // ===== 13. 再生护盾 (shield_natural_recovery) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIELD_NATURAL_RECOVERY_ITEMS;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER;

    // ===== 14. 效率 (attack_cooldown_efficiency) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATTACK_COOLDOWN_EFFICIENCY_ITEMS;

    // ===== 15. 转化 (conversion) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONVERSION_ITEMS;
    public static final ModConfigSpec.DoubleValue CONVERSION_RATIO;

    // ===== 16. 二原协议 (binary_protocol) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BINARY_PROTOCOL_ITEMS;

    // ===== 17. 强袭 (assault) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ASSAULT_ITEMS;
    public static final ModConfigSpec.DoubleValue ASSAULT_ATTACK_SPEED_PER_STACK;
    public static final ModConfigSpec.IntValue ASSAULT_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue ASSAULT_SELF_DAMAGE_PER_STACK;
    public static final ModConfigSpec.DoubleValue ASSAULT_MOVEMENT_SPEED_PENALTY;

    // ===== 18. 充能攻击 (charged_attack) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHARGED_ATTACK_ITEMS;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_BASE_CHARGE_RATE;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_SPEED_SCALE_FACTOR;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY;

    // ===== 19. 精密构造 (precision_construct) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRECISION_CONSTRUCT_ITEMS;
    public static final ModConfigSpec.DoubleValue PRECISION_CONSTRUCT_BONUS_PER_LEVEL;

    // ===== 20. 护盾移植 (shield_transfer) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIELD_TRANSFER_ITEMS;
    public static final ModConfigSpec.DoubleValue SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY;

    // ===== 21. 追击阵列/突击无人机 (pursuit_array) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DRONE_MODULE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ASSAULT_DRONE_MODULE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PURSUIT_ARRAY_REQUIRED_ITEMS;
    public static final ModConfigSpec.DoubleValue DRONE_BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue DRONE_BASE_DAMAGE;
    public static final ModConfigSpec.IntValue DRONE_MAX_COUNT;
    public static final ModConfigSpec.DoubleValue DRONE_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue ORBIT_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue ORBIT_ATTACK_RANGE;
    public static final ModConfigSpec.DoubleValue PURSUIT_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue PURSUIT_ATTACK_RANGE;

    // ===== 21.5 无人机斩杀机制 (drone_execute) =====
    public static final ModConfigSpec.BooleanValue DRONE_EXECUTE_ENABLED;

    // ===== 21.6 僚机 (wingman) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WINGMAN_MODULE_ITEMS;
    public static final ModConfigSpec.DoubleValue WINGMAN_BASE_HEALTH;
    public static final ModConfigSpec.IntValue WINGMAN_MAX_COUNT;
    public static final ModConfigSpec.DoubleValue WINGMAN_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue WINGMAN_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue WINGMAN_EXPLOSIVE_COUNT;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSIVE_DAMAGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_RADIUS;

    // ===== 21.7 蜂群 (swarm) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SWARM_MODULE_ITEMS;
    public static final ModConfigSpec.DoubleValue SWARM_BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue SWARM_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SWARM_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue SWARM_ATTACK_RANGE;
    public static final ModConfigSpec.DoubleValue SWARM_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue SWARM_MAX_COUNT;
    public static final ModConfigSpec.IntValue SWARM_COUNT_LIMIT;
    public static final ModConfigSpec.DoubleValue SWARM_MOVE_SPEED;
    public static final ModConfigSpec.IntValue SWARM_BUILD_TIME;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_STANDARD;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_ADVANCED;
    public static final ModConfigSpec.DoubleValue SWARM_VULNERABILITY_VALUE;
    public static final ModConfigSpec.DoubleValue SWARM_SHIELD_REPAIR_MULTIPLIER;

    // ===== 22. 宽限协议 (near_death_protection) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NEAR_DEATH_PROTECTION_ITEMS;
    public static final ModConfigSpec.IntValue NEAR_DEATH_PROTECTION_COOLDOWN;
    public static final ModConfigSpec.IntValue NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION;

    // ===== 23. 高等工程 (advanced_engineering) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADVANCED_ENGINEERING_ITEMS;
    public static final ModConfigSpec.DoubleValue ADVANCED_ENGINEERING_BONUS_PER_LEVEL;

    // ===== 24. 最后指令 (near_death_explosion) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NEAR_DEATH_EXPLOSION_ITEMS;
    public static final ModConfigSpec.IntValue NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_INITIAL_SPEED;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION;

    // ===== 24.5 自毁装置 (self_destruct) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SELF_DESTRUCT_ITEMS;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH;

    // ===== 24.6 督战者 (taskmaster) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TASKMASTER_ITEMS;

    // ===== 25. 列队阵列 (formation_array) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORMATION_ARRAY_REQUIRED_ITEMS;
    public static final ModConfigSpec.DoubleValue FORMATION_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue FORMATION_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue FORMATION_ATTACK_PASS_DELAY;

    // ===== 26. 指挥官 (commander) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMMANDER_REQUIRED_ITEMS;
    public static final ModConfigSpec.IntValue COMMANDER_MAX_COUNT;
    public static final ModConfigSpec.IntValue COMMANDER_APPOINT_DELAY;
    public static final ModConfigSpec.DoubleValue COMMANDER_VULNERABILITY;

    // ===== 27. 守卫阵列/防御无人机 (guard_array) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFENSE_DRONE_MODULE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GUARD_ARRAY_REQUIRED_ITEMS;
    public static final ModConfigSpec.DoubleValue GUARD_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue GUARD_ATTACK_RANGE;

    // ===== 28. 弧形屏障 (arc_barrier) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ARC_BARRIER_ITEMS;
    public static final ModConfigSpec.DoubleValue ARC_BARRIER_POSITION_DEVIATION_THRESHOLD;

    // ===== 29. 反制脉冲 (counter_pulse) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COUNTER_PULSE_ITEMS;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue COUNTER_PULSE_BASE_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue COUNTER_PULSE_BASE_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_CHARGE_INTERVAL;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_MAX_CHARGE_LEVEL;

    // ===== 30. 重塑 (reshaping) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESHAPING_ITEMS;
    public static final ModConfigSpec.DoubleValue RESHAPING_HEAL_RATE;
    public static final ModConfigSpec.DoubleValue RESHAPING_BASE_DAMAGE_REDUCTION;
    public static final ModConfigSpec.IntValue RESHAPING_DAMAGE_REDUCTION_DURATION;

    // ===== 31. 充能护盾 (charged_shield) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHARGED_SHIELD_ITEMS;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_CHARGE_RATIO;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_MAX_BONUS;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY;

    // ===== 35. 积怨 (grudge) =====
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GRUDGE_ITEMS;
    public static final ModConfigSpec.DoubleValue GRUDGE_CONVERSION_RATIO;
    public static final ModConfigSpec.DoubleValue GRUDGE_FADE_BASE;
    public static final ModConfigSpec.DoubleValue GRUDGE_FADE_PERCENT;
    public static final ModConfigSpec.DoubleValue GRUDGE_MOVEMENT_SPEED_PENALTY;

    // ===== 32. 升级系统 (upgrade_system) =====
    public static final ModConfigSpec.ConfigValue<Boolean> UPGRADE_SYSTEM_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPGRADE_PATHS;

    // ===== 33. 快速装备 (quick_equip) =====
    public static final ModConfigSpec.IntValue QUICK_EQUIP_UPGRADE_POINTS_COST;

    // ===== 34. 其他通用设置 =====
    public static final ModConfigSpec.ConfigValue<Boolean> HARDCORE_MODE_ENABLED;
    public static final ModConfigSpec.IntValue SHIELD_BLOCK_INVULNERABLE_TICKS;
    public static final ModConfigSpec.DoubleValue IGNITE_DEFAULT_DAMAGE;
    public static final ModConfigSpec.IntValue IGNITE_DEFAULT_DURATION;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_PLAYER_HEALTH;
    public static final ModConfigSpec.ConfigValue<Boolean> NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY;
    public static final ModConfigSpec.IntValue HOSTILE_TARGET_MARK_DURATION;

    static final ModConfigSpec SPEC;

    private static boolean initialized = false;

    static {
        // ===== 1. 护盾基础属性 =====
        BUILDER.comment("属性系统配置").push("attributes");

        ATTRIBUTES_CONFIG = BUILDER.comment(
            "属性定义配置",
            "格式：属性名:计算方法:属性组（属性组可选）",
            "计算方法：",
            "  - BASE（底数方法）：所有属性值做底数相加",
            "  - PERCENT（百分比方法）：所有属性值做百分比相乘，默认值为1.0",
            "  - INDEPENDENT_MULTIPLY（独立乘区方法）：所有属性值做独立相乘，默认值为1.0",
            "属性组：将相关属性分组，便于统一计算（如 shield_effect_percent 和 shield_effect_independent 都属于 shield_effect 组）",
            "示例：shield_base:BASE,shield_effect_percent:PERCENT:shield_effect,shield_effect_independent:INDEPENDENT_MULTIPLY:shield_effect"
        ).define("attributeDefinitions",
            "shield_base:BASE:shield," +
            "shield_percent:PERCENT:shield," +
            "shield_independent:INDEPENDENT_MULTIPLY:shield," +
            "shield_cooldown_reduction_percent:PERCENT:shield_cooldown_reduction," +
            "shield_cooldown_reduction_independent:INDEPENDENT_MULTIPLY:shield_cooldown_reduction," +
            "shield_cooldown_time:BASE," +
            "shield_hit_cooldown_extend:BASE," +
            "shield_hit_cooldown_extend_multiplier:INDEPENDENT_MULTIPLY," +
            "shield_hit_cooldown_extend_final_multiplier:INDEPENDENT_MULTIPLY," +
            "shield_effect_percent:PERCENT:shield_effect," +
            "shield_effect_independent:INDEPENDENT_MULTIPLY:shield_effect," +
            "shield_effect_radius:INDEPENDENT_MULTIPLY:shield_effect_radius," +
            "shield_damage_reduction:INDEPENDENT_MULTIPLY," +
            "shield_self_damage_reduction:INDEPENDENT_MULTIPLY," +
            "player_health:BASE:player_health," +
            "player_health_percent:PERCENT:player_health," +
            "player_health_independent:INDEPENDENT_MULTIPLY:player_health," +
            "player_damage_reduction:INDEPENDENT_MULTIPLY," +
            "player_self_damage_reduction:INDEPENDENT_MULTIPLY," +
            "coating:BASE," +
            "adaptive_armor_duration:PERCENT," +
            "recovery_efficiency_percent:PERCENT:recovery_efficiency," +
            "recovery_efficiency_independent:INDEPENDENT_MULTIPLY:recovery_efficiency," +
            "attack_damage:BASE:attack_damage," +
            "attack_damage_percent:PERCENT:attack_damage," +
            "attack_damage_independent:INDEPENDENT_MULTIPLY:attack_damage," +
            "attack_speed_percent:PERCENT:attack_speed," +
            "attack_speed_independent:INDEPENDENT_MULTIPLY:attack_speed," +
            "combo:BASE," +
            "movement_speed_percent:PERCENT:movement_speed," +
            "movement_speed_independent:INDEPENDENT_MULTIPLY:movement_speed," +
            "knockback_resistance:BASE," +
            "player_knockback_percent:PERCENT:player_knockback," +
            "construct_base_count:BASE," +
            "construct_standard_count:BASE:construct_standard_count," +
            "construct_standard_count_percent:PERCENT:construct_standard_count," +
            "construct_standard_count_independent:INDEPENDENT_MULTIPLY:construct_standard_count," +
            "construct_advanced_count:BASE:construct_advanced_count," +
            "construct_advanced_count_percent:PERCENT:construct_advanced_count," +
            "construct_advanced_count_independent:INDEPENDENT_MULTIPLY:construct_advanced_count," +
            "construct_standard_non_weapon_count_percent:PERCENT:construct_standard_non_weapon_count," +
            "construct_basic_non_weapon_count_percent:PERCENT:construct_basic_non_weapon_count," +
            "drone_count:BASE:drone_count," +
            "drone_count_percent:PERCENT:drone_count," +
            "drone_count_independent:INDEPENDENT_MULTIPLY:drone_count," +
            "drone_damage:BASE:drone_damage," +
            "drone_damage_percent:PERCENT:drone_damage," +
            "drone_damage_independent:INDEPENDENT_MULTIPLY:drone_damage," +
            "drone_health:BASE:drone_health," +
            "drone_health_percent:PERCENT:drone_health," +
            "drone_health_independent:INDEPENDENT_MULTIPLY:drone_health," +
            "construct_health:BASE:construct_health," +
            "construct_health_percent:PERCENT:construct_health," +
            "construct_health_independent:INDEPENDENT_MULTIPLY:construct_health," +
            "construct_damage:BASE:construct_damage," +
            "construct_damage_percent:PERCENT:construct_damage," +
            "construct_damage_independent:INDEPENDENT_MULTIPLY:construct_damage," +
            "construct_attack_speed_percent:PERCENT:construct_attack_speed," +
            "construct_attack_speed_independent:INDEPENDENT_MULTIPLY:construct_attack_speed," +
            "construct_build_speed_percent:PERCENT:construct_build_speed," +
            "construct_build_speed_independent:INDEPENDENT_MULTIPLY:construct_build_speed," +
            "drone_assault_attack_speed_percent:PERCENT:drone_assault_attack_speed," +
            "drone_assault_attack_speed_independent:INDEPENDENT_MULTIPLY:drone_assault_attack_speed," +
            "drone_defense_health:BASE:drone_defense_health," +
            "drone_defense_health_percent:PERCENT:drone_defense_health," +
            "drone_defense_health_independent:INDEPENDENT_MULTIPLY:drone_defense_health," +
            "commander_health:BASE:commander_health," +
            "commander_health_percent:PERCENT:commander_health," +
            "commander_health_independent:INDEPENDENT_MULTIPLY:commander_health," +
            "commander_damage:BASE:commander_damage," +
            "commander_damage_percent:PERCENT:commander_damage," +
            "commander_damage_independent:INDEPENDENT_MULTIPLY:commander_damage," +
            "explosion_damage_percent:PERCENT:explosion_damage," +
            "explosion_damage_independent:INDEPENDENT_MULTIPLY:explosion_damage," +
            "explosion_radius_percent:PERCENT:explosion_radius," +
            "explosion_radius_independent:INDEPENDENT_MULTIPLY:explosion_radius," +
            "swarm_count_mothership:BASE:swarm_count_mothership"
        );

        ITEM_ATTRIBUTES_CONFIG = BUILDER.comment(
            "物品属性配置",
            "格式：物品ID|属性名=数值|属性名=数值",
            "使用 | 分隔物品ID和属性，使用 = 分隔属性名和值",
            "每个物品单独占一行",
            "示例：minecraft:diamond|shield_base=10.0|shield_percent=0.1"
        ).defineListAllowEmpty("itemAttributes",
            List.of(
                "gytrinket:shield_gy|shield_base=8.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy1|shield_base=12.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy2|shield_base=16.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy3|shield_base=20.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_aura_ring|shield_base=8.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring1|shield_base=12.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring2|shield_base=16.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring3|shield_base=20.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_siphon|shield_base=8.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.3",
                "gytrinket:shield_siphon1|shield_base=12.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.2",
                "gytrinket:shield_siphon2|shield_base=16.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.1",
                "gytrinket:shield_siphon3|shield_base=20.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=0.0",

                "gytrinket:shield_reflect|shield_base=9.6|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect1|shield_base=14.4|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect2|shield_base=19.2|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect3|shield_base=24.0|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_amplifier|shield_base=4.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier1|shield_base=6.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier2|shield_base=8.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier3|shield_base=10.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_warp|shield_base=18.0|shield_cooldown_time=10.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp1|shield_base=18.0|shield_cooldown_time=8.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp2|shield_base=18.0|shield_cooldown_time=7.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp3|shield_base=18.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_amplifier_module|shield_percent=0.2",
                "gytrinket:barrier_shield_module|shield_percent=0.05",
                "gytrinket:reflect_shield_module|shield_percent=0.05",
                "gytrinket:ultimate_shield_module|shield_base=11.0|shield_damage_reduction=-0.15|shield_effect_percent=0.15|shield_hit_cooldown_extend_final_multiplier=-0.5|player_health_independent=-0.85",

                "gytrinket:shield_cooldown_reduction_module|shield_cooldown_reduction_percent=0.2",
                "gytrinket:shield_quick_charge_module|shield_cooldown_reduction_percent=0.25|shield_independent=-0.25",
                "gytrinket:explosive_shield_module|shield_percent=0.05",

                "gytrinket:shield_effect_boost_module|shield_effect_percent=0.1|shield_effect_radius=0.25",
                "gytrinket:divergent_shield_module|shield_effect_radius=0.5|shield_percent=-0.1|shield_cooldown_reduction_independent=-0.1",
                "gytrinket:focused_shield_module|shield_effect_percent=0.25|shield_percent=0.05|shield_effect_radius=-0.2",

                "gytrinket:health_boost_module|player_health_percent=0.2",
                "gytrinket:coating_module|coating=3",
                "gytrinket:colossus_module|player_health_percent=0.25|player_knockback_percent=0.4|knockback_resistance=0.2|movement_speed_independent=-0.25",

                "gytrinket:bond_module|adaptive_armor_duration=0.2",
                "gytrinket:core_armor_module|shield_base=1.0|player_health=1.0|shield_self_damage_reduction=-0.2|player_self_damage_reduction=-0.2|adaptive_armor_duration=0.2",

                "gytrinket:regen_module|recovery_efficiency_percent=0.4",
                "gytrinket:regen_shield_module|recovery_efficiency_percent=0.1",

                "gytrinket:transformation_module|shield_base=1.0|player_health=1.0",

                "gytrinket:fast_shooting_module|attack_speed_percent=0.15",
                "gytrinket:burst_fire_module|attack_speed_percent=0.2|combo=2",

                "gytrinket:thrust_boost_module|movement_speed_percent=0.25",
                "gytrinket:aerodynamic_framework_module|movement_speed_percent=0.25|player_health_independent=-0.1|player_knockback_percent=-0.2|knockback_resistance=-0.2",

                "gytrinket:precision_construct_module|construct_health_percent=0.25|construct_build_speed_percent=0.10",
                "gytrinket:shield_transfer_module|shield_damage_reduction=-0.5|player_damage_reduction=-0.1|player_health=2|player_health_percent=0.1",

                "gytrinket:drone_module|",
                "gytrinket:advanced_engineering_module|drone_count=1",

                "gytrinket:assault_drone_module|drone_assault_attack_speed_percent=0.2|drone_count=1",
                "gytrinket:wing_commander_module|drone_count=1|commander_health_percent=2.0|commander_damage_percent=2.0",

                "gytrinket:defense_drone_module|drone_defense_health_percent=1.5",

                "gytrinket:self_destruct_module|",
                "gytrinket:taskmaster_module|construct_standard_non_weapon_count_percent=1|construct_basic_non_weapon_count_percent=1|construct_advanced_count=1",

                "gytrinket:guardian|shield_effect_percent=0.10|shield_effect_radius=0.25|shield_damage_reduction=-0.1|attack_speed_percent=-0.1",

                "gytrinket:mothership_body|shield_percent=0.15|player_health_percent=0.15|movement_speed_percent=0.4|knockback_resistance=0.2",

                "gytrinket:grudge_module|player_health_percent=0.05|attack_damage_percent=0.05|movement_speed_independent=-0.1",

                "gytrinket:quick_reconstruction_module|recovery_efficiency_percent=1.0|player_health=10|coating=2",

                "minecraft:command_block|shield_effect_percent=1.0|shield_cooldown_reduction_percent=0.8|shield_damage_reduction=-0.9|shield_self_damage_reduction=-0.9|player_health_percent=1|attack_speed_percent=1|attack_damage_percent=1|knockback_resistance=1|player_knockback_percent=1|movement_speed_percent=0.5|player_damage_reduction=-0.9|player_self_damage_reduction=-0.9|recovery_efficiency=0.5"
            ),
            s -> true
        );

        BUILDER.pop();

        // ===== 护盾类型系统 =====
        BUILDER.comment("护盾类型系统配置").push("shield_types");

        SHIELD_TYPES_CONFIG = BUILDER.comment(
            "护盾类型定义配置",
            "格式：类型名=是否兼容;类型名=是否兼容;...",
            "是否兼容：true=可与其他类型共存，false=不兼容，第一个激活的生效",
            "示例：none=true;aura=false"
        ).define("shieldTypeDefinitions", "none=true;aura=false;siphon=false;reflect=false;amplification=false;warp=false");

        ITEM_SHIELD_TYPES_CONFIG = BUILDER.comment(
            "物品护盾类型配置",
            "格式：物品ID|类型名,类型名",
            "使用 | 分隔物品ID和类型，使用 , 分隔多个类型",
            "每个物品单独占一行",
            "示例：minecraft:diamond|none"
        ).defineListAllowEmpty("itemShieldTypes",
            List.of(
                "gytrinket:shield_gy|none",
                "gytrinket:shield_gy1|none",
                "gytrinket:shield_gy2|none",
                "gytrinket:shield_gy3|none",
                "gytrinket:shield_aura_ring|aura",
                "gytrinket:shield_aura_ring1|aura",
                "gytrinket:shield_aura_ring2|aura",
                "gytrinket:shield_aura_ring3|aura",
                "gytrinket:shield_siphon|siphon",
                "gytrinket:shield_siphon1|siphon",
                "gytrinket:shield_siphon2|siphon",
                "gytrinket:shield_siphon3|siphon",
                "gytrinket:shield_reflect|reflect",
                "gytrinket:shield_reflect1|reflect",
                "gytrinket:shield_reflect2|reflect",
                "gytrinket:shield_reflect3|reflect",
                "gytrinket:shield_amplifier|amplification",
                "gytrinket:shield_amplifier1|amplification",
                "gytrinket:shield_amplifier2|amplification",
                "gytrinket:shield_amplifier3|amplification",
                "gytrinket:shield_warp|warp",
                "gytrinket:shield_warp1|warp",
                "gytrinket:shield_warp2|warp",
                "gytrinket:shield_warp3|warp"
            ),
            s -> true
        );

        BUILDER.pop();

        // ===== 机身类型系统 =====
        BUILDER.comment("机身类型配置").push("body_types");

        BODY_ITEMS_CONFIG = BUILDER.comment(
            "机身物品配置",
            "格式：物品ID",
            "列在此处的物品为机身物品，玩家光点核心中只生效第一个",
            "示例：gytrinket:guardian"
        ).defineListAllowEmpty("bodyItems",
            List.of(
                "gytrinket:guardian",
                "gytrinket:mothership_body"
            ),
            s -> true
        );

        BUILDER.pop();

        // ===== 禁用系统 =====
        BUILDER.comment("禁用系统配置").push("disable_system");

        ITEM_DISABLE_TARGETS_CONFIG = BUILDER.comment(
            "物品禁用目标配置",
            "格式：物品ID|禁用目标物品ID1,禁用目标物品ID2",
            "当该物品存在于光点核心中且未被禁用时，指定的目标物品将被禁用",
            "没有写则默认不禁用任何物品",
            "示例：gytrinket:item_a|gytrinket:item_b,gytrinket:item_c"
        ).defineListAllowEmpty("itemDisableTargets",
            List.of(
                "gytrinket:assault_drone_module|gytrinket:defense_drone_module",
                "gytrinket:quick_reconstruction_module|gytrinket:shield_gy,gytrinket:shield_gy1,gytrinket:shield_gy2,gytrinket:shield_gy3,gytrinket:shield_aura_ring,gytrinket:shield_aura_ring1,gytrinket:shield_aura_ring2,gytrinket:shield_aura_ring3,gytrinket:shield_siphon,gytrinket:shield_siphon1,gytrinket:shield_siphon2,gytrinket:shield_siphon3,gytrinket:shield_reflect,gytrinket:shield_reflect1,gytrinket:shield_reflect2,gytrinket:shield_reflect3,gytrinket:shield_amplifier,gytrinket:shield_amplifier1,gytrinket:shield_amplifier2,gytrinket:shield_amplifier3,gytrinket:shield_warp,gytrinket:shield_warp1,gytrinket:shield_warp2,gytrinket:shield_warp3,gytrinket:shield_amplifier_module,gytrinket:barrier_shield_module,gytrinket:reflect_shield_module,gytrinket:ultimate_shield_module,gytrinket:shield_cooldown_reduction_module,gytrinket:shield_quick_charge_module,gytrinket:explosive_shield_module,gytrinket:shield_effect_boost_module,gytrinket:divergent_shield_module,gytrinket:focused_shield_module,gytrinket:weaponized_shield_module,gytrinket:regen_shield_module,gytrinket:shield_transfer_module,gytrinket:charged_shield_module,gytrinket:electric_energy_release_module"
            ),
            s -> true);

        ITEM_DEPENDENCIES_CONFIG = BUILDER.comment(
            "物品依赖配置",
            "格式：物品ID|依赖物品ID1,依赖物品ID2",
            "该物品需要至少一个依赖物品存在且未被禁用才能生效",
            "当所有依赖物品都被禁用或不存在时，该物品也会被禁用",
            "没有写则默认不依赖任何物品",
            "示例：gytrinket:assault_drone_module|gytrinket:drone_module"
        ).defineListAllowEmpty("itemDependencies",
            List.of(
            "gytrinket:advanced_engineering_module|gytrinket:drone_module",
            "gytrinket:precision_construct_module|gytrinket:drone_module",
            "gytrinket:assault_drone_module|gytrinket:drone_module",
            "gytrinket:line_formation_module|gytrinket:assault_drone_module",
            "gytrinket:last_order_module|gytrinket:assault_drone_module",
            "gytrinket:wing_commander_module|gytrinket:assault_drone_module",
            "gytrinket:arc_barrier_module|gytrinket:defense_drone_module",
            "gytrinket:reshaping_module|gytrinket:defense_drone_module",
            "gytrinket:counter_pulse_module|gytrinket:defense_drone_module",
            "gytrinket:self_destruct_module|gytrinket:drone_module",
            "gytrinket:charged_shield_module|gytrinket:charged_attack_module",
            "gytrinket:grudge_module|gytrinket:charged_attack_module"
            ),
            s -> true);

        BUILDER.pop();

        // ===== 1. 光环护盾 =====
        BUILDER.comment("光环护盾配置").push("aura_shield");

        AURA_RADIUS = BUILDER.comment("光环护盾半径").defineInRange("auraRadius", 3.5, 0.0, 100.0);
        AURA_DAMAGE = BUILDER.comment("光环护盾伤害").defineInRange("auraDamage", 0.75, 0.0, 100.0);
        AURA_TRIGGER_FREQUENCY = BUILDER.comment("光环护盾触发频率（刻）").defineInRange("auraTriggerFrequency", 5, 1, 200);
        AURA_SHIELD_COST = BUILDER.comment("光环护盾消耗护盾值").defineInRange("auraShieldCost", 0.042, 0.0, 10.0);

        BUILDER.pop();

        // ===== 2. 虹吸护盾 =====
        BUILDER.comment("虹吸护盾配置").push("siphon_shield");

        SIPHON_RADIUS = BUILDER.comment("虹吸护盾基础半径").defineInRange("siphonRadius", 4.0, 0.0, 100.0);
        SIPHON_DAMAGE = BUILDER.comment("虹吸护盾基础伤害量").defineInRange("siphonDamage", 0.3, 0.0, 100.0);
        SIPHON_TICK_INTERVAL = BUILDER.comment("虹吸护盾伤害频率（刻）").defineInRange("siphonTickInterval", 5, 1, 200);
        SIPHON_HEAL_RATIO = BUILDER.comment("虹吸护盾伤害恢复护盾比例").defineInRange("siphonHealRatio", 0.3, 0.0, 1.0);
        SIPHON_DURATION_TICKS = BUILDER.comment("虹吸效果持续时间（刻）").defineInRange("siphonDurationTicks", 20, 1, 200);
        SIPHON_EFFECT_PER_STACK = BUILDER.comment("每层虹吸效果提供的百分比加成").defineInRange("siphonEffectPerStack", 0.025, 0.001, 1.0);
        SIPHON_MAX_EFFECT = BUILDER.comment("虹吸效果最大百分比加成").defineInRange("siphonMaxEffect", 0.4, 0.01, 1.0);
        SIPHON_DECAY_RATIO = BUILDER.comment("虹吸效果消退比率").defineInRange("siphonDecayRatio", 0.03, 0.0, 1.0);

        BUILDER.pop();

        // ===== 3. 反射护盾类型 =====
        BUILDER.comment("反射护盾配置").push("reflect_shield");

        REFLECT_RADIUS = BUILDER.comment("反射护盾半径").defineInRange("reflectRadius", 40.0, 0.0, 100.0);
        REFLECT_SPEED_BASE_MODIFIER = BUILDER.comment("反射弹射物速度基础系数").defineInRange("reflectSpeedBaseModifier", 1.5, 0.0, 10.0);
        REFLECT_SPEED_EXTRA_MODIFIER = BUILDER.comment("反射弹射物速度额外系数（决定护盾效果半径属性能够生效多少.1就是100%）").defineInRange("reflectSpeedExtraModifier", 1.0, 0.0, 10.0);
        REFLECT_DAMAGE_EFFECT_MULTIPLIER = BUILDER.comment("反射弹射物伤害护盾效果系数（乘以护盾效果属性）").defineInRange("reflectDamageEffectMultiplier", 1.0, 0.0, 10.0);

        BUILDER.pop();

        // ===== 4. 增幅护盾 =====
        BUILDER.comment("增幅护盾系统配置").push("amplification_shield");

        DANGEROUS_ENTITIES = BUILDER.comment(
            "危险实体列表",
            "这些实体会被增幅护盾视为威胁，每个威胁增加攻击伤害加成",
            "格式：实体类型ID",
            "示例：minecraft:arrow, minecraft:fireball"
        ).defineListAllowEmpty("dangerousEntities",
            java.util.List.of(
                "minecraft:arrow",
                "minecraft:spectral_arrow",
                "minecraft:trident",
                "minecraft:fireball",
                "minecraft:small_fireball",
                "minecraft:dragon_fireball",
                "minecraft:thrown_potion",
                "minecraft:area_effect_cloud"
            ),
            s -> true
        );

        AMPLIFICATION_BASE_AMPLIFICATION = BUILDER.comment(
            "增幅护盾基础增幅值",
            "当玩家有护盾值时提供的基础攻击伤害加成（独立乘区）",
            "例如：0.2 表示增加20%"
        ).defineInRange("amplificationBaseAmplification", 0.2, 0.0, 2.0);

        AMPLIFICATION_THREAT_AMPLIFICATION = BUILDER.comment(
            "增幅护盾威胁增幅值",
            "每个危险目标增加的攻击伤害加成（独立乘区）",
            "例如：0.5 表示每个威胁增加50%"
        ).defineInRange("amplificationThreatAmplification", 0.5, 0.0, 1.0);

        AMPLIFICATION_CHECK_RADIUS = BUILDER.comment(
            "增幅护盾威胁检测半径（格）",
            "检测玩家周围危险目标的基础半径",
            "该值会受护盾效果半径属性影响"
        ).defineInRange("amplificationCheckRadius", 5.0, 1.0, 20.0);

        AMPLIFICATION_MAX_AMPLIFICATION = BUILDER.comment(
            "增幅护盾最大增幅值",
            "攻击伤害加成的上限（独立乘区）",
            "例如：1.0 表示最大增加100%"
        ).defineInRange("amplificationMaxAmplification", 1.0, 0.0, 3.0);

        AMPLIFICATION_MOVEMENT_SPEED_BONUS = BUILDER.comment(
            "增幅护盾移动速度加成",
            "当有护盾值时，为玩家或被护盾保护的实体提供的移动速度独立乘区加成",
            "该值受护盾效果属性影响，但不受危险物数量提升机制影响",
            "例如：0.2 表示增加20%"
        ).defineInRange("amplificationMovementSpeedBonus", 0.2, 0.0, 2.0);

        BUILDER.pop();

        // ===== 5. 跃传护盾 =====
        BUILDER.comment("跃传护盾系统配置").push("warp_shield");

        WARP_SHIELD_INVINCIBLE_DURATION = BUILDER.comment(
            "跃传护盾玩家无敌时间（刻）",
            "护盾破裂后玩家进入无敌状态的持续时间",
            "例如：15 表示持续15刻（0.75秒）"
        ).defineInRange("warpShieldInvincibleDuration", 15, 1, 100);

        WARP_SHIELD_EXPLOSION_DAMAGE = BUILDER.comment(
            "跃传护盾爆炸基础伤害",
            "护盾破裂时产生的爆炸基础伤害值",
            "该值会受护盾效果属性组影响"
        ).defineInRange("warpShieldExplosionDamage", 7.5, 0.0, 100.0);

        WARP_SHIELD_EXPLOSION_RADIUS = BUILDER.comment(
            "跃传护盾爆炸半径（格）",
            "护盾破裂时爆炸的基础半径",
            "该值会受护盾效果半径属性组影响"
        ).defineInRange("warpShieldExplosionRadius", 3.0, 1.0, 20.0);

        WARP_SHIELD_WARP_DISTANCE = BUILDER.comment(
            "跃传护盾传送距离（格）",
            "护盾破裂时玩家/被保护实体被传送的基础距离",
            "该值会受护盾效果半径属性组影响"
        ).defineInRange("warpShieldWarpDistance", 6.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 6. 屏障 =====
        BUILDER.comment("屏障系统配置").push("barrier");

        BARRIER_ITEMS = BUILDER.comment(
            "屏障处理器启用物品",
            "格式：物品ID",
            "示例：minecraft:shield"
        ).defineListAllowEmpty("barrierItems",
            java.util.List.of("gytrinket:barrier_shield_module"),
            s -> true
        );

        BARRIER_MAX_DAMAGE = BUILDER.comment(
            "屏障限制伤害最大值",
            "当伤害超过此值时，将被限制为此值",
            "示例：5.0"
        ).defineInRange("barrierMaxDamage", 5.0, 0.0, 1000.0);

        BUILDER.pop();

        // ===== 7. 反射护盾 =====
        BUILDER.comment("反射护盾伤害处理器配置").push("reflect_damage");

        REFLECT_DAMAGE_ITEMS = BUILDER.comment(
            "反射护盾伤害处理器启用物品",
            "格式：物品ID",
            "示例：minecraft:diamond"
        ).defineListAllowEmpty("reflectDamageItems",
            java.util.List.of("gytrinket:reflect_shield_module"),
            s -> true
        );

        REFLECT_DAMAGE_BASE_DAMAGE = BUILDER.comment(
            "反射护盾基础伤害值",
            "该伤害会受护盾效果属性影响",
            "示例：1.0"
        ).defineInRange("reflectDamageBaseDamage", 0.7, 0.0, 10.0);

        REFLECT_DAMAGE_RAY_LENGTH = BUILDER.comment(
            "反射护盾射线基础长度（格）",
            "该长度会受护盾效果半径属性影响",
            "示例：5.0"
        ).defineInRange("reflectDamageRayLength", 2.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 8. 易爆护盾 =====
        BUILDER.comment("易爆护盾系统配置").push("explosive_shield");

        EXPLOSIVE_SHIELD_ITEMS = BUILDER.comment(
            "易爆护盾效果启用物品",
            "格式：物品ID",
            "示例：minecraft:tnt"
        ).defineListAllowEmpty("explosiveShieldItems",
            java.util.List.of("gytrinket:explosive_shield_module"),
            s -> true
        );

        EXPLOSIVE_SHIELD_DAMAGE = BUILDER.comment(
            "易爆护盾默认伤害值",
            "该伤害会受护盾效果属性影响",
            "示例：10.0"
        ).defineInRange("explosiveShieldDamage", 10.0, 0.0, 100.0);

        EXPLOSIVE_SHIELD_RADIUS = BUILDER.comment(
            "易爆护盾默认半径（格）",
            "该半径会受护盾效果半径属性影响",
            "示例：5.0"
        ).defineInRange("explosiveShieldRadius", 5.0, 0.0, 10.0);

        BUILDER.pop();

        // ===== 9. 电能释放 =====
        BUILDER.comment("电能释放系统配置").push("electric_discharge");

        ELECTRIC_DISCHARGE_ITEMS = BUILDER.comment(
            "电能释放模块物品",
            "放入光点核心后，玩家可以通过左键触发电能释放",
            "格式：物品ID",
            "示例：minecraft:netherite_sword"
        ).defineListAllowEmpty("electricDischargeItems",
            java.util.List.of("gytrinket:electric_energy_release_module"),
            s -> true
        );

        ELECTRIC_DISCHARGE_BURN_CHARGE = BUILDER.comment(
            "电能释放基础灼烧充能量",
            "每次闪电命中目标时施加的灼烧充能量",
            "该值会受到玩家攻击速度的影响：攻击速度越快，灼烧充能越少",
            "范围：0.1 ~ 10.0"
        ).defineInRange("electricDischargeBurnCharge", 0.5, 0.1, 10.0);

        ELECTRIC_DISCHARGE_BURN_DURATION = BUILDER.comment(
            "电能释放灼烧持续时间（刻）",
            "每次闪电命中后灼烧效果的持续时间",
            "范围：1 ~ 200"
        ).defineInRange("electricDischargeBurnDuration", 10, 1, 200);

        BUILDER.pop();

        // ===== 10. 武器化护盾 =====
        BUILDER.comment("武器化护盾系统配置").push("weaponized_shield");

        WEAPONIZED_SHIELD_ITEMS = BUILDER.comment(
            "武器化护盾物品",
            "放入光点核心后，当护盾值不为0时，对周围危险目标施加易伤效果",
            "格式：物品ID",
            "示例：gytrinket:weaponized_shield_module"
        ).defineListAllowEmpty("weaponizedShieldItems",
            java.util.List.of("gytrinket:weaponized_shield_module"),
            s -> true
        );

        WEAPONIZED_SHIELD_VULNERABILITY = BUILDER.comment(
            "武器化护盾基础易伤值",
            "该值会受到护盾效果属性组影响",
            "范围：0.0 ~ 1.0",
            "示例：0.20"
        ).defineInRange("weaponizedShieldVulnerability", 0.20, 0.0, 10.0);

        WEAPONIZED_SHIELD_RADIUS = BUILDER.comment(
            "武器化护盾基础作用半径（格）",
            "该值会受到护盾效果半径属性组影响",
            "示例：4.0"
        ).defineInRange("weaponizedShieldRadius", 4.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 11. 镀层 =====
        BUILDER.comment("镀层系统配置").push("coating_system");

        COATING_REDUCTION_PER_LAYER = BUILDER.comment("每层镀层减少的伤害量").defineInRange("coatingReductionPerLayer", 0.2, 0.0, 10.0);

        BUILDER.pop();

        // ===== 12. 适应性装甲 =====
        BUILDER.comment("适应性装甲系统配置").push("adaptive_armor");

        ADAPTIVE_ARMOR_ITEMS = BUILDER.comment(
            "适应性装甲启用物品",
            "格式：物品ID",
            "示例：minecraft:netherite_chestplate"
        ).defineListAllowEmpty("adaptiveArmorItems",
            java.util.List.of("gytrinket:adaptive_armor_module"),
            s -> true
        );

        ADAPTIVE_ARMOR_DURATION = BUILDER.comment(
            "适应性装甲叠层持续时间（刻）",
            "每批叠层单独计时"
        ).defineInRange("adaptiveArmorDuration", 50, 1, 6000);

        ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT = BUILDER.comment(
            "单次受到攻击最多添加的装甲叠层数"
        ).defineInRange("adaptiveArmorMaxLayersPerHit", 1000, 1, 10000);

        ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE = BUILDER.comment(
            "每点伤害转化为多少装甲叠层",
            "例如：设为2.0时，受到5点伤害会添加10层装甲叠层"
        ).defineInRange("adaptiveArmorLayersPerDamage", 3.0, 0.1, 10.0);

        ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEMS = BUILDER.comment(
            "适应性装甲护盾效果物品",
            "拥有此物品时，装甲叠层提供的伤害减免会转化为护盾效果属性",
            "格式：物品ID",
            "示例：minecraft:netherite_boots"
        ).defineListAllowEmpty("adaptiveArmorShieldEffectItems",
            java.util.List.of("gytrinket:bond_module"),
            s -> true
        );

        BUILDER.pop();

        // ===== 13. 再生护盾 =====
        BUILDER.comment("护盾自然恢复系统配置").push("shield_natural_recovery");

        SHIELD_NATURAL_RECOVERY_ITEMS = BUILDER.comment(
            "护盾自然恢复物品",
            "放入光点核心后，启用护盾自然恢复",
            "并提供恢复修正值：护盾存在时降低生命恢复，提高护盾恢复",
            "格式：物品ID",
            "示例：gytrinket:shield_recovery_module"
        ).defineListAllowEmpty("shieldNaturalRecoveryItems",
            java.util.List.of("gytrinket:regen_shield_module"),
            s -> true
        );

        NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK = BUILDER.comment(
            "护盾自然恢复基础值（每刻恢复的护盾比例）",
            "例如：0.001 表示每刻恢复最大护盾的 0.4%"
        ).defineInRange("naturalRecoveryShieldRecoveryPerTick", 0.004, 0.0, 0.1);

        NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER = BUILDER.comment(
            "护盾存在时的玩家生命恢复修正值",
            "当护盾冷却完成后，玩家生命恢复会乘以此系数",
            "例如：0.5 表示生命恢复降低到50%"
        ).defineInRange("naturalRecoveryShieldPresentHealthModifier", 0.5, 0.0, 1.0);

        NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER = BUILDER.comment(
            "护盾存在时的护盾自然恢复修正值",
            "当护盾冷却完成后，护盾恢复会乘以此系数",
            "例如：0.75 表示护盾恢复为75%"
        ).defineInRange("naturalRecoveryShieldPresentShieldModifier", 0.75, 0.0, 2.0);

        BUILDER.pop();

        // ===== 14. 效率 =====
        BUILDER.comment("攻击冷却效率系统配置").push("attack_cooldown_efficiency");

        ATTACK_COOLDOWN_EFFICIENCY_ITEMS = BUILDER.comment(
            "效率指定物品",
            "格式：物品ID",
            "示例：gytrinket:efficiency_module"
        ).defineListAllowEmpty("attackCooldownEfficiencyItems",
            java.util.List.of("gytrinket:efficiency_module"),
            s -> true
        );

        BUILDER.pop();

        // ===== 15. 转化 =====
        BUILDER.comment("转化效果配置").push("conversion");

        CONVERSION_ITEMS = BUILDER.comment(
            "转化效果启用物品",
            "格式：物品ID",
            "示例：gytrinket:conversion_module"
        ).defineListAllowEmpty("conversionItems",
            java.util.List.of("gytrinket:transformation_module"),
            s -> true
        );

        CONVERSION_RATIO = BUILDER.comment(
            "转化效果的转化比例",
            "较低资源的此比例会被转化为较高资源",
            "例如：0.3 表示将较低资源的30%转化给较高资源",
            "取值范围：0.0 - 1.0"
        ).defineInRange("conversionRatio", 0.3, 0.0, 1.0);

        BUILDER.pop();

        // ===== 16. 二元协议 =====
        BUILDER.comment("二元协议系统配置").push("binary_protocol");

        BINARY_PROTOCOL_ITEMS = BUILDER.comment(
            "二元协议物品",
            "格式：物品ID",
            "示例：gytrinket:binary_protocol_module"
        ).defineListAllowEmpty("binaryProtocolItems",
            java.util.List.of("gytrinket:binary_protocol_module"),
            s -> true
        );

        BUILDER.pop();

        // ===== 17. 强袭 =====
        BUILDER.comment("强袭系统配置").push("assault");

        ASSAULT_ITEMS = BUILDER.comment(
            "强袭模块物品",
            "格式：物品ID",
            "示例：gytrinket:assault_module"
        ).defineListAllowEmpty("assaultItems",
            java.util.List.of("gytrinket:assault_module"),
            s -> true
        );

        ASSAULT_ATTACK_SPEED_PER_STACK = BUILDER.comment(
            "每层强袭提供的攻击速度独立乘区加成",
            "默认0.1（即10%）",
            "范围：0.01 ~ 1.0"
        ).defineInRange("attackSpeedPerStack", 0.1, 0.01, 1.0);

        ASSAULT_DURATION_TICKS = BUILDER.comment(
            "强袭层数持续时间（刻）",
            "默认40tick（2秒），重复叠加刷新时间",
            "范围：10 ~ 200"
        ).defineInRange("durationTicks", 40, 10, 200);

        ASSAULT_SELF_DAMAGE_PER_STACK = BUILDER.comment(
            "每层强袭对玩家自身造成的伤害",
            "默认0.1",
            "范围：0.01 ~ 10.0"
        ).defineInRange("selfDamagePerStack", 0.05, 0.01, 10.0);

        ASSAULT_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "强袭期间的移动速度独立乘区惩罚",
            "处于强袭时（按住左键期间）施加的减速比例",
            "默认-0.6（即-60%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.6, -1.0, 0.0);

        BUILDER.pop();

        // ===== 18. 充能攻击 =====
        BUILDER.comment("充能攻击系统配置").push("charged_attack");

        CHARGED_ATTACK_ITEMS = BUILDER.comment(
            "充能攻击模块物品",
            "格式：物品ID",
            "示例：gytrinket:charged_attack_module"
        ).defineListAllowEmpty("chargedAttackItems",
            java.util.List.of("gytrinket:charged_attack_module"),
            s -> true
        );

        CHARGED_ATTACK_BASE_CHARGE_RATE = BUILDER.comment(
            "充能攻击基础充能速率（每tick充能值）",
            "实际充能速率 = 基础速率 * 攻击速度加成",
            "默认0.05",
            "范围：0.01 ~ 10.0"
        ).defineInRange("baseChargeRate", 0.05, 0.0, 10.0);

        CHARGED_ATTACK_SPEED_SCALE_FACTOR = BUILDER.comment(
            "攻击速度对充能速率的影响系数",
            "充能速率额外乘区 = 攻击速度 * 此系数",
            "默认0.15",
            "范围：0.0 ~ 1.0"
        ).defineInRange("speedScaleFactor", 1.0, 0.0, 10.0);

        CHARGED_ATTACK_DRAG_COEFFICIENT = BUILDER.comment(
            "充能阻力系数",
            "充能值越大，阻力越大",
            "实际充能增量 = 基础增量 * (1 - 阻力系数 * 充能值 / (充能值 + 阈值))",
            "默认0.8",
            "范围：0.0 ~ 1.0"
        ).defineInRange("dragCoefficient", 1.0, 0.0, 10.0);

        CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR = BUILDER.comment(
            "充能阻力阈值修正系数",
            "动态阈值 = 玩家攻击速度 * 此系数",
            "阈值越大，阻力效果越晚显现，充能前期增长越快",
            "默认1.0",
            "范围：0.1 ~ 10.0"
        ).defineInRange("dragThresholdFactor", 5.0, 0.1, 100.0);

        CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "充能期间的移动速度独立乘区惩罚",
            "处于充能状态时施加的减速比例",
            "默认-0.2（即-20%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.2, -0.99, 0.0);

        BUILDER.pop();

        // ===== 19. 精妙构造 =====
        BUILDER.comment("精妙构造系统配置").push("precision_construct");

        PRECISION_CONSTRUCT_ITEMS = BUILDER.comment(
            "精妙构造前置物品",
            "格式：物品ID",
            "示例：gytrinket:precision_construct_module"
        ).defineListAllowEmpty("precisionConstructItems",
            java.util.List.of("gytrinket:precision_construct_module"),
            s -> true
        );

        PRECISION_CONSTRUCT_BONUS_PER_LEVEL = BUILDER.comment(
            "精妙构造每级提供的构建速度独立乘区加成",
            "0.0025表示每级0.25%",
            "默认0.0025"
        ).defineInRange("precisionConstructBonusPerLevel", 0.0025, 0.0, 1.0);

        BUILDER.pop();

        // ===== 20. 护盾移植 =====
        BUILDER.comment("护盾移植系统配置").push("shield_transfer");

        SHIELD_TRANSFER_ITEMS = BUILDER.comment(
            "护盾移植模块物品",
            "格式：物品ID",
            "示例：gytrinket:shield_transfer_module"
        ).defineListAllowEmpty("shieldTransferItems",
            java.util.List.of("gytrinket:shield_transfer_module"),
            s -> true
        );

        SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY = BUILDER.comment(
            "护盾移植每保护一个实体降低的护盾效果和护盾效果半径百分比",
            "0.03表示每保护一个实体降低3%",
            "降低值之间相乘计算，例如3个实体：0.97*0.97*0.97=0.91，降低0.09",
            "默认0.04"
        ).defineInRange("effectPenaltyPerEntity", 0.04, 0.0, 1.0);

        BUILDER.pop();

        // ===== 21. 追击阵列/突击无人机 =====
        BUILDER.comment("追击阵列/突击无人机配置").push("pursuit_array");

        DRONE_MODULE_ITEMS = BUILDER.comment(
            "基础无人机构建物品",
            "格式：物品ID",
            "示例：gytrinket:drone_module"
        ).defineListAllowEmpty("droneModuleItems",
            java.util.List.of("gytrinket:drone_module"),
            s -> true
        );

        ASSAULT_DRONE_MODULE_ITEMS = BUILDER.comment(
            "突击无人机构建物品",
            "格式：物品ID",
            "示例：gytrinket:assault_drone_module"
        ).defineListAllowEmpty("assaultDroneModuleItems",
            java.util.List.of("gytrinket:assault_drone_module"),
            s -> true
        );

        PURSUIT_ARRAY_REQUIRED_ITEMS = BUILDER.comment(
            "追击阵列所需物品",
            "格式：物品ID",
            "示例：gytrinket:assault_drone_module"
        ).defineListAllowEmpty("pursuitArrayRequiredItems",
            java.util.List.of("gytrinket:assault_drone_module"),
            s -> true
        );

        DRONE_BASE_HEALTH = BUILDER.comment(
            "无人机基础最大生命值"
        ).defineInRange("droneBaseHealth", 5.0, 1.0, 1000.0);

        DRONE_BASE_DAMAGE = BUILDER.comment(
            "无人机基础伤害（每颗子弹）"
        ).defineInRange("droneBaseDamage", 0.3, 0.01, 100.0);

        DRONE_MAX_COUNT = BUILDER.comment(
            "无人机最大数量"
        ).defineInRange("droneMaxCount", 3, 1, 20);

        DRONE_FOLLOW_RANGE = BUILDER.comment(
            "无人机跟随范围（格）"
        ).defineInRange("droneFollowRange", 16.0, 4.0, 64.0);

        ORBIT_ATTACK_INTERVAL = BUILDER.comment(
            "环绕阵列攻击间隔（秒）"
        ).defineInRange("orbitAttackInterval", 0.5, 0.05, 10.0);

        ORBIT_ATTACK_RANGE = BUILDER.comment(
            "环绕阵列攻击范围（格）"
        ).defineInRange("orbitAttackRange", 8.0, 1.0, 64.0);

        PURSUIT_ATTACK_INTERVAL = BUILDER.comment(
            "追击阵列攻击间隔（秒）"
        ).defineInRange("pursuitAttackInterval", 0.33, 0.05, 10.0);

        PURSUIT_ATTACK_RANGE = BUILDER.comment(
            "追击阵列攻击范围（格）"
        ).defineInRange("pursuitAttackRange", 20.0, 1.0, 64.0);

        BUILDER.pop();

        // ===== 21.5 斩杀机制 =====
        BUILDER.comment("斩杀机制配置").push("drone_execute");

        DRONE_EXECUTE_ENABLED = BUILDER.comment(
            "是否启用斩杀机制",
            "启用时：当目标生命值低于本模组非玩家伤害，伤害归属玩家（爆炸伤害源）",
            "禁用时：伤害量不变，但伤害源不归属玩家（不会触发玩家的击杀效果）"
        ).define("droneExecuteEnabled", true);

        BUILDER.pop();

        // ===== 21.6 僚机 =====
        BUILDER.comment("僚机构造体配置").push("wingman");

        WINGMAN_MODULE_ITEMS = BUILDER.comment(
            "僚机构建物品",
            "格式：物品ID",
            "示例：gytrinket:wingman_module"
        ).defineListAllowEmpty("wingmanModuleItems",
            java.util.List.of("gytrinket:wingman_module"),
            s -> true
        );

        WINGMAN_BASE_HEALTH = BUILDER.comment(
            "僚机基础最大生命值"
        ).defineInRange("wingmanBaseHealth", 24.0, 1.0, 1000.0);

        WINGMAN_MAX_COUNT = BUILDER.comment(
            "僚机最大数量"
        ).defineInRange("wingmanMaxCount", 1, 1, 20);

        WINGMAN_ATTACK_INTERVAL = BUILDER.comment(
            "僚机攻击间隔（秒）"
        ).defineInRange("wingmanAttackInterval", 1.0, 0.05, 100.0);

        WINGMAN_ATTACK_RANGE = BUILDER.comment(
            "僚机攻击范围（格）"
        ).defineInRange("wingmanAttackRange", 20.0, 1.0, 64.0);

        WINGMAN_EXPLOSIVE_COUNT = BUILDER.comment(
            "每次攻击发射的爆破弹数量"
        ).defineInRange("wingmanExplosiveCount", 3, 1, 20);

        WINGMAN_EXPLOSIVE_DAMAGE = BUILDER.comment(
            "每颗爆破弹命中伤害"
        ).defineInRange("wingmanExplosiveDamage", 0.5, 0.01, 100.0);

        WINGMAN_EXPLOSION_DAMAGE = BUILDER.comment(
            "爆破弹销毁时爆炸伤害"
        ).defineInRange("wingmanExplosionDamage", 0.5, 0.01, 100.0);

        WINGMAN_EXPLOSION_RADIUS = BUILDER.comment(
            "爆破弹销毁时爆炸半径（格）"
        ).defineInRange("wingmanExplosionRadius", 2.0, 0.1, 10.0);

        BUILDER.pop();

        // ===== 21.7 蜂群 (swarm) =====
        BUILDER.comment("蜂群构造体配置").push("swarm");

        SWARM_MODULE_ITEMS = BUILDER.comment(
            "蜂群构建物品",
            "格式：物品ID",
            "示例：gytrinket:mothership_body"
        ).defineListAllowEmpty("swarmModuleItems",
            java.util.List.of("gytrinket:mothership_body"),
            s -> true
        );

        SWARM_BASE_HEALTH = BUILDER.comment(
            "蜂群基础最大生命值（支持小数）"
        ).defineInRange("swarmBaseHealth", 1.6, 0.1, 1000.0);

        SWARM_BASE_DAMAGE = BUILDER.comment(
            "蜂群基础攻击伤害（电弧单次伤害）"
        ).defineInRange("swarmBaseDamage", 0.08, 0.001, 100.0);

        SWARM_ATTACK_INTERVAL = BUILDER.comment(
            "蜂群攻击间隔（秒），1.5/s 对应约0.667秒"
        ).defineInRange("swarmAttackInterval", 0.667, 0.05, 100.0);

        SWARM_ATTACK_RANGE = BUILDER.comment(
            "蜂群攻击范围（格），电弧生效半径"
        ).defineInRange("swarmAttackRange", 5.0, 0.5, 64.0);

        SWARM_SEARCH_RANGE = BUILDER.comment(
            "蜂群索敌范围（格）"
        ).defineInRange("swarmSearchRange", 20.0, 1.0, 64.0);

        SWARM_MAX_COUNT = BUILDER.comment(
            "蜂群最大数量"
        ).defineInRange("swarmMaxCount", 4, 1, 20);

        SWARM_COUNT_LIMIT = BUILDER.comment(
            "蜂群数量极限值",
            "当蜂群数量超过此值时，",
            "不再增加蜂群数量上限，而是提升每只蜂群的基础属性和易伤值",
            "确保实际效果基本等价于原本没有蜂群极限值的效果",
            "设为0表示不限制"
        ).defineInRange("swarmCountLimit", 35, 0, 100);

        SWARM_MOVE_SPEED = BUILDER.comment(
            "蜂群移动速度（格/刻）"
        ).defineInRange("swarmMoveSpeed", 0.25, 0.001, 10.0);

        SWARM_BUILD_TIME = BUILDER.comment(
            "蜂群构建时间（tick），20tick=1秒"
        ).defineInRange("swarmBuildTime", 20, 1, 6000);

        SWARM_TIER_UPGRADE_CHANCE_STANDARD = BUILDER.comment(
            "蜂群构建时升阶为标准的概率（0~1）"
        ).defineInRange("swarmTierUpgradeChanceStandard", 0.05, 0.0, 1.0);

        SWARM_TIER_UPGRADE_CHANCE_ADVANCED = BUILDER.comment(
            "蜂群构建时升阶为高阶的概率（0~1）"
        ).defineInRange("swarmTierUpgradeChanceAdvanced", 0.01, 0.0, 1.0);

        SWARM_VULNERABILITY_VALUE = BUILDER.comment(
            "蜂群攻击施加的易伤值（0.0025=0.25%），可叠加"
        ).defineInRange("swarmVulnerabilityValue", 0.0025, 0.0, 10.0);

        SWARM_SHIELD_REPAIR_MULTIPLIER = BUILDER.comment(
            "蜂群修复模式伤害->护盾恢复转化倍率（5=5倍伤害值）"
        ).defineInRange("swarmShieldRepairMultiplier", 3.0, 0.0, 100.0);

        BUILDER.pop();

        // ===== 22. 宽限协议 =====
        BUILDER.comment("濒死保护系统配置").push("near_death_protection");

        NEAR_DEATH_PROTECTION_ITEMS = BUILDER.comment(
            "宽限协议前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活濒死保护",
            "示例：gytrinket:near_death_protection_module"
        ).defineListAllowEmpty("nearDeathProtectionItems",
            java.util.List.of("gytrinket:wide_protocol_module"),
            s -> true
        );

        NEAR_DEATH_PROTECTION_COOLDOWN = BUILDER.comment(
            "宽限协议冷却时间（tick）",
            "触发濒死保护后的冷却时间，期间无法再次触发",
            "默认300tick（15秒）"
        ).defineInRange("nearDeathProtectionCooldown", 300, 20, 6000);

        NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION = BUILDER.comment(
            "宽限协议无敌持续时间（tick）",
            "触发濒死保护后的无敌持续时间",
            "默认20tick（1秒）"
        ).defineInRange("nearDeathProtectionInvincibleDuration", 20, 1, 200);

        BUILDER.pop();

        // ===== 23. 高等工程 =====
        BUILDER.comment("高等工程系统配置").push("advanced_engineering");

        ADVANCED_ENGINEERING_ITEMS = BUILDER.comment(
            "高等工程前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活高等工程加成",
            "示例：gytrinket:precision_construct_module"
        ).defineListAllowEmpty("advancedEngineeringItems",
            java.util.List.of("gytrinket:advanced_engineering_module"),
            s -> true
        );

        ADVANCED_ENGINEERING_BONUS_PER_LEVEL = BUILDER.comment(
            "高等工程每级提供的无人机生命和伤害独立乘区加成",
            "0.01表示每级1%",
            "默认0.01"
        ).defineInRange("advancedEngineeringBonusPerLevel", 0.01, 0.0, 1.0);

        BUILDER.pop();

        // ===== 24. 最后指令 =====
        BUILDER.comment("濒死自爆系统配置").push("near_death_explosion");

        NEAR_DEATH_EXPLOSION_ITEMS = BUILDER.comment(
            "最后指令前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活濒死自爆",
            "示例：gytrinket:near_death_explosion_module"
        ).defineListAllowEmpty("nearDeathExplosionItems",
            java.util.List.of("gytrinket:last_order_module"),
            s -> true
        );

        NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION = BUILDER.comment(
            "最后指令无敌持续时间（tick）",
            "触发濒死自爆后的无敌持续时间（也是自爆飞行时间）",
            "默认100tick（5秒）"
        ).defineInRange("nearDeathExplosionInvincibleDuration", 100, 20, 6000);

        NEAR_DEATH_EXPLOSION_COEFFICIENT = BUILDER.comment(
            "最后指令爆炸系数",
            "爆炸伤害 = 无人机最大生命值 × 当前速度 × 爆炸系数"
        ).defineInRange("nearDeathExplosionCoefficient", 2.0, 0.1, 100.0);

        NEAR_DEATH_EXPLOSION_RADIUS = BUILDER.comment(
            "最后指令爆炸半径（格）"
        ).defineInRange("nearDeathExplosionRadius", 3.0, 0.5, 20.0);

        NEAR_DEATH_EXPLOSION_SEARCH_RADIUS = BUILDER.comment(
            "最后指令搜索危险物半径（格）"
        ).defineInRange("nearDeathExplosionSearchRadius", 50.0, 5.0, 100.0);

        NEAR_DEATH_EXPLOSION_INITIAL_SPEED = BUILDER.comment(
            "最后指令初始速度（格/tick）"
        ).defineInRange("nearDeathExplosionInitialSpeed", 0.15, 0.01, 2.0);

        NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION = BUILDER.comment(
            "最后指令速度加速度（格/tick²）",
            "每tick增加的速度"
        ).defineInRange("nearDeathExplosionSpeedAcceleration", 0.05, 0.001, 1.0);

        BUILDER.pop();

        // ===== 24.5 自毁装置 =====
        BUILDER.comment("自毁装置系统配置").push("self_destruct");

        SELF_DESTRUCT_ITEMS = BUILDER.comment(
            "自毁装置前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活自毁装置",
            "示例：gytrinket:self_destruct_module"
        ).defineListAllowEmpty("selfDestructItems",
            java.util.List.of("gytrinket:self_destruct_module"),
            s -> true
        );

        SELF_DESTRUCT_BASE_DAMAGE = BUILDER.comment(
            "自毁装置基础爆炸伤害",
            "构造体被摧毁时的基础爆炸伤害",
            "默认1.0"
        ).defineInRange("selfDestructBaseDamage", 1.0, 0.0, 1000.0);

        SELF_DESTRUCT_BASE_RADIUS = BUILDER.comment(
            "自毁装置基础爆炸半径（格）",
            "构造体被摧毁时的基础爆炸半径",
            "默认1.0"
        ).defineInRange("selfDestructBaseRadius", 1.0, 0.0, 100.0);

        SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH = BUILDER.comment(
            "自毁装置每点最大生命值增加的爆炸伤害",
            "默认1.0"
        ).defineInRange("selfDestructDamagePerMaxHealth", 1.0, 0.0, 100.0);

        SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH = BUILDER.comment(
            "自毁装置每点最大生命值增加的爆炸半径（格）",
            "默认0.3"
        ).defineInRange("selfDestructRadiusPerMaxHealth", 0.3, 0.0, 10.0);

        BUILDER.pop();

        // ===== 24.6 督战者 =====
        BUILDER.comment("督战者系统配置").push("taskmaster");

        TASKMASTER_ITEMS = BUILDER.comment(
            "督战者前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活督战者",
            "示例：gytrinket:taskmaster_module"
        ).defineListAllowEmpty("taskmasterItems",
            java.util.List.of("gytrinket:taskmaster_module"),
            s -> true
        );

        BUILDER.pop();

        // ===== 25. 列队阵列 =====
        BUILDER.comment("列队阵列配置").push("formation_array");

        FORMATION_ARRAY_REQUIRED_ITEMS = BUILDER.comment(
            "列队阵列所需物品",
            "格式：物品ID，玩家光点核心中需包含所有指定物品才能切换到列队阵列",
            "示例：gytrinket:line_formation_module"
        ).defineListAllowEmpty("formationArrayRequiredItems",
            java.util.List.of("gytrinket:line_formation_module"),
            s -> true
        );

        FORMATION_ATTACK_INTERVAL = BUILDER.comment(
            "列队阵列攻击间隔（秒）"
        ).defineInRange("formationAttackInterval", 1.0, 0.05, 10.0);

        FORMATION_ATTACK_RANGE = BUILDER.comment(
            "列队阵列攻击范围（格）"
        ).defineInRange("formationAttackRange", 15.0, 1.0, 64.0);

        FORMATION_ATTACK_PASS_DELAY = BUILDER.comment(
            "列队阵列攻击传递延迟（tick）"
        ).defineInRange("formationAttackPassDelay", 3, 1, 60);

        BUILDER.pop();

        // ===== 26. 指挥官 =====
        BUILDER.comment("指挥官系统配置").push("commander");

        COMMANDER_REQUIRED_ITEMS = BUILDER.comment(
            "指挥官前置物品",
            "格式：物品ID，玩家光点核心中需包含至少一个指定物品才能激活指挥官系统",
            "示例：gytrinket:commander_module"
        ).defineListAllowEmpty("commanderRequiredItems",
            java.util.List.of("gytrinket:wing_commander_module"),
            s -> true
        );

        COMMANDER_MAX_COUNT = BUILDER.comment(
            "指挥官最大数量",
            "同一玩家的无人机中最多同时存在的指挥官数量"
        ).defineInRange("commanderMaxCount", 2, 1, 10);

        COMMANDER_APPOINT_DELAY = BUILDER.comment(
            "指挥官任命延迟（tick）",
            "指挥官数量未达上限时，等待指定tick后自动任命状态最好的无人机"
        ).defineInRange("commanderAppointDelay", 40, 10, 200);

        COMMANDER_VULNERABILITY = BUILDER.comment(
            "指挥官攻击易伤值",
            "指挥官无人机每次攻击命中时施加的易伤值（可叠加）",
            "0.01 = 1%易伤"
        ).defineInRange("commanderVulnerability", 0.01, 0.001, 1.0);

        BUILDER.pop();

        // ===== 27. 守卫阵列/防御无人机 =====
        BUILDER.comment("守卫阵列/防御无人机配置").push("guard_array");

        DEFENSE_DRONE_MODULE_ITEMS = BUILDER.comment(
            "防御无人机构建物品",
            "格式：物品ID",
            "示例：minecraft:defense_drone_module"
        ).defineListAllowEmpty("defenseDroneModuleItems",
            java.util.List.of("gytrinket:defense_drone_module"),
            s -> true
        );

        GUARD_ARRAY_REQUIRED_ITEMS = BUILDER.comment(
            "守卫阵列所需物品",
            "格式：物品ID，玩家光点核心中需包含所有指定物品才能切换到守卫阵列",
            "示例：gytrinket:defense_drone_module"
        ).defineListAllowEmpty("guardArrayRequiredItems",
            java.util.List.of("gytrinket:defense_drone_module"),
            s -> true
        );

        GUARD_ATTACK_INTERVAL = BUILDER.comment(
            "守卫阵列攻击间隔（秒）"
        ).defineInRange("guardAttackInterval", 0.5, 0.05, 10.0);

        GUARD_ATTACK_RANGE = BUILDER.comment(
            "守卫阵列攻击范围（格）"
        ).defineInRange("guardAttackRange", 8.0, 1.0, 64.0);

        BUILDER.pop();

        // ===== 28. 弧形屏障 =====
        BUILDER.comment("弧形屏障系统配置").push("arc_barrier");

        ARC_BARRIER_ITEMS = BUILDER.comment(
            "弧形屏障启用物品",
            "放入光点核心后，当防御无人机在玩家与伤害源之间时，伤害会被无人机拦截",
            "格式：物品ID",
            "示例：gytrinket:arc_barrier_module"
        ).defineListAllowEmpty("arcBarrierItems",
            java.util.List.of("gytrinket:arc_barrier_module"),
            s -> true
        );

        ARC_BARRIER_POSITION_DEVIATION_THRESHOLD = BUILDER.comment(
            "弧形屏障位置偏差阈值（格）",
            "用于判断防御无人机是否在玩家与伤害源之间",
            "无人机到玩家与伤害源连线的垂直距离小于此值时视为在中间",
            "默认1.0格"
        ).defineInRange("positionDeviationThreshold", 1.0, 0.5, 10.0);

        BUILDER.pop();

        // ===== 29. 反制脉冲 =====
        BUILDER.comment("反制脉冲系统配置").push("counter_pulse");

        COUNTER_PULSE_ITEMS = BUILDER.comment(
            "反制脉冲启用物品",
            "放入光点核心后，防御无人机获得反制脉冲效果：",
            "1. 防御无人机每3秒触发一次反制脉冲",
            "2. 对自身半径内的敌人造成爆炸伤害",
            "3. 不触发时持续充能，提高爆炸半径和伤害",
            "4. 充能收益边际递减",
            "5. 受到伤害时立即触发反制脉冲，不受冷却限制",
            "需要同时拥有防御无人机模块",
            "格式：物品ID",
            "示例：gytrinket:counter_pulse_module"
        ).defineListAllowEmpty("counterPulseItems",
            java.util.List.of("gytrinket:counter_pulse_module"),
            s -> true
        );

        COUNTER_PULSE_COOLDOWN = BUILDER.comment(
            "反制脉冲冷却时间（tick）",
            "默认60tick（3秒）"
        ).defineInRange("cooldown", 60, 20, 600);

        COUNTER_PULSE_BASE_EXPLOSION_RADIUS = BUILDER.comment(
            "反制脉冲基础爆炸半径（格）",
            "默认1.3格"
        ).defineInRange("baseExplosionRadius", 1.3, 0.5, 10.0);

        COUNTER_PULSE_BASE_EXPLOSION_DAMAGE = BUILDER.comment(
            "反制脉冲基础爆炸伤害",
            "默认1.0"
        ).defineInRange("baseExplosionDamage", 1.0, 0.1, 100.0);

        COUNTER_PULSE_CHARGE_INTERVAL = BUILDER.comment(
            "反制脉冲充能间隔（tick）",
            "每多少tick充能1层",
            "默认3tick"
        ).defineInRange("chargeInterval", 3, 1, 100);

        COUNTER_PULSE_MAX_CHARGE_LEVEL = BUILDER.comment(
            "反制脉冲最大充能层数",
            "默认1000层"
        ).defineInRange("maxChargeLevel", 1000, 10, 10000);

        BUILDER.pop();

        // ===== 30. 重塑 =====
        BUILDER.comment("重塑系统配置").push("reshaping");

        RESHAPING_ITEMS = BUILDER.comment(
            "重塑启用物品",
            "放入光点核心后，防御无人机获得重塑效果：",
            "1. 防御无人机每秒恢复最大生命值的百分比",
            "2. 防御无人机死亡时生成装甲碎片",
            "3. 玩家吸收装甲碎片后获得伤害减免",
            "需要同时拥有防御无人机模块",
            "格式：物品ID",
            "示例：gytrinket:reshaping_module"
        ).defineListAllowEmpty("reshapingItems",
            java.util.List.of("gytrinket:reshaping_module"),
            s -> true
        );

        RESHAPING_HEAL_RATE = BUILDER.comment(
            "重塑防御无人机生命恢复速率",
            "每秒恢复最大生命值的百分比",
            "默认0.02（2%/秒）"
        ).defineInRange("healRate", 0.02, 0.0, 1.0);

        RESHAPING_BASE_DAMAGE_REDUCTION = BUILDER.comment(
            "重塑装甲碎片基础伤害减免（%）",
            "玩家吸收装甲碎片后获得的基础伤害减免百分比",
            "实际减免 = 基础减免 × (1 + 伤害加成/100)",
            "默认15.0%"
        ).defineInRange("baseDamageReduction", 15.0, 0.0, 100.0);

        RESHAPING_DAMAGE_REDUCTION_DURATION = BUILDER.comment(
            "重塑伤害减免持续时间（tick）",
            "玩家吸收装甲碎片后伤害减免的持续时间",
            "默认100tick（5秒）"
        ).defineInRange("damageReductionDuration", 100, 20, 6000);

        BUILDER.pop();

        // ===== 31. 充能护盾 =====
        BUILDER.comment("充能护盾系统配置").push("charged_shield");

        CHARGED_SHIELD_ITEMS = BUILDER.comment(
            "充能护盾模块物品",
            "放入光点核心后，玩家充能时获得动态护盾效果和护盾效果半径加成",
            "该物品依赖充能攻击模块",
            "格式：物品ID",
            "示例：gytrinket:charged_shield_module"
        ).defineListAllowEmpty("chargedShieldItems",
            java.util.List.of("gytrinket:charged_shield_module"),
            s -> true
        );

        CHARGED_SHIELD_CHARGE_RATIO = BUILDER.comment(
            "充能值转化为护盾加成的比率",
            "动态属性值 = 累计充能值 * 此比率",
            "默认0.1（即10%）",
            "范围：0.01 ~ 1.0"
        ).defineInRange("chargeRatio", 0.1, 0.01, 1.0);

        CHARGED_SHIELD_MAX_BONUS = BUILDER.comment(
            "动态属性值上限",
            "动态属性值不超过此值（独立乘区值，0.8即80%加成）",
            "默认0.8",
            "范围：0.1 ~ 5.0"
        ).defineInRange("maxBonus", 0.8, 0.1, 5.0);

        CHARGED_SHIELD_DECAY_RATE = BUILDER.comment(
            "充能护盾消退速率（每tick消退的独立乘区值）",
            "停止充能后，动态属性值按此速率线性消退",
            "默认0.05（即每tick消退5%，约16tick完全消退）",
            "范围：0.005 ~ 0.5"
        ).defineInRange("decayRate", 0.08, 0.005, 0.5);

        CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "充能护盾模块附加的移动速度独立乘区惩罚",
            "充能期间若拥有充能护盾模块，额外施加的减速比例",
            "默认-0.15（即-15%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.15, -0.99, 0.0);

        BUILDER.pop();

        // ===== 35. 积怨 =====
        BUILDER.comment("积怨系统配置").push("grudge");

        GRUDGE_ITEMS = BUILDER.comment(
            "积怨模块物品",
            "放入光点核心后，玩家充能期间损失生命或护盾值时，转化为临时充能速率",
            "该物品依赖充能攻击模块",
            "格式：物品ID",
            "示例：gytrinket:grudge_module"
        ).defineListAllowEmpty("grudgeItems",
            java.util.List.of("gytrinket:grudge_module"),
            s -> true);

        GRUDGE_CONVERSION_RATIO = BUILDER.comment(
            "积怨转化比率",
            "每点损失的生命或护盾值转化为多少充能速率",
            "默认0.05（即1:0.05）",
            "范围：0.001 ~ 1.0"
        ).defineInRange("conversionRatio", 0.2, 0.001, 1.0);

        GRUDGE_FADE_BASE = BUILDER.comment(
            "积怨消退基础速率",
            "消退时每tick固定减少的充能速率",
            "默认1.0",
            "范围：0.0 ~ 10.0"
        ).defineInRange("fadeBase", 0.00001, 0.0, 10.0);

        GRUDGE_FADE_PERCENT = BUILDER.comment(
            "积怨消退百分比",
            "消退时每tick额外减少当前值的百分比",
            "消退速度 = fadeBase + 当前值 * fadePercent",
            "默认0.08（即8%）",
            "范围：0.0 ~ 1.0"
        ).defineInRange("fadePercent", 0.02, 0.0, 1.0);

        GRUDGE_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "积怨模块附加的移动速度独立乘区惩罚",
            "充能期间若拥有积怨模块，额外施加的减速比例",
            "默认-0.15（即-15%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.15, -0.99, 0.0);

        BUILDER.pop();

        // ===== 32. 升级系统 =====
        BUILDER.comment("升级系统配置").push("upgrade_system");

        UPGRADE_SYSTEM_ENABLED = BUILDER.comment("是否启用升级系统").define("enabled", true);

        UPGRADE_PATHS = BUILDER.comment(
            "升级路径定义,需要通过模组界面使用",
            "格式：基础物品ID.升级物品ID",
            "每条代表一个升级步骤，若有多级升级则分别注册",
            "同一基础物品可以注册多个升级目标，形成升级分支",
            "示例：gytrinket:shield_gy.gytrinket:shield_gy1"
        ).defineListAllowEmpty("upgradePaths",
            java.util.List.of(
                "gytrinket:shield_gy.gytrinket:shield_gy1",
                "gytrinket:shield_gy1.gytrinket:shield_gy2",
                "gytrinket:shield_gy2.gytrinket:shield_gy3",
                "gytrinket:shield_gy.gytrinket:shield_aura_ring",
                "gytrinket:shield_gy.gytrinket:shield_siphon",
                "gytrinket:shield_gy.gytrinket:shield_reflect",
                "gytrinket:shield_gy.gytrinket:shield_amplifier",
                "gytrinket:shield_gy.gytrinket:shield_warp",
                "gytrinket:shield_aura_ring.gytrinket:shield_aura_ring1",
                "gytrinket:shield_aura_ring1.gytrinket:shield_aura_ring2",
                "gytrinket:shield_aura_ring2.gytrinket:shield_aura_ring3",
                "gytrinket:shield_siphon.gytrinket:shield_siphon1",
                "gytrinket:shield_siphon1.gytrinket:shield_siphon2",
                "gytrinket:shield_siphon2.gytrinket:shield_siphon3",
                "gytrinket:shield_reflect.gytrinket:shield_reflect1",
                "gytrinket:shield_reflect1.gytrinket:shield_reflect2",
                "gytrinket:shield_reflect2.gytrinket:shield_reflect3",
                "gytrinket:shield_amplifier.gytrinket:shield_amplifier1",
                "gytrinket:shield_amplifier1.gytrinket:shield_amplifier2",
                "gytrinket:shield_amplifier2.gytrinket:shield_amplifier3",
                "gytrinket:shield_warp.gytrinket:shield_warp1",
                "gytrinket:shield_warp1.gytrinket:shield_warp2",
                "gytrinket:shield_warp2.gytrinket:shield_warp3"
            ),
            s -> true
        );

        BUILDER.pop();

        // ===== 33. 快速装备 =====
        BUILDER.comment("快速装备配置").push("quick_equip");

        QUICK_EQUIP_UPGRADE_POINTS_COST = BUILDER.comment(
            "快速装备每次消耗的升级点数量",
            "每次触发快速装备时消耗的升级点数量",
            "默认1"
        ).defineInRange("upgradePointsCost", 1, 0, 100);

        BUILDER.pop();

        // ===== 34. 其他通用设置 =====
        BUILDER.comment("困难模式配置").push("hardcore_mode");

        HARDCORE_MODE_ENABLED = BUILDER.comment(
            "困难模式",
            "启用后，当玩家死亡时，将移除玩家光点核心内所有物品",
            "默认不启用"
        ).define("hardcoreMode", false);

        BUILDER.pop();

        BUILDER.comment("护盾格挡配置").push("shield_block");

        SHIELD_BLOCK_INVULNERABLE_TICKS = BUILDER.comment(
            "护盾格挡时施加的无敌状态持续时间（刻）",
            "当护盾完全吸收伤害时，被攻击者获得短暂无敌帧",
            "默认10刻（0.5秒）"
        ).defineInRange("blockInvulnerableTicks", 10, 0, 100);

        BUILDER.pop();

        BUILDER.comment("点燃系统配置").push("ignite_system");

        IGNITE_DEFAULT_DAMAGE = BUILDER.comment("点燃默认伤害").defineInRange("igniteDefaultDamage", 1.2, 0.0, 100.0);
        IGNITE_DEFAULT_DURATION = BUILDER.comment("点燃默认持续时间（秒）").defineInRange("igniteDefaultDuration", 3, 1, 600);

        BUILDER.pop();

        BUILDER.comment("自然恢复系统配置").push("natural_recovery");

        NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED = BUILDER.comment(
            "是否启用玩家基础生命自然恢复",
            "启用：无论恢复效率属性值多少，始终按 naturalRecoveryPlayerHealth 的值恢复",
            "不启用：仅当恢复效率属性 > 1 时才恢复，恢复量为 naturalRecoveryPlayerHealth 的值；否则不恢复"
        ).define("playerHealthEnabled", true);
        NATURAL_RECOVERY_PLAYER_HEALTH = BUILDER.comment("玩家基础生命恢复速度（%/秒）").defineInRange("naturalRecoveryPlayerHealth", 0.02, 0.0, 10.0);
        NATURAL_RECOVERY_SHIELD = BUILDER.comment("护盾基础恢复速度（%/秒，0为禁用）").defineInRange("naturalRecoveryShield", 0.0, 0.0, 10.0);
        NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY = BUILDER.comment("攻击冷却期间恢复惩罚系数（0-1，越低恢复越少）").defineInRange("naturalRecoveryAttackCooldownPenalty", 0.8, 0.0, 1.0);

        BUILDER.pop();

        // ===== 敌对目标系统 =====
        BUILDER.comment("敌对目标系统配置").push("hostile_target");

        HOSTILE_TARGET_MARK_DURATION = BUILDER.comment(
            "玩家攻击标记持续时间（tick）",
            "被玩家攻击过的实体会被标记为威胁，持续此时间后标记失效",
            "20 tick = 1秒，默认100 tick = 5秒"
        ).defineInRange("markDuration", 100, 1, 10000);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static final Map<String, Boolean> SHIELD_TYPE_COMPATIBILITY = new HashMap<>();
    private static final Map<Item, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final Set<Item> BODY_ITEM_SET = new HashSet<>();
    private static final Set<Item> DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ASSAULT_DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> DEFENSE_DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> SWARM_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADAPTIVE_ARMOR_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET = new HashSet<>();
    private static final Set<Item> BARRIER_ITEM_SET = new HashSet<>();
    private static final Set<Item> EXPLOSIVE_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> REFLECT_DAMAGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ELECTRIC_DISCHARGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> SHIELD_TRANSFER_ITEM_SET = new HashSet<>();
    private static final Set<Item> ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET = new HashSet<>();
    private static final Set<Item> SHIELD_NATURAL_RECOVERY_ITEM_SET = new HashSet<>();
    private static final Set<Item> BINARY_PROTOCOL_ITEM_SET = new HashSet<>();
    private static final Set<Item> WEAPONIZED_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> CONVERSION_ITEM_SET = new HashSet<>();
    private static final Set<String> DANGEROUS_ENTITY_SET = new HashSet<>();
    private static final Set<Item> NEAR_DEATH_PROTECTION_ITEM_SET = new HashSet<>();
    private static final Set<Item> NEAR_DEATH_EXPLOSION_ITEM_SET = new HashSet<>();
    private static final Set<Item> SELF_DESTRUCT_ITEM_SET = new HashSet<>();
    private static final Set<Item> TASKMASTER_ITEM_SET = new HashSet<>();
    private static final Set<Item> COMMANDER_ITEM_SET = new HashSet<>();
    private static final Set<Item> ARC_BARRIER_ITEM_SET = new HashSet<>();
    private static final Set<Item> RESHAPING_ITEM_SET = new HashSet<>();
    private static final Set<Item> COUNTER_PULSE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ASSAULT_ITEM_SET = new HashSet<>();
    private static final Set<Item> CHARGED_ATTACK_ITEM_SET = new HashSet<>();
    private static final Set<Item> CHARGED_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> GRUDGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> PRECISION_CONSTRUCT_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADVANCED_ENGINEERING_ITEM_SET = new HashSet<>();
    private static final Set<Item> PURSUIT_ARRAY_ITEM_SET = new HashSet<>();
    private static final Set<Item> FORMATION_ARRAY_ITEM_SET = new HashSet<>();
    private static final Set<Item> GUARD_ARRAY_ITEM_SET = new HashSet<>();

    public static List<String> getItemShieldTypes(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            return Collections.emptyList();
        }
        return ITEM_SHIELD_TYPES.getOrDefault(item, Collections.emptyList());
    }

    public static boolean isShieldTypeCompatible(String typeName) {
        return SHIELD_TYPE_COMPATIBILITY.getOrDefault(typeName, true);
    }

    public static boolean isBodyItem(Item item) {
        return BODY_ITEM_SET.contains(item);
    }

    public static void saveItemAttributesConfig() {
        java.util.Set<String> registeredItems = AttributeManager.getAllRegisteredItemAttributes();
        java.util.List<String> configList = new java.util.ArrayList<>();
        for (String itemId : registeredItems) {
            ItemAttributeConfig config = AttributeManager.getItemAttributes(itemId);
            if (config == null || config.getAttributes().isEmpty()) continue;
            StringBuilder sb = new StringBuilder(itemId);
            for (var entry : config.getAttributes().entrySet()) {
                sb.append("|").append(entry.getKey()).append("=").append(entry.getValue());
            }
            configList.add(sb.toString());
        }
        ITEM_ATTRIBUTES_CONFIG.set(configList);
        SPEC.save();
        gytrinket.LOGGER.info("物品属性配置已保存，共 {} 个物品", configList.size());
    }

    public static void loadItemAttributes() {
        List<? extends String> itemAttrsList = ITEM_ATTRIBUTES_CONFIG.get();
        for (String itemConfig : itemAttrsList) {
            if (!itemConfig.trim().isEmpty()) {
                String[] itemParts = itemConfig.trim().split("\\|");
                if (itemParts.length >= 2) {
                    String itemId = itemParts[0].trim();
                    Map<String, Double> attrs = new HashMap<>();
                    for (int i = 1; i < itemParts.length; i++) {
                        String[] attrParts = itemParts[i].trim().split("=");
                        if (attrParts.length == 2) {
                            String attrName = attrParts[0].trim();
                            try {
                                double value = Double.parseDouble(attrParts[1].trim());
                                attrs.put(attrName, value);
                            } catch (NumberFormatException e) {
                                gytrinket.LOGGER.warn("无效的属性值：{} for {}", attrParts[1], itemId);
                            }
                        }
                    }
                    if (!attrs.isEmpty()) {
                        AttributeManager.registerItemAttributes(itemId, attrs);
                        gytrinket.LOGGER.info("注册物品属性: {} -> {}", itemId, attrs);
                    }
                }
            }
        }
    }

    public static void resetItemAttributesConfig() {
        ITEM_ATTRIBUTES_CONFIG.set(ITEM_ATTRIBUTES_CONFIG.getDefault());
        SPEC.save();
        loadItemAttributes();
        gytrinket.LOGGER.info("物品属性配置已重置为默认值");
    }

    private static void loadItemSet(Set<Item> targetSet, List<? extends String> configList, String logLabel) {
        targetSet.clear();
        for (String itemId : configList) {
            String trimmed = itemId.trim();
            if (!trimmed.isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(trimmed));
                if (item != null && item != Items.AIR) {
                    targetSet.add(item);
                    gytrinket.LOGGER.info("注册{}物品: {}", logLabel, trimmed);
                }
            }
        }
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!event.getConfig().getSpec().equals(SPEC)) {
            return;
        }
        if (initialized) {
            gytrinket.LOGGER.info("配置已初始化，跳过重复加载");
            return;
        }
        initialized = true;

        String attributesStr = ATTRIBUTES_CONFIG.get();
        if (!attributesStr.isEmpty()) {
            String[] attributeDefs = attributesStr.split(",");
            for (String attrDef : attributeDefs) {
                String[] parts = attrDef.trim().split(":");
                if (parts.length >= 2) {
                    String attrName = parts[0].trim();
                    try {
                        AttributeType attrType = AttributeType.valueOf(parts[1].trim());
                        String group = (parts.length >= 3) ? parts[2].trim() : null;
                        AttributeManager.registerAttribute(attrName, attrType, group);
                    } catch (IllegalArgumentException e) {
                        gytrinket.LOGGER.warn("无效的属性类型：{}，请检查配置", parts[1].trim());
                    }
                }
            }
        }

        loadItemAttributes();

        SHIELD_TYPE_COMPATIBILITY.clear();
        String shieldTypesStr = SHIELD_TYPES_CONFIG.get();
        if (!shieldTypesStr.isEmpty()) {
            String[] typeDefs = shieldTypesStr.split(";");
            for (String typeDef : typeDefs) {
                String[] parts = typeDef.trim().split("=");
                if (parts.length == 2) {
                    String typeName = parts[0].trim();
                    boolean compatible = Boolean.parseBoolean(parts[1].trim());
                    SHIELD_TYPE_COMPATIBILITY.put(typeName, compatible);
                    gytrinket.LOGGER.info("注册护盾类型: {} (兼容={})", typeName, compatible);
                }
            }
        }

        ITEM_SHIELD_TYPES.clear();
        List<? extends String> itemShieldTypesList = ITEM_SHIELD_TYPES_CONFIG.get();
        for (String itemConfig : itemShieldTypesList) {
            if (!itemConfig.trim().isEmpty()) {
                String[] itemParts = itemConfig.trim().split("\\|");
                if (itemParts.length >= 2) {
                    String itemId = itemParts[0].trim();
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    if (item != null && item != Items.AIR) {
                        String[] typeNames = itemParts[1].trim().split(",");
                        List<String> types = new ArrayList<>();
                        for (String typeName : typeNames) {
                            types.add(typeName.trim());
                        }
                        ITEM_SHIELD_TYPES.put(item, types);
                        gytrinket.LOGGER.info("注册物品护盾类型: {} -> {}", itemId, types);
                    }
                }
            }
        }

        ShieldTypeManager.init();

        // 加载机身物品配置
        loadItemSet(BODY_ITEM_SET, BODY_ITEMS_CONFIG.get(), "机身物品");

        DisableSystem.loadConfig();

        loadItemSet(ADAPTIVE_ARMOR_ITEM_SET, ADAPTIVE_ARMOR_ITEMS.get(), "适应性装甲启用");
        loadItemSet(ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET, ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEMS.get(), "适应性装甲护盾效果");
        loadItemSet(ELECTRIC_DISCHARGE_ITEM_SET, ELECTRIC_DISCHARGE_ITEMS.get(), "闪电释放模块");
        loadItemSet(ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET, ATTACK_COOLDOWN_EFFICIENCY_ITEMS.get(), "攻击冷却效率");
        loadItemSet(BARRIER_ITEM_SET, BARRIER_ITEMS.get(), "屏障处理器启用");
        loadItemSet(SHIELD_NATURAL_RECOVERY_ITEM_SET, SHIELD_NATURAL_RECOVERY_ITEMS.get(), "护盾自然恢复");
        loadItemSet(REFLECT_DAMAGE_ITEM_SET, REFLECT_DAMAGE_ITEMS.get(), "反射护盾伤害处理器启用");
        loadItemSet(EXPLOSIVE_SHIELD_ITEM_SET, EXPLOSIVE_SHIELD_ITEMS.get(), "易爆护盾效果启用");
        loadItemSet(SHIELD_TRANSFER_ITEM_SET, SHIELD_TRANSFER_ITEMS.get(), "护盾移植模块");
        loadItemSet(BINARY_PROTOCOL_ITEM_SET, BINARY_PROTOCOL_ITEMS.get(), "二元协议");
        loadItemSet(WEAPONIZED_SHIELD_ITEM_SET, WEAPONIZED_SHIELD_ITEMS.get(), "武器化护盾");
        loadItemSet(NEAR_DEATH_PROTECTION_ITEM_SET, NEAR_DEATH_PROTECTION_ITEMS.get(), "濒死保护前置");
        loadItemSet(NEAR_DEATH_EXPLOSION_ITEM_SET, NEAR_DEATH_EXPLOSION_ITEMS.get(), "濒死自爆前置");
        loadItemSet(SELF_DESTRUCT_ITEM_SET, SELF_DESTRUCT_ITEMS.get(), "自毁装置前置");
        loadItemSet(TASKMASTER_ITEM_SET, TASKMASTER_ITEMS.get(), "督战者前置");
        loadItemSet(DRONE_MODULE_ITEM_SET, DRONE_MODULE_ITEMS.get(), "基础无人机构建");
        loadItemSet(ASSAULT_DRONE_MODULE_ITEM_SET, ASSAULT_DRONE_MODULE_ITEMS.get(), "突击无人机构建");
        loadItemSet(COMMANDER_ITEM_SET, COMMANDER_REQUIRED_ITEMS.get(), "指挥官前置");
        loadItemSet(DEFENSE_DRONE_MODULE_ITEM_SET, DEFENSE_DRONE_MODULE_ITEMS.get(), "防御无人机构建");
        loadItemSet(WINGMAN_MODULE_ITEM_SET, WINGMAN_MODULE_ITEMS.get(), "僚机构建");
        loadItemSet(SWARM_MODULE_ITEM_SET, SWARM_MODULE_ITEMS.get(), "蜂群构建");
        loadItemSet(ARC_BARRIER_ITEM_SET, ARC_BARRIER_ITEMS.get(), "弧形屏障启用");
        loadItemSet(RESHAPING_ITEM_SET, RESHAPING_ITEMS.get(), "重塑启用");
        loadItemSet(COUNTER_PULSE_ITEM_SET, COUNTER_PULSE_ITEMS.get(), "反制脉冲启用");
        loadItemSet(ASSAULT_ITEM_SET, ASSAULT_ITEMS.get(), "强袭模块");
        loadItemSet(CHARGED_ATTACK_ITEM_SET, CHARGED_ATTACK_ITEMS.get(), "充能攻击模块");
        loadItemSet(CHARGED_SHIELD_ITEM_SET, CHARGED_SHIELD_ITEMS.get(), "充能护盾模块");
        loadItemSet(GRUDGE_ITEM_SET, GRUDGE_ITEMS.get(), "积怨模块");
        loadItemSet(PRECISION_CONSTRUCT_ITEM_SET, PRECISION_CONSTRUCT_ITEMS.get(), "精妙构造前置");
        loadItemSet(ADVANCED_ENGINEERING_ITEM_SET, ADVANCED_ENGINEERING_ITEMS.get(), "高等工程前置");
        loadItemSet(PURSUIT_ARRAY_ITEM_SET, PURSUIT_ARRAY_REQUIRED_ITEMS.get(), "追击阵列前置");
        loadItemSet(FORMATION_ARRAY_ITEM_SET, FORMATION_ARRAY_REQUIRED_ITEMS.get(), "编队阵列前置");
        loadItemSet(GUARD_ARRAY_ITEM_SET, GUARD_ARRAY_REQUIRED_ITEMS.get(), "守卫阵列前置");

        DANGEROUS_ENTITY_SET.clear();
        List<? extends String> dangerousEntities = DANGEROUS_ENTITIES.get();
        for (String entityId : dangerousEntities) {
            if (!entityId.trim().isEmpty()) {
                DANGEROUS_ENTITY_SET.add(entityId.trim());
                gytrinket.LOGGER.info("注册危险实体: {}", entityId);
            }
        }

        loadItemSet(CONVERSION_ITEM_SET, CONVERSION_ITEMS.get(), "转化效果启用");

        UpgradeManager.loadConfig();

        gytrinket.LOGGER.info("属性系统配置加载完成");
    }

    public static double getReflectRadius() {
        return REFLECT_RADIUS.get();
    }

    public static double getReflectSpeedBaseModifier() {
        return REFLECT_SPEED_BASE_MODIFIER.get();
    }

    public static double getReflectSpeedExtraModifier() {
        return REFLECT_SPEED_EXTRA_MODIFIER.get();
    }

    public static double getReflectDamageEffectMultiplier() {
        return REFLECT_DAMAGE_EFFECT_MULTIPLIER.get();
    }

    public static double getIgniteDefaultDamage() {
        return IGNITE_DEFAULT_DAMAGE.get();
    }

    public static int getIgniteDefaultDuration() {
        return IGNITE_DEFAULT_DURATION.get();
    }

    public static double getNaturalRecoveryPlayerHealth() {
        return NATURAL_RECOVERY_PLAYER_HEALTH.get();
    }

    public static double getNaturalRecoveryShield() {
        return NATURAL_RECOVERY_SHIELD.get();
    }

    public static double getNaturalRecoveryAttackCooldownPenalty() {
        return NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY.get();
    }

    public static int getHostileTargetMarkDuration() {
        return HOSTILE_TARGET_MARK_DURATION.get();
    }

    public static double getCoatingReductionPerLayer() {
        return COATING_REDUCTION_PER_LAYER.get();
    }

    public static boolean isDroneModuleItem(Item item) {
        return DRONE_MODULE_ITEM_SET.contains(item);
    }

    public static boolean isAssaultDroneModuleItem(Item item) {
        return ASSAULT_DRONE_MODULE_ITEM_SET.contains(item);
    }

    public static double getDroneBaseHealth() {
        return DRONE_BASE_HEALTH.get();
    }

    public static double getDroneBaseDamage() {
        return DRONE_BASE_DAMAGE.get();
    }

    public static int getDroneMaxCount() {
        return DRONE_MAX_COUNT.get();
    }

    public static double getDroneFollowRange() {
        return DRONE_FOLLOW_RANGE.get();
    }

    public static boolean isDefenseDroneModuleItem(Item item) {
        return DEFENSE_DRONE_MODULE_ITEM_SET.contains(item);
    }

    // ===== 僚机配置方法 =====

    public static boolean isWingmanModuleItem(Item item) {
        return WINGMAN_MODULE_ITEM_SET.contains(item);
    }

    public static double getWingmanBaseHealth() {
        return WINGMAN_BASE_HEALTH.get();
    }

    public static int getWingmanMaxCount() {
        return WINGMAN_MAX_COUNT.get();
    }

    public static double getWingmanAttackInterval() {
        return WINGMAN_ATTACK_INTERVAL.get();
    }

    public static Double getWingmanAttackRange() {
        return WINGMAN_ATTACK_RANGE.get();
    }

    public static int getWingmanExplosiveCount() {
        return WINGMAN_EXPLOSIVE_COUNT.get();
    }

    public static double getWingmanExplosiveDamage() {
        return WINGMAN_EXPLOSIVE_DAMAGE.get();
    }

    public static double getWingmanExplosionDamage() {
        return WINGMAN_EXPLOSION_DAMAGE.get();
    }

    public static double getWingmanExplosionRadius() {
        return WINGMAN_EXPLOSION_RADIUS.get();
    }

    // ===== 蜂群配置方法 =====

    public static boolean isSwarmModuleItem(Item item) {
        return SWARM_MODULE_ITEM_SET.contains(item);
    }

    public static double getSwarmBaseHealth() {
        return SWARM_BASE_HEALTH.get();
    }

    public static double getSwarmBaseDamage() {
        return SWARM_BASE_DAMAGE.get();
    }

    public static double getSwarmAttackInterval() {
        return SWARM_ATTACK_INTERVAL.get();
    }

    public static double getSwarmAttackRange() {
        return SWARM_ATTACK_RANGE.get();
    }

    public static double getSwarmSearchRange() {
        return SWARM_SEARCH_RANGE.get();
    }

    public static int getSwarmMaxCount() {
        return SWARM_MAX_COUNT.get();
    }

    public static int getSwarmCountLimit() {
        return SWARM_COUNT_LIMIT.get();
    }

    public static double getSwarmMoveSpeed() {
        return SWARM_MOVE_SPEED.get();
    }

    public static int getSwarmBuildTime() {
        return SWARM_BUILD_TIME.get();
    }

    public static double getSwarmTierUpgradeChanceStandard() {
        return SWARM_TIER_UPGRADE_CHANCE_STANDARD.get();
    }

    public static double getSwarmTierUpgradeChanceAdvanced() {
        return SWARM_TIER_UPGRADE_CHANCE_ADVANCED.get();
    }

    public static double getSwarmVulnerabilityValue() {
        return SWARM_VULNERABILITY_VALUE.get();
    }

    public static double getSwarmShieldRepairMultiplier() {
        return SWARM_SHIELD_REPAIR_MULTIPLIER.get();
    }

    public static boolean isNearDeathProtectionItem(Item item) {
        return NEAR_DEATH_PROTECTION_ITEM_SET.contains(item);
    }

    public static boolean isNearDeathExplosionItem(Item item) {
        return NEAR_DEATH_EXPLOSION_ITEM_SET.contains(item);
    }

    public static boolean isSelfDestructItem(Item item) {
        return SELF_DESTRUCT_ITEM_SET.contains(item);
    }

    public static boolean isTaskmasterItem(Item item) {
        return TASKMASTER_ITEM_SET.contains(item);
    }

    public static boolean isCommanderItem(Item item) {
        return COMMANDER_ITEM_SET.contains(item);
    }

    public static boolean isAdaptiveArmorItem(Item item) {
        return ADAPTIVE_ARMOR_ITEM_SET.contains(item);
    }

    public static int getAdaptiveArmorDuration() {
        return ADAPTIVE_ARMOR_DURATION.get();
    }

    public static int getAdaptiveArmorMaxLayersPerHit() {
        return ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.get();
    }

    public static double getAdaptiveArmorLayersPerDamage() {
        return ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.get();
    }

    public static boolean isAdaptiveArmorShieldEffectItem(Item item) {
        return ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET.contains(item);
    }

    public static boolean isShieldTransferItem(Item item) {
        return SHIELD_TRANSFER_ITEM_SET.contains(item);
    }

    public static boolean isBarrierItem(Item item) {
        return BARRIER_ITEM_SET.contains(item);
    }

    public static boolean isExplosiveShieldItem(Item item) {
        return EXPLOSIVE_SHIELD_ITEM_SET.contains(item);
    }

    public static boolean isReflectDamageItem(Item item) {
        return REFLECT_DAMAGE_ITEM_SET.contains(item);
    }

    public static boolean isElectricDischargeItem(Item item) {
        return ELECTRIC_DISCHARGE_ITEM_SET.contains(item);
    }

    public static double getElectricDischargeBurnCharge() {
        return ELECTRIC_DISCHARGE_BURN_CHARGE.get();
    }

    public static int getElectricDischargeBurnDuration() {
        return ELECTRIC_DISCHARGE_BURN_DURATION.get();
    }

    public static boolean isAttackCooldownEfficiencyItem(Item item) {
        return ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET.contains(item);
    }

    public static boolean isShieldNaturalRecoveryItem(Item item) {
        return SHIELD_NATURAL_RECOVERY_ITEM_SET.contains(item);
    }

    public static double getNaturalRecoveryShieldRecoveryPerTick() {
        return NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK.get();
    }

    public static double getNaturalRecoveryShieldPresentHealthModifier() {
        return NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER.get();
    }

    public static double getNaturalRecoveryShieldPresentShieldModifier() {
        return NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER.get();
    }

    public static boolean isBinaryProtocolItem(Item item) {
        return BINARY_PROTOCOL_ITEM_SET.contains(item);
    }

    public static boolean isWeaponizedShieldItem(Item item) {
        return WEAPONIZED_SHIELD_ITEM_SET.contains(item);
    }

    public static boolean isConversionItem(Item item) {
        return CONVERSION_ITEM_SET.contains(item);
    }

    public static boolean isDangerousEntity(String entityId) {
        return DANGEROUS_ENTITY_SET.contains(entityId);
    }

    public static double getAmplificationBaseAmplification() {
        return AMPLIFICATION_BASE_AMPLIFICATION.get();
    }

    public static double getAmplificationThreatAmplification() {
        return AMPLIFICATION_THREAT_AMPLIFICATION.get();
    }

    public static double getAmplificationCheckRadius() {
        return AMPLIFICATION_CHECK_RADIUS.get();
    }

    public static double getAmplificationMaxAmplification() {
        return AMPLIFICATION_MAX_AMPLIFICATION.get();
    }

    public static double getAmplificationMovementSpeedBonus() {
        return AMPLIFICATION_MOVEMENT_SPEED_BONUS.get();
    }

    public static double getWarpShieldExplosionDamage() {
        return WARP_SHIELD_EXPLOSION_DAMAGE.get();
    }

    public static boolean isArcBarrierItem(Item item) {
        return ARC_BARRIER_ITEM_SET.contains(item);
    }

    public static boolean isReshapingItem(Item item) {
        return RESHAPING_ITEM_SET.contains(item);
    }

    public static boolean isCounterPulseItem(Item item) {
        return COUNTER_PULSE_ITEM_SET.contains(item);
    }

    public static boolean isAssaultItem(Item item) {
        return ASSAULT_ITEM_SET.contains(item);
    }

    public static double getAssaultAttackSpeedPerStack() {
        return ASSAULT_ATTACK_SPEED_PER_STACK.get();
    }

    public static int getAssaultDurationTicks() {
        return ASSAULT_DURATION_TICKS.get();
    }

    public static double getAssaultSelfDamagePerStack() {
        return ASSAULT_SELF_DAMAGE_PER_STACK.get();
    }

    public static double getAssaultMovementSpeedPenalty() {
        return ASSAULT_MOVEMENT_SPEED_PENALTY.get();
    }

    public static boolean isChargedAttackItem(Item item) {
        return CHARGED_ATTACK_ITEM_SET.contains(item);
    }

    public static double getChargedAttackBaseChargeRate() {
        return CHARGED_ATTACK_BASE_CHARGE_RATE.get();
    }

    public static double getChargedAttackSpeedScaleFactor() {
        return CHARGED_ATTACK_SPEED_SCALE_FACTOR.get();
    }

    public static double getChargedAttackDragCoefficient() {
        return CHARGED_ATTACK_DRAG_COEFFICIENT.get();
    }

    public static double getChargedAttackDragThresholdFactor() {
        return CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.get();
    }

    public static double getChargedAttackMovementSpeedPenalty() {
        return CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.get();
    }

    public static boolean isChargedShieldItem(Item item) {
        return CHARGED_SHIELD_ITEM_SET.contains(item);
    }

    public static double getChargedShieldChargeRatio() {
        return CHARGED_SHIELD_CHARGE_RATIO.get();
    }

    public static double getChargedShieldMaxBonus() {
        return CHARGED_SHIELD_MAX_BONUS.get();
    }

    public static double getChargedShieldDecayRate() {
        return CHARGED_SHIELD_DECAY_RATE.get();
    }

    public static double getChargedShieldMovementSpeedPenalty() {
        return CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY.get();
    }

    public static boolean isGrudgeItem(Item item) {
        return GRUDGE_ITEM_SET.contains(item);
    }

    public static boolean isPrecisionConstructItem(Item item) {
        return PRECISION_CONSTRUCT_ITEM_SET.contains(item);
    }

    public static boolean isAdvancedEngineeringItem(Item item) {
        return ADVANCED_ENGINEERING_ITEM_SET.contains(item);
    }

    public static boolean isPursuitArrayItem(Item item) {
        return PURSUIT_ARRAY_ITEM_SET.contains(item);
    }

    public static boolean isFormationArrayItem(Item item) {
        return FORMATION_ARRAY_ITEM_SET.contains(item);
    }

    public static boolean isGuardArrayItem(Item item) {
        return GUARD_ARRAY_ITEM_SET.contains(item);
    }

    public static double getGrudgeConversionRatio() {
        return GRUDGE_CONVERSION_RATIO.get();
    }

    public static double getGrudgeFadeBase() {
        return GRUDGE_FADE_BASE.get();
    }

    public static double getGrudgeFadePercent() {
        return GRUDGE_FADE_PERCENT.get();
    }

    public static double getGrudgeMovementSpeedPenalty() {
        return GRUDGE_MOVEMENT_SPEED_PENALTY.get();
    }

    public static int getQuickEquipUpgradePointsCost() {
        return QUICK_EQUIP_UPGRADE_POINTS_COST.get();
    }

    public static boolean isHardcoreModeEnabled() {
        return HARDCORE_MODE_ENABLED.get();
    }
}
