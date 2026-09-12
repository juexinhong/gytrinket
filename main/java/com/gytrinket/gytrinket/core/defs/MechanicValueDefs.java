package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.damage.ReflectDamageHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 特殊机制数值参数注册表（编译期静态定义）。
 * <p>
 * 定义配置面板可为每个机制集合编辑的物品级数值参数：参数键（存储/网络传输用）、
 * 显示翻译键、Config 默认值提供者。阶段一仅注册非周期类机制；
 * 阵列类周期机制（pursuit/formation/guard）待阶段二多实例独立后加入。
 * <p>
 * 覆盖值由 UI 产生，存储在 {@link DefsManager.SpecialMechanicOverride} 的 values 段；
 * 运行时读取走 {@link DefsManager#resolveMechanicValue}（服务端）/
 * {@link DefsManager#clientResolveMechanicValue}（客户端），未覆盖回退此处默认值。
 */
public final class MechanicValueDefs {

    /** 单个机制参数：参数键 + 显示翻译键 + Config 默认值提供者 */
    public record ParamDef(String key, String nameKey, DoubleSupplier defaultSupplier) {
        public double defaultValue() { return defaultSupplier.getAsDouble(); }
    }

    private static final Map<String, List<ParamDef>> PARAMS = new LinkedHashMap<>();

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
        // ===== 充能护盾 (charged_shield) =====
        register("charged_shield_items",
                new ParamDef("charge_ratio", nameKey("charged_shield_items", "charge_ratio"),
                        Config::getChargedShieldChargeRatio),
                new ParamDef("max_bonus", nameKey("charged_shield_items", "max_bonus"),
                        Config::getChargedShieldMaxBonus),
                new ParamDef("decay_rate", nameKey("charged_shield_items", "decay_rate"),
                        Config::getChargedShieldDecayRate),
                new ParamDef("move_speed_penalty", nameKey("charged_shield_items", "move_speed_penalty"),
                        Config::getChargedShieldMovementSpeedPenalty)
        );

        // ===== 幽灵机身 (ghost_fuselage) =====
        register("ghost_fuselage_items",
                new ParamDef("stealth_speed_bonus_per_level", nameKey("ghost_fuselage_items", "stealth_speed_bonus_per_level"),
                        Config::getGhostFuselageStealthSpeedBonusPerLevel),
                new ParamDef("max_bonus_per_level", nameKey("ghost_fuselage_items", "max_bonus_per_level"),
                        Config::getGhostFuselageMaxBonusPerLevel),
                new ParamDef("base_max_damage_bonus", nameKey("ghost_fuselage_items", "base_max_damage_bonus"),
                        Config::getGhostFuselageBaseMaxDamageBonus),
                new ParamDef("move_speed_threshold", nameKey("ghost_fuselage_items", "move_speed_threshold"),
                        Config::getGhostFuselageMoveSpeedThreshold),
                new ParamDef("move_speed_reduction", nameKey("ghost_fuselage_items", "move_speed_reduction"),
                        Config::getGhostFuselageMoveSpeedReduction),
                new ParamDef("decay_rate", nameKey("ghost_fuselage_items", "decay_rate"),
                        Config::getGhostFuselageDecayRate),
                new ParamDef("min_decay", nameKey("ghost_fuselage_items", "min_decay"),
                        Config::getGhostFuselageMinDecay),
                new ParamDef("full_stealth_ticks", nameKey("ghost_fuselage_items", "full_stealth_ticks"),
                        () -> Config.getGhostFuselageFullStealthTicks())
        );

        // ===== 积怨 (grudge) =====
        register("grudge_items",
                new ParamDef("conversion_ratio", nameKey("grudge_items", "conversion_ratio"),
                        Config::getGrudgeConversionRatio),
                new ParamDef("fade_base", nameKey("grudge_items", "fade_base"),
                        Config::getGrudgeFadeBase),
                new ParamDef("fade_percent", nameKey("grudge_items", "fade_percent"),
                        Config::getGrudgeFadePercent),
                new ParamDef("move_speed_penalty", nameKey("grudge_items", "move_speed_penalty"),
                        Config::getGrudgeMovementSpeedPenalty)
        );

        // ===== 精密构造 (precision_construct) =====
        register("precision_construct_items",
                new ParamDef("bonus_per_level", nameKey("precision_construct_items", "bonus_per_level"),
                        () -> Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get())
        );

        // ===== 高等工程 (advanced_engineering) =====
        register("advanced_engineering_items",
                new ParamDef("bonus_per_level", nameKey("advanced_engineering_items", "bonus_per_level"),
                        () -> Config.ADVANCED_ENGINEERING_BONUS_PER_LEVEL.get())
        );

        // ===== 僚机进化模块 (wingman_evolution_module) =====
        register("wingman_evolution_module_items",
                new ParamDef("bonus_per_level", nameKey("wingman_evolution_module_items", "bonus_per_level"),
                        Config::getWingmanEvolutionBonusPerLevel)
        );

        // ===== 僚机纳米再生模块 (wingman_nano_regen_module) =====
        register("wingman_nano_regen_module_items",
                new ParamDef("regen_percent", nameKey("wingman_nano_regen_module_items", "regen_percent"),
                        Config::getWingmanNanoRegenPercent)
        );

        // ===== 生命护盾转化 (conversion) =====
        register("conversion_items",
                new ParamDef("conversion_ratio", nameKey("conversion_items", "conversion_ratio"),
                        () -> Config.CONVERSION_RATIO.get())
        );

        // ===== 结界 (barrier) =====
        register("barrier_items",
                new ParamDef("barrier_max_damage", nameKey("barrier_items", "barrier_max_damage"),
                        () -> Config.BARRIER_MAX_DAMAGE.get().doubleValue())
        );

        // ===== 护盾移植 (shield_transfer) =====
        register("shield_transfer_items",
                new ParamDef("penalty_per_entity", nameKey("shield_transfer_items", "penalty_per_entity"),
                        () -> Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get())
        );

        // ===== 电能释放 (electric_discharge) =====
        register("electric_discharge_items",
                new ParamDef("burn_duration", nameKey("electric_discharge_items", "burn_duration"),
                        () -> Config.getElectricDischargeBurnDuration()),
                new ParamDef("burn_charge", nameKey("electric_discharge_items", "burn_charge"),
                        Config::getElectricDischargeBurnCharge)
        );

        // ===== 湮灭护盾 (explosive_shield) =====
        register("explosive_shield_items",
                new ParamDef("radius", nameKey("explosive_shield_items", "radius"),
                        () -> Config.EXPLOSIVE_SHIELD_RADIUS.get()),
                new ParamDef("base_damage", nameKey("explosive_shield_items", "base_damage"),
                        () -> Config.EXPLOSIVE_SHIELD_DAMAGE.get())
        );

        // ===== 武装护盾 (weaponized_shield) =====
        register("weaponized_shield_items",
                new ParamDef("radius", nameKey("weaponized_shield_items", "radius"),
                        () -> Config.WEAPONIZED_SHIELD_RADIUS.get()),
                new ParamDef("vulnerability", nameKey("weaponized_shield_items", "vulnerability"),
                        () -> Config.WEAPONIZED_SHIELD_VULNERABILITY.get().doubleValue())
        );

        // ===== 濒死保护 (near_death_protection) =====
        register("near_death_protection_items",
                new ParamDef("invincible_duration", nameKey("near_death_protection_items", "invincible_duration"),
                        () -> Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get()),
                new ParamDef("cooldown", nameKey("near_death_protection_items", "cooldown"),
                        () -> Config.NEAR_DEATH_PROTECTION_COOLDOWN.get())
        );

        // ===== 自毁装置 (self_destruct) =====
        register("self_destruct_items",
                new ParamDef("base_damage", nameKey("self_destruct_items", "base_damage"),
                        () -> Config.SELF_DESTRUCT_BASE_DAMAGE.get()),
                new ParamDef("base_radius", nameKey("self_destruct_items", "base_radius"),
                        () -> Config.SELF_DESTRUCT_BASE_RADIUS.get())
        );

        // ===== 弹射物次级爆炸 (projectile_explosion) =====
        register("projectile_explosion_items",
                new ParamDef("damage_fraction", nameKey("projectile_explosion_items", "damage_fraction"),
                        () -> Config.SECONDARY_EXPLOSION_DAMAGE_FRACTION.get()),
                new ParamDef("radius_base", nameKey("projectile_explosion_items", "radius_base"),
                        () -> Config.SECONDARY_EXPLOSION_RADIUS_BASE.get()),
                new ParamDef("radius_per_damage", nameKey("projectile_explosion_items", "radius_per_damage"),
                        () -> Config.SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION.get())
        );

        // ===== 反制脉冲 (counter_pulse) =====
        register("counter_pulse_items",
                new ParamDef("explosion_radius", nameKey("counter_pulse_items", "explosion_radius"),
                        () -> Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get()),
                new ParamDef("explosion_damage", nameKey("counter_pulse_items", "explosion_damage"),
                        () -> Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get().doubleValue()),
                new ParamDef("cooldown", nameKey("counter_pulse_items", "cooldown"),
                        () -> Config.COUNTER_PULSE_COOLDOWN.get())
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
                        () -> 100.0),
                new ParamDef("base_health", nameKey("drone_module_items", "base_health"),
                        Config::getDroneBaseHealth),
                new ParamDef("base_damage", nameKey("drone_module_items", "base_damage"),
                        Config::getDroneBaseDamage),
                new ParamDef("base_count", nameKey("drone_module_items", "base_count"),
                        () -> (double) Config.getDroneMaxCount()),
                // 0 = 未定义、沿用 Config 默认；此处默认值取 Config 真实值，编辑器“未覆盖”显示与运行时一致
                new ParamDef("attack_interval", nameKey("drone_module_items", "attack_interval"),
                        () -> Config.DRONE_ATTACK_INTERVAL.get()),
                new ParamDef("move_speed_multiplier", nameKey("drone_module_items", "move_speed_multiplier"),
                        () -> 1.0),
                new ParamDef("attack_range", nameKey("drone_module_items", "attack_range"),
                        () -> Config.DRONE_ATTACK_RANGE.get()),
                new ParamDef("target_range", nameKey("drone_module_items", "target_range"),
                        () -> Config.DRONE_TARGET_RANGE.get())
        );

        // ===== 蜂群构建模块 (swarm_module，实例化机制：每个来源物品（母舰机身）一个蜂群实例) =====
        register("swarm_module_items",
                new ParamDef("build_time", nameKey("swarm_module_items", "build_time"),
                        Config::getSwarmBuildTime),
                new ParamDef("base_health", nameKey("swarm_module_items", "base_health"),
                        Config::getSwarmBaseHealth),
                new ParamDef("base_damage", nameKey("swarm_module_items", "base_damage"),
                        Config::getSwarmBaseDamage),
                new ParamDef("base_count", nameKey("swarm_module_items", "base_count"),
                        () -> (double) Config.getSwarmMaxCount()),
                // 0 = 未定义、沿用 Config 默认；此处默认值取 Config 真实值，编辑器“未覆盖”显示与运行时一致
                new ParamDef("attack_interval", nameKey("swarm_module_items", "attack_interval"),
                        Config::getSwarmAttackInterval),
                new ParamDef("attack_range", nameKey("swarm_module_items", "attack_range"),
                        Config::getSwarmAttackRange),
                new ParamDef("search_range", nameKey("swarm_module_items", "search_range"),
                        Config::getSwarmSearchRange),
                new ParamDef("move_speed_multiplier", nameKey("swarm_module_items", "move_speed_multiplier"),
                        () -> 1.0)
        );

        // ===== 僚机构建模块 (wingman_module，实例化机制：每个来源物品一个僚机实例) =====
        register("wingman_module_items",
                new ParamDef("build_time", nameKey("wingman_module_items", "build_time"),
                        () -> 500.0),
                new ParamDef("base_health", nameKey("wingman_module_items", "base_health"),
                        Config::getWingmanBaseHealth),
                new ParamDef("base_damage", nameKey("wingman_module_items", "base_damage"),
                        Config::getWingmanExplosiveDamage),
                new ParamDef("base_count", nameKey("wingman_module_items", "base_count"),
                        () -> (double) Config.getWingmanMaxCount()),
                new ParamDef("attack_interval", nameKey("wingman_module_items", "attack_interval"),
                        Config::getWingmanAttackInterval),
                new ParamDef("attack_range", nameKey("wingman_module_items", "attack_range"),
                        Config::getWingmanAttackRange)
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
            "reflect_damage_items", "drone_module_items", "swarm_module_items", "wingman_module_items");

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
}
