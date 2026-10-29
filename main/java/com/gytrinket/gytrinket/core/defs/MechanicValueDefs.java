package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.core.damage.ReflectDamageHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 特殊机制数值参数注册表（编译期静态定义）。
 * <p>
 * 定义配置面板可为每个机制集合编辑的物品级数值参数：参数键（存储/网络传输用）、
 * 显示翻译键、静态默认值提供者。
 * 阵列类机制（orbit/pursuit/formation/guard）同样在此注册。
 * <p>
 * 覆盖值由 UI 产生，存储在 {@link DefsTypes.SpecialMechanicOverride} 的 values 段；
 * 运行时读取走 {@link DefsManager#resolveMechanicValue}（服务端）/
 * {@link DefsManager#clientResolveMechanicValue}（客户端），未覆盖回退此处默认值。
 */
public final class MechanicValueDefs {

    /** 单个机制参数：参数键 + 显示翻译键 + 静态默认值提供者 */
    public record ParamDef(String key, String nameKey, DoubleSupplier defaultSupplier) {
        public double defaultValue() { return defaultSupplier.getAsDouble(); }
    }

    private static final Map<String, List<ParamDef>> PARAMS = new LinkedHashMap<>();

    /** 已迁移到 item_definitions 的机制参数最终回退值（Config 全局项已移除） */
    private static final double BARRIER_MAX_DAMAGE = 5.0;
    private static final double SHIELD_TRANSFER_PENALTY = 0.04;
    private static final double EXPLOSIVE_RADIUS = 4.0;
    private static final double EXPLOSIVE_DAMAGE = 10.0;
    private static final double WEAPONIZED_RADIUS = 4.0;
    private static final double WEAPONIZED_VULNERABILITY = 0.2;
    private static final double ELECTRIC_BURN_DURATION = 10.0;
    private static final double ELECTRIC_BURN_CHARGE = 0.5;

    /** 以下机制的行为参数已迁移到 item_definitions 的 values 段（Config 全局项已移除） */
    private static final double NEAR_DEATH_INVINCIBLE_DURATION = 20.0;
    private static final double NEAR_DEATH_COOLDOWN = 300.0;
    private static final double SELF_DESTRUCT_BASE_DAMAGE = 1.0;
    private static final double SELF_DESTRUCT_BASE_RADIUS = 1.0;
    private static final double COUNTER_PULSE_RADIUS = 1.3;
    private static final double COUNTER_PULSE_DAMAGE = 1.0;
    private static final double COUNTER_PULSE_COOLDOWN = 60.0;
    private static final double PROJECTILE_EXPLOSION_DAMAGE_FRACTION = 0.15;
    private static final double PROJECTILE_EXPLOSION_RADIUS_BASE = 2.0;
    private static final double PROJECTILE_EXPLOSION_RADIUS_PER_DAMAGE = 0.5;
    private static final double PRECISION_CONSTRUCT_BONUS_PER_LEVEL = 0.0025;
    private static final double ADVANCED_ENGINEERING_BONUS_PER_LEVEL = 0.01;
    private static final double CONVERSION_RATIO = 0.3;
    private static final double ASSAULT_ATTACK_SPEED_PER_STACK = 0.1;
    private static final double ASSAULT_SELF_DAMAGE_PER_STACK = 0.02;
    private static final double ASSAULT_OVERFLOW_EFFICIENCY = 1.0;
    private static final double ASSAULT_MOVE_SPEED_PENALTY = -0.6;
    private static final double JOURNEY_ATTACK_SPEED_PER_STACK = 0.0075;
    private static final double JOURNEY_MOVE_SPEED_PER_STACK = 0.0075;
    private static final double JOURNEY_DURATION_TICKS = 100.0;
    private static final double JOURNEY_MAX_STACKS = 40.0;
    private static final double JOURNEY_DECAY_INTERVAL_TICKS = 3.0;
    private static final double JOURNEY_DECAY_PER_INTERVAL = 2.0;
    private static final double CHARGED_SHIELD_CHARGE_RATIO = 0.1;
    private static final double CHARGED_SHIELD_MAX_BONUS = 0.8;
    private static final double CHARGED_SHIELD_DECAY_RATE = 0.08;
    private static final double CHARGED_SHIELD_MOVE_SPEED_PENALTY = -0.15;
    /** 再生护盾：模块自带的护盾再生速度（%/秒，按各实例自身上限计算） */
    private static final double SHIELD_REGEN_PER_SECOND = 0.02;
    /** 再生护盾：有护盾值时的玩家生命再生效率 */
    private static final double SHIELD_PRESENT_HEALTH_MODIFIER = 0.5;
    /** 再生护盾：有护盾值时的护盾再生效率 */
    private static final double SHIELD_PRESENT_SHIELD_MODIFIER = 0.75;
    /** 再生护盾：无护盾值时的玩家生命再生效率 */
    private static final double NO_SHIELD_HEALTH_MODIFIER = 1.0;
    private static final double GRUDGE_CONVERSION_RATIO = 0.2;
    private static final double GRUDGE_FADE_BASE = 0.00001;
    private static final double GRUDGE_FADE_PERCENT = 0.02;
    private static final double GRUDGE_MOVE_SPEED_PENALTY = -0.15;

    /** 机身/构造体基础属性的静态默认（已迁移到 item_definitions 的 values 段） */
    private static final double GHOST_STEALTH_SPEED_PER_LEVEL = 0.005;
    private static final double GHOST_MAX_BONUS_PER_LEVEL = 0.005;
    private static final double GHOST_BASE_MAX_DAMAGE_BONUS = 3.0;
    private static final double GHOST_MOVE_SPEED_THRESHOLD = 0.25;
    private static final double GHOST_MOVE_SPEED_REDUCTION = 0.2;
    private static final double GHOST_DECAY_RATE = 0.3;
    private static final double GHOST_MIN_DECAY = 0.02;
    private static final double GHOST_FULL_STEALTH_TICKS = 40.0;

    private static final double WINGMAN_EVOLUTION_BONUS_PER_LEVEL = 0.00625;
    private static final double WINGMAN_NANO_REGEN_PERCENT = 0.02;

    private static final double DRONE_BUILD_TIME = 100.0;
    private static final double DRONE_BASE_HEALTH = 5.0;
    private static final double DRONE_BASE_DAMAGE = 0.3;
    private static final double DRONE_BASE_COUNT = 3.0;
    private static final double DRONE_ATTACK_INTERVAL = 0.5;
    private static final double DRONE_ATTACK_RANGE = 8.0;
    private static final double DRONE_TARGET_RANGE = 8.0;

    private static final double SWARM_BUILD_TIME = 20.0;
    private static final double SWARM_BASE_HEALTH = 1.6;
    private static final double SWARM_BASE_DAMAGE = 0.08;
    private static final double SWARM_BASE_COUNT = 4.0;
    private static final double SWARM_ATTACK_INTERVAL = 1.5;
    private static final double SWARM_ATTACK_RANGE = 4.0;
    private static final double SWARM_SEARCH_RANGE = 20.0;

    private static final double WINGMAN_BUILD_TIME = 500.0;
    private static final double WINGMAN_BASE_HEALTH = 24.0;
    private static final double WINGMAN_BASE_DAMAGE = 0.5;
    private static final double WINGMAN_BASE_COUNT = 1.0;
    private static final double WINGMAN_ATTACK_INTERVAL = 1.0;
    private static final double WINGMAN_ATTACK_RANGE = 20.0;

    private static void register(String mechanicSet, ParamDef... defs) {
        PARAMS.put(mechanicSet, List.of(defs));
    }

    /** 生成机制参数的显示翻译键：gui.gytrinket.mechanic_value.&lt;机制基名&gt;.&lt;参数键&gt; */
    private static String nameKey(String mechanicSet, String key) {
        String base = mechanicSet.endsWith("_items")
                ? mechanicSet.substring(0, mechanicSet.length() - "_items".length()) : mechanicSet;
        return "gui.gytrinket.mechanic_value." + base + "." + key;
    }

    static {
        // ===== 再生护盾 (shield_natural_recovery) =====
        // 装备后启用护盾自然恢复：每秒按各护盾实例自身上限的 regen_per_second 回复（不回复已归零的实例）；
        // 是否“有护盾值”决定生命/护盾再生效率取哪一组。
        register("shield_natural_recovery_items",
                new ParamDef("regen_per_second", nameKey("shield_natural_recovery_items", "regen_per_second"),
                        () -> SHIELD_REGEN_PER_SECOND),
                new ParamDef("shield_present_health_modifier", nameKey("shield_natural_recovery_items", "shield_present_health_modifier"),
                        () -> SHIELD_PRESENT_HEALTH_MODIFIER),
                new ParamDef("shield_present_shield_modifier", nameKey("shield_natural_recovery_items", "shield_present_shield_modifier"),
                        () -> SHIELD_PRESENT_SHIELD_MODIFIER),
                new ParamDef("no_shield_health_modifier", nameKey("shield_natural_recovery_items", "no_shield_health_modifier"),
                        () -> NO_SHIELD_HEALTH_MODIFIER)
        );

        // ===== 充能护盾 (charged_shield) =====
        register("charged_shield_items",
                new ParamDef("charge_ratio", nameKey("charged_shield_items", "charge_ratio"),
                        () -> CHARGED_SHIELD_CHARGE_RATIO),
                new ParamDef("max_bonus", nameKey("charged_shield_items", "max_bonus"),
                        () -> CHARGED_SHIELD_MAX_BONUS),
                new ParamDef("decay_rate", nameKey("charged_shield_items", "decay_rate"),
                        () -> CHARGED_SHIELD_DECAY_RATE),
                new ParamDef("move_speed_penalty", nameKey("charged_shield_items", "move_speed_penalty"),
                        () -> CHARGED_SHIELD_MOVE_SPEED_PENALTY)
        );

        // ===== 幽灵机身 (ghost_fuselage) =====
        register("ghost_fuselage_items",
                new ParamDef("stealth_speed_bonus_per_level", nameKey("ghost_fuselage_items", "stealth_speed_bonus_per_level"),
                        () -> GHOST_STEALTH_SPEED_PER_LEVEL),
                new ParamDef("max_bonus_per_level", nameKey("ghost_fuselage_items", "max_bonus_per_level"),
                        () -> GHOST_MAX_BONUS_PER_LEVEL),
                new ParamDef("base_max_damage_bonus", nameKey("ghost_fuselage_items", "base_max_damage_bonus"),
                        () -> GHOST_BASE_MAX_DAMAGE_BONUS),
                new ParamDef("move_speed_threshold", nameKey("ghost_fuselage_items", "move_speed_threshold"),
                        () -> GHOST_MOVE_SPEED_THRESHOLD),
                new ParamDef("move_speed_reduction", nameKey("ghost_fuselage_items", "move_speed_reduction"),
                        () -> GHOST_MOVE_SPEED_REDUCTION),
                new ParamDef("decay_rate", nameKey("ghost_fuselage_items", "decay_rate"),
                        () -> GHOST_DECAY_RATE),
                new ParamDef("min_decay", nameKey("ghost_fuselage_items", "min_decay"),
                        () -> GHOST_MIN_DECAY),
                new ParamDef("full_stealth_ticks", nameKey("ghost_fuselage_items", "full_stealth_ticks"),
                        () -> GHOST_FULL_STEALTH_TICKS)
        );

        // ===== 积怨 (grudge) =====
        register("grudge_items",
                new ParamDef("conversion_ratio", nameKey("grudge_items", "conversion_ratio"),
                        () -> GRUDGE_CONVERSION_RATIO),
                new ParamDef("fade_base", nameKey("grudge_items", "fade_base"),
                        () -> GRUDGE_FADE_BASE),
                new ParamDef("fade_percent", nameKey("grudge_items", "fade_percent"),
                        () -> GRUDGE_FADE_PERCENT),
                new ParamDef("move_speed_penalty", nameKey("grudge_items", "move_speed_penalty"),
                        () -> GRUDGE_MOVE_SPEED_PENALTY)
        );

        // ===== 精密构造 (precision_construct) =====
        register("precision_construct_items",
                new ParamDef("bonus_per_level", nameKey("precision_construct_items", "bonus_per_level"),
                        () -> PRECISION_CONSTRUCT_BONUS_PER_LEVEL)
        );

        // ===== 高等工程 (advanced_engineering) =====
        register("advanced_engineering_items",
                new ParamDef("bonus_per_level", nameKey("advanced_engineering_items", "bonus_per_level"),
                        () -> ADVANCED_ENGINEERING_BONUS_PER_LEVEL)
        );

        // ===== 僚机进化模块 (wingman_evolution_module) =====
        register("wingman_evolution_module_items",
                new ParamDef("bonus_per_level", nameKey("wingman_evolution_module_items", "bonus_per_level"),
                        () -> WINGMAN_EVOLUTION_BONUS_PER_LEVEL)
        );

        // ===== 僚机纳米再生模块 (wingman_nano_regen_module) =====
        register("wingman_nano_regen_module_items",
                new ParamDef("regen_percent", nameKey("wingman_nano_regen_module_items", "regen_percent"),
                        () -> WINGMAN_NANO_REGEN_PERCENT)
        );

        // ===== 生命护盾转化 (conversion，实例化机制：每个来源物品一个转化实例) =====
        register("conversion_items",
                new ParamDef("conversion_ratio", nameKey("conversion_items", "conversion_ratio"),
                        () -> CONVERSION_RATIO),
                // 转化方向：0=双向比较（默认，较少一方按比例转化给较多一方），1=仅护盾转生命，2=仅生命转护盾
                new ParamDef("conversion_direction", nameKey("conversion_items", "conversion_direction"),
                        () -> 0.0)
        );

        // ===== 结界 (barrier) =====
        register("barrier_items",
                new ParamDef("barrier_max_damage", nameKey("barrier_items", "barrier_max_damage"),
                        () -> BARRIER_MAX_DAMAGE)
        );

        // ===== 护盾移植 (shield_transfer) =====
        register("shield_transfer_items",
                new ParamDef("penalty_per_entity", nameKey("shield_transfer_items", "penalty_per_entity"),
                        () -> SHIELD_TRANSFER_PENALTY)
        );

        // ===== 电能释放 (electric_discharge) =====
        register("electric_discharge_items",
                new ParamDef("burn_duration", nameKey("electric_discharge_items", "burn_duration"),
                        () -> ELECTRIC_BURN_DURATION),
                new ParamDef("burn_charge", nameKey("electric_discharge_items", "burn_charge"),
                        () -> ELECTRIC_BURN_CHARGE)
        );

        // ===== 强袭 (assault，非实例化：走默认叠/单合并策略) =====
        register("assault_items",
                new ParamDef("attack_speed_per_stack", nameKey("assault_items", "attack_speed_per_stack"),
                        () -> ASSAULT_ATTACK_SPEED_PER_STACK),
                new ParamDef("self_damage_per_stack", nameKey("assault_items", "self_damage_per_stack"),
                        () -> ASSAULT_SELF_DAMAGE_PER_STACK),
                new ParamDef("overflow_damage_efficiency", nameKey("assault_items", "overflow_damage_efficiency"),
                        () -> ASSAULT_OVERFLOW_EFFICIENCY),
                new ParamDef("move_speed_penalty", nameKey("assault_items", "move_speed_penalty"),
                        () -> ASSAULT_MOVE_SPEED_PENALTY)
        );

        // ===== 征途 (journey_module，非实例化：叠层参数按物品解析) =====
        register("journey_module_items",
                new ParamDef("attack_speed_per_stack", nameKey("journey_module_items", "attack_speed_per_stack"),
                        () -> JOURNEY_ATTACK_SPEED_PER_STACK),
                new ParamDef("movement_speed_per_stack", nameKey("journey_module_items", "movement_speed_per_stack"),
                        () -> JOURNEY_MOVE_SPEED_PER_STACK),
                new ParamDef("duration_ticks", nameKey("journey_module_items", "duration_ticks"),
                        () -> JOURNEY_DURATION_TICKS),
                new ParamDef("max_stacks", nameKey("journey_module_items", "max_stacks"),
                        () -> JOURNEY_MAX_STACKS),
                new ParamDef("decay_interval_ticks", nameKey("journey_module_items", "decay_interval_ticks"),
                        () -> JOURNEY_DECAY_INTERVAL_TICKS),
                new ParamDef("decay_per_interval", nameKey("journey_module_items", "decay_per_interval"),
                        () -> JOURNEY_DECAY_PER_INTERVAL)
        );

        // ===== 湮灭护盾 (explosive_shield) =====
        register("explosive_shield_items",
                new ParamDef("radius", nameKey("explosive_shield_items", "radius"),
                        () -> EXPLOSIVE_RADIUS),
                new ParamDef("base_damage", nameKey("explosive_shield_items", "base_damage"),
                        () -> EXPLOSIVE_DAMAGE)
        );

        // ===== 武装护盾 (weaponized_shield) =====
        register("weaponized_shield_items",
                new ParamDef("radius", nameKey("weaponized_shield_items", "radius"),
                        () -> WEAPONIZED_RADIUS),
                new ParamDef("vulnerability", nameKey("weaponized_shield_items", "vulnerability"),
                        () -> WEAPONIZED_VULNERABILITY)
        );

        // ===== 濒死保护 (near_death_protection) =====
        register("near_death_protection_items",
                new ParamDef("invincible_duration", nameKey("near_death_protection_items", "invincible_duration"),
                        () -> NEAR_DEATH_INVINCIBLE_DURATION),
                new ParamDef("cooldown", nameKey("near_death_protection_items", "cooldown"),
                        () -> NEAR_DEATH_COOLDOWN)
        );

        // ===== 自毁装置 (self_destruct) =====
        register("self_destruct_items",
                new ParamDef("base_damage", nameKey("self_destruct_items", "base_damage"),
                        () -> SELF_DESTRUCT_BASE_DAMAGE),
                new ParamDef("base_radius", nameKey("self_destruct_items", "base_radius"),
                        () -> SELF_DESTRUCT_BASE_RADIUS)
        );

        // ===== 弹射物次级爆炸 (projectile_explosion) =====
        register("projectile_explosion_items",
                new ParamDef("damage_fraction", nameKey("projectile_explosion_items", "damage_fraction"),
                        () -> PROJECTILE_EXPLOSION_DAMAGE_FRACTION),
                new ParamDef("radius_base", nameKey("projectile_explosion_items", "radius_base"),
                        () -> PROJECTILE_EXPLOSION_RADIUS_BASE),
                new ParamDef("radius_per_damage", nameKey("projectile_explosion_items", "radius_per_damage"),
                        () -> PROJECTILE_EXPLOSION_RADIUS_PER_DAMAGE)
        );

        // ===== 反制脉冲 (counter_pulse) =====
        register("counter_pulse_items",
                new ParamDef("explosion_radius", nameKey("counter_pulse_items", "explosion_radius"),
                        () -> COUNTER_PULSE_RADIUS),
                new ParamDef("explosion_damage", nameKey("counter_pulse_items", "explosion_damage"),
                        () -> COUNTER_PULSE_DAMAGE),
                new ParamDef("cooldown", nameKey("counter_pulse_items", "cooldown"),
                        () -> COUNTER_PULSE_COOLDOWN)
        );

        // ===== 反射护盾 (reflect_damage，实例化机制：参数按物品独立解析) =====
        register("reflect_damage_items",
                new ParamDef("base_explosion_damage", nameKey("reflect_damage_items", "base_explosion_damage"),
                        () -> ReflectDamageHandler.BASE_EXPLOSION_DAMAGE),
                new ParamDef("base_sputter_length", nameKey("reflect_damage_items", "base_sputter_length"),
                        () -> ReflectDamageHandler.BASE_SPUTTER_LENGTH),
                new ParamDef("damage_threshold", nameKey("reflect_damage_items", "damage_threshold"),
                        () -> ReflectDamageHandler.DAMAGE_THRESHOLD),
                new ParamDef("damage_bonus_per_excess", nameKey("reflect_damage_items", "damage_bonus_per_excess"),
                        () -> ReflectDamageHandler.DAMAGE_BONUS_PER_EXCESS),
                new ParamDef("length_bonus_per_excess", nameKey("reflect_damage_items", "length_bonus_per_excess"),
                        () -> ReflectDamageHandler.LENGTH_BONUS_PER_EXCESS),
                new ParamDef("base_particle_count", nameKey("reflect_damage_items", "base_particle_count"),
                        () -> ReflectDamageHandler.BASE_PARTICLE_COUNT),
                new ParamDef("particle_count_per_excess", nameKey("reflect_damage_items", "particle_count_per_excess"),
                        () -> ReflectDamageHandler.PARTICLE_COUNT_PER_EXCESS),
                new ParamDef("base_angle", nameKey("reflect_damage_items", "base_angle"),
                        () -> ReflectDamageHandler.BASE_ANGLE),
                new ParamDef("angle_per_excess", nameKey("reflect_damage_items", "angle_per_excess"),
                        () -> ReflectDamageHandler.ANGLE_PER_EXCESS),
                new ParamDef("max_angle_cap", nameKey("reflect_damage_items", "max_angle_cap"),
                        () -> ReflectDamageHandler.MAX_ANGLE_CAP),
                new ParamDef("speed_multiplier_per_excess", nameKey("reflect_damage_items", "speed_multiplier_per_excess"),
                        () -> ReflectDamageHandler.SPEED_MULTIPLIER_PER_EXCESS),
                new ParamDef("max_speed_multiplier", nameKey("reflect_damage_items", "max_speed_multiplier"),
                        () -> ReflectDamageHandler.MAX_SPEED_MULTIPLIER)
        );

        // ===== 无人机构建模块 (drone_module，实例化机制：每个来源物品一个无人机实例) =====
        register("drone_module_items",
                new ParamDef("build_time", nameKey("drone_module_items", "build_time"),
                        () -> DRONE_BUILD_TIME),
                new ParamDef("base_health", nameKey("drone_module_items", "base_health"),
                        () -> DRONE_BASE_HEALTH),
                new ParamDef("base_damage", nameKey("drone_module_items", "base_damage"),
                        () -> DRONE_BASE_DAMAGE),
                new ParamDef("base_count", nameKey("drone_module_items", "base_count"),
                        () -> DRONE_BASE_COUNT),
                new ParamDef("attack_interval", nameKey("drone_module_items", "attack_interval"),
                        () -> DRONE_ATTACK_INTERVAL),
                new ParamDef("move_speed_multiplier", nameKey("drone_module_items", "move_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("attack_range", nameKey("drone_module_items", "attack_range"),
                        () -> DRONE_ATTACK_RANGE),
                new ParamDef("target_range", nameKey("drone_module_items", "target_range"),
                        () -> DRONE_TARGET_RANGE)
        );

        // ===== 蜂群构建模块 (swarm_module，实例化机制：每个来源物品（母舰机身）一个蜂群实例) =====
        register("swarm_module_items",
                new ParamDef("build_time", nameKey("swarm_module_items", "build_time"),
                        () -> SWARM_BUILD_TIME),
                new ParamDef("base_health", nameKey("swarm_module_items", "base_health"),
                        () -> SWARM_BASE_HEALTH),
                new ParamDef("base_damage", nameKey("swarm_module_items", "base_damage"),
                        () -> SWARM_BASE_DAMAGE),
                new ParamDef("base_count", nameKey("swarm_module_items", "base_count"),
                        () -> SWARM_BASE_COUNT),
                new ParamDef("attack_interval", nameKey("swarm_module_items", "attack_interval"),
                        () -> SWARM_ATTACK_INTERVAL),
                new ParamDef("attack_range", nameKey("swarm_module_items", "attack_range"),
                        () -> SWARM_ATTACK_RANGE),
                new ParamDef("search_range", nameKey("swarm_module_items", "search_range"),
                        () -> SWARM_SEARCH_RANGE),
                new ParamDef("move_speed_multiplier", nameKey("swarm_module_items", "move_speed_multiplier"),
                        () -> 1.0)
        );

        // ===== 僚机构建模块 (wingman_module，实例化机制：每个来源物品一个僚机实例) =====
        register("wingman_module_items",
                new ParamDef("build_time", nameKey("wingman_module_items", "build_time"),
                        () -> WINGMAN_BUILD_TIME),
                new ParamDef("base_health", nameKey("wingman_module_items", "base_health"),
                        () -> WINGMAN_BASE_HEALTH),
                new ParamDef("base_damage", nameKey("wingman_module_items", "base_damage"),
                        () -> WINGMAN_BASE_DAMAGE),
                new ParamDef("base_count", nameKey("wingman_module_items", "base_count"),
                        () -> WINGMAN_BASE_COUNT),
                new ParamDef("attack_interval", nameKey("wingman_module_items", "attack_interval"),
                        () -> WINGMAN_ATTACK_INTERVAL),
                new ParamDef("attack_range", nameKey("wingman_module_items", "attack_range"),
                        () -> WINGMAN_ATTACK_RANGE)
        );

        // ===== 环绕阵列 (orbit_array_required，非实例化：攻速 + 环绕速度 + 范围基准，独立乘区作用于所有无人机；
        // 无前置物品，可在配置面板为任意物品添加) =====
        register("orbit_array_required_items",
                new ParamDef("attack_speed_multiplier", nameKey("orbit_array_required_items", "attack_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("orbit_speed_multiplier", nameKey("orbit_array_required_items", "orbit_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("damage_multiplier", nameKey("orbit_array_required_items", "damage_multiplier"),
                        () -> 1.0),
                new ParamDef("attack_range_multiplier", nameKey("orbit_array_required_items", "attack_range_multiplier"),
                        () -> 1.0),
                new ParamDef("target_range_multiplier", nameKey("orbit_array_required_items", "target_range_multiplier"),
                        () -> 1.0)
        );

        // ===== 追击阵列 (pursuit_array_required，非实例化：攻速 +50% + 移动速度 + 范围 ×2.5，独立乘区) =====
        register("pursuit_array_required_items",
                new ParamDef("attack_speed_multiplier", nameKey("pursuit_array_required_items", "attack_speed_multiplier"),
                        () -> 1.5),
                new ParamDef("move_speed_multiplier", nameKey("pursuit_array_required_items", "move_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("damage_multiplier", nameKey("pursuit_array_required_items", "damage_multiplier"),
                        () -> 1.0),
                new ParamDef("attack_range_multiplier", nameKey("pursuit_array_required_items", "attack_range_multiplier"),
                        () -> 2.5),
                new ParamDef("target_range_multiplier", nameKey("pursuit_array_required_items", "target_range_multiplier"),
                        () -> 2.5)
        );

        // ===== 列队阵列 (formation_array_required，非实例化：攻速 -20% + 伤害 ×2 + 范围 ×1.875，独立乘区) =====
        register("formation_array_required_items",
                new ParamDef("attack_speed_multiplier", nameKey("formation_array_required_items", "attack_speed_multiplier"),
                        () -> 0.8),
                new ParamDef("damage_multiplier", nameKey("formation_array_required_items", "damage_multiplier"),
                        () -> 2.0),
                new ParamDef("attack_range_multiplier", nameKey("formation_array_required_items", "attack_range_multiplier"),
                        () -> 1.875),
                new ParamDef("target_range_multiplier", nameKey("formation_array_required_items", "target_range_multiplier"),
                        () -> 1.875)
        );

        // ===== 守卫阵列 (guard_array_required，非实例化：仅攻速修正，范围与环绕基准一致，独立乘区) =====
        register("guard_array_required_items",
                new ParamDef("attack_speed_multiplier", nameKey("guard_array_required_items", "attack_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("damage_multiplier", nameKey("guard_array_required_items", "damage_multiplier"),
                        () -> 1.0),
                new ParamDef("attack_range_multiplier", nameKey("guard_array_required_items", "attack_range_multiplier"),
                        () -> 1.0),
                new ParamDef("target_range_multiplier", nameKey("guard_array_required_items", "target_range_multiplier"),
                        () -> 1.0)
        );
    }

    private MechanicValueDefs() {}

    /** 获取机制集合可编辑的参数列表（未注册的机制返回空列表） */
    public static List<ParamDef> getParams(String mechanicSet) {
        return PARAMS.getOrDefault(mechanicSet, List.of());
    }

    /** 所有已注册数值参数的机制集合名（供特殊机制选择器并入可添加列表，如无数据包声明的环绕阵列） */
    public static List<String> allSets() {
        return List.copyOf(PARAMS.keySet());
    }

    /** 实例化机制集合：每个来源物品一个实例，参数按物品独立解析（无叠/单合并语义） */
    private static final List<String> INSTANCE_SETS = List.of(
            "reflect_damage_items", "drone_module_items", "swarm_module_items", "wingman_module_items",
            "conversion_items");

    /** 是否为实例化机制集合（参数按物品独立解析，编辑器不显示叠/单切换） */
    public static boolean isInstanceSet(String mechanicSet) {
        return INSTANCE_SETS.contains(mechanicSet);
    }

    /** 获取参数默认值（未注册的机制/参数返回 null） */
    public static Double getDefaultValue(String mechanicSet, String paramKey) {
        for (ParamDef def : getParams(mechanicSet)) {
            if (def.key().equals(paramKey)) {
                return def.defaultValue();
            }
        }
        return null;
    }

    /** 获取参数默认值，未注册时返回调用方给定的回退值 */
    public static double defaultValueOf(String mechanicSet, String paramKey, double fallback) {
        Double value = getDefaultValue(mechanicSet, paramKey);
        return value != null ? value : fallback;
    }
}
