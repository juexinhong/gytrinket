package com.gy_mod.gy_trinket.core.defs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 护盾类型数值参数注册表（编译期静态定义）。
 * <p>
 * 定义配置面板可为每个护盾类型编辑的物品级数值参数：参数键（存储/网络传输用）、
 * 显示翻译键、默认值提供者。覆盖全部 6 种护盾类型（none / aura / siphon / reflect /
 * amplification / warp），每类均前置四项基础属性参数（shield_base / shield_cooldown_time /
 * shield_hit_cooldown_extend / shield_hit_cooldown_extend_multiplier，翻译键共享 base 前缀），
 * 随后是该类型的行为参数。
 * <p>
 * 覆盖值由 UI 产生，存储在 {@link DefsManager.ShieldTypeOverride} 的 values 段；
 * 运行时读取走 {@link DefsManager#resolveShieldTypeValueForItem}（服务端，按物品实例取值，
 * 不跨物品合并）。该方法的取值顺序为：item_definitions 物品级 shieldValues（数据文件 /
 * UI 覆写）→ shield_types 类型级默认值 → 此处默认值。光环护盾的四项行为参数已完整迁移到
 * item_definitions（逐物品声明），Config 不再提供对应全局项。
 */
public final class ShieldValueDefs {

    /** 单个护盾参数：参数键 + 显示翻译键 + 默认值提供者 */
    public record ParamDef(String key, String nameKey, DoubleSupplier defaultSupplier) {
        public double defaultValue() { return defaultSupplier.getAsDouble(); }
    }

    private static final Map<String, List<ParamDef>> PARAMS = new LinkedHashMap<>();

    /** 四项基础属性参数（全部类型共享，翻译键统一走 base 前缀）；默认值为保守回退，显示优先取类型定义 */
    private static final ParamDef[] BASE_PARAMS = {
            new ParamDef("shield_base", nameKey("base", "shield_base"), () -> 6.0),
            new ParamDef("shield_cooldown_time", nameKey("base", "shield_cooldown_time"), () -> 6.5),
            new ParamDef("shield_hit_cooldown_extend", nameKey("base", "shield_hit_cooldown_extend"), () -> 40.0),
            new ParamDef("shield_hit_cooldown_extend_multiplier",
                    nameKey("base", "shield_hit_cooldown_extend_multiplier"), () -> 0.1)
    };

    /**
     * 光环护盾行为参数默认值。
     * <p>
     * 这些数值已迁移到物品定义（item_definitions 的 {@code shieldValues} 段，逐物品声明），
     * Config 中不再提供全局项；此处仅作为"物品与护盾类型都未声明该键"时的最终回退，
     * 数值与迁移前 Config 默认值一致。
     */
    private static final double AURA_TRIGGER_FREQUENCY = 5.0;
    private static final double AURA_RADIUS = 3.5;
    private static final double AURA_DAMAGE = 0.75;
    private static final double AURA_SHIELD_COST = 0.042;

    /** 虹吸护盾行为参数默认值（已迁移到 item_definitions，Config 全局项已移除） */
    private static final double SIPHON_TICK_INTERVAL = 5.0;
    private static final double SIPHON_RADIUS = 4.0;
    private static final double SIPHON_DAMAGE = 0.3;
    private static final double SIPHON_HEAL_RATIO = 0.3;
    private static final double SIPHON_DURATION_TICKS = 20.0;
    private static final double SIPHON_EFFECT_PER_STACK = 0.025;
    private static final double SIPHON_MAX_EFFECT = 0.4;
    private static final double SIPHON_DECAY_RATIO = 0.03;

    /** 反射护盾行为参数默认值（已迁移到 item_definitions，Config 全局项已移除） */
    private static final double REFLECT_SPEED_BASE_MODIFIER = 1.5;
    private static final double REFLECT_SPEED_EXTRA_MODIFIER = 1.0;
    private static final double REFLECT_DAMAGE_EFFECT_MULTIPLIER = 1.0;

    /** 增幅护盾行为参数默认值（已迁移到 item_definitions，Config 全局项已移除） */
    private static final double AMPLIFICATION_BASE = 0.2;
    private static final double AMPLIFICATION_THREAT = 0.2;
    private static final double AMPLIFICATION_HEALTH_PER_POINT = 0.02;
    private static final double AMPLIFICATION_MAX = 1.0;
    private static final double AMPLIFICATION_CHECK_RADIUS = 5.0;
    private static final double AMPLIFICATION_MOVEMENT_SPEED_BONUS = 0.2;

    /** 跃迁护盾行为参数默认值（已迁移到 item_definitions，Config 全局项已移除） */
    private static final double WARP_DISTANCE = 5.0;
    private static final double WARP_EXPLOSION_RADIUS = 3.3;
    private static final double WARP_EXPLOSION_DAMAGE = 7.5;
    private static final double WARP_INVINCIBLE_DURATION = 15.0;

    private static void register(String shieldType, ParamDef... defs) {
        PARAMS.put(shieldType, List.of(defs));
    }

    /** 四项基础属性参数定义（tooltip 显示复用，编辑器展示顺序一致） */
    public static ParamDef[] getBaseParams() {
        return BASE_PARAMS;
    }

    /** 基础四项 + 类型行为参数合并（基础项在前，编辑器优先展示） */
    private static ParamDef[] withBase(ParamDef... effectParams) {
        ParamDef[] all = new ParamDef[BASE_PARAMS.length + effectParams.length];
        System.arraycopy(BASE_PARAMS, 0, all, 0, BASE_PARAMS.length);
        System.arraycopy(effectParams, 0, all, BASE_PARAMS.length, effectParams.length);
        return all;
    }

    /** 生成护盾参数的显示翻译键：gui.gytrinket.shield_value.&lt;护盾类型&gt;.&lt;参数键&gt; */
    private static String nameKey(String shieldType, String key) {
        return "gui.gytrinket.shield_value." + shieldType + "." + key;
    }

    static {
        // ===== 基础护盾 (none)：仅四项基础属性，无行为参数 =====
        register("none", withBase());

        // ===== 光环护盾 (aura) =====
        // 数值已迁移到 item_definitions 物品级 shieldValues；此处仅为未声明物品的最终回退
        register("aura", withBase(
                new ParamDef("trigger_frequency", nameKey("aura", "trigger_frequency"),
                        () -> AURA_TRIGGER_FREQUENCY),
                new ParamDef("radius", nameKey("aura", "radius"),
                        () -> AURA_RADIUS),
                new ParamDef("damage", nameKey("aura", "damage"),
                        () -> AURA_DAMAGE),
                new ParamDef("shield_cost", nameKey("aura", "shield_cost"),
                        () -> AURA_SHIELD_COST)
        ));

        // ===== 虹吸护盾 (siphon) =====
        // 数值已迁移到 item_definitions 物品级 shieldValues
        register("siphon", withBase(
                new ParamDef("tick_interval", nameKey("siphon", "tick_interval"),
                        () -> SIPHON_TICK_INTERVAL),
                new ParamDef("radius", nameKey("siphon", "radius"),
                        () -> SIPHON_RADIUS),
                new ParamDef("damage", nameKey("siphon", "damage"),
                        () -> SIPHON_DAMAGE),
                new ParamDef("heal_ratio", nameKey("siphon", "heal_ratio"),
                        () -> SIPHON_HEAL_RATIO),
                new ParamDef("duration_ticks", nameKey("siphon", "duration_ticks"),
                        () -> SIPHON_DURATION_TICKS),
                new ParamDef("effect_per_stack", nameKey("siphon", "effect_per_stack"),
                        () -> SIPHON_EFFECT_PER_STACK),
                new ParamDef("max_effect", nameKey("siphon", "max_effect"),
                        () -> SIPHON_MAX_EFFECT),
                new ParamDef("decay_ratio", nameKey("siphon", "decay_ratio"),
                        () -> SIPHON_DECAY_RATIO)
        ));

        // ===== 反射护盾 (reflect) =====
        // 数值已迁移到 item_definitions 物品级 shieldValues
        register("reflect", withBase(
                new ParamDef("speed_base_modifier", nameKey("reflect", "speed_base_modifier"),
                        () -> REFLECT_SPEED_BASE_MODIFIER),
                new ParamDef("speed_extra_modifier", nameKey("reflect", "speed_extra_modifier"),
                        () -> REFLECT_SPEED_EXTRA_MODIFIER),
                new ParamDef("damage_effect_multiplier", nameKey("reflect", "damage_effect_multiplier"),
                        () -> REFLECT_DAMAGE_EFFECT_MULTIPLIER)
        ));

        // ===== 增幅护盾 (amplification) =====
        // 数值已迁移到 item_definitions 物品级 shieldValues
        register("amplification", withBase(
                new ParamDef("base_amplification", nameKey("amplification", "base_amplification"),
                        () -> AMPLIFICATION_BASE),
                new ParamDef("threat_amplification", nameKey("amplification", "threat_amplification"),
                        () -> AMPLIFICATION_THREAT),
                new ParamDef("health_amplification_per_point", nameKey("amplification", "health_amplification_per_point"),
                        () -> AMPLIFICATION_HEALTH_PER_POINT),
                new ParamDef("max_amplification", nameKey("amplification", "max_amplification"),
                        () -> AMPLIFICATION_MAX),
                new ParamDef("check_radius", nameKey("amplification", "check_radius"),
                        () -> AMPLIFICATION_CHECK_RADIUS),
                new ParamDef("movement_speed_bonus", nameKey("amplification", "movement_speed_bonus"),
                        () -> AMPLIFICATION_MOVEMENT_SPEED_BONUS)
        ));

        // ===== 跃迁护盾 (warp) =====
        // 数值已迁移到 item_definitions 物品级 shieldValues
        register("warp", withBase(
                new ParamDef("warp_distance", nameKey("warp", "warp_distance"),
                        () -> WARP_DISTANCE),
                new ParamDef("explosion_radius", nameKey("warp", "explosion_radius"),
                        () -> WARP_EXPLOSION_RADIUS),
                new ParamDef("explosion_damage", nameKey("warp", "explosion_damage"),
                        () -> WARP_EXPLOSION_DAMAGE),
                new ParamDef("invincible_duration", nameKey("warp", "invincible_duration"),
                        () -> WARP_INVINCIBLE_DURATION)
        ));
    }

    private ShieldValueDefs() {}

    /** 获取护盾类型可编辑的参数列表（未注册的类型返回空列表） */
    public static List<ParamDef> getParams(String shieldType) {
        return PARAMS.getOrDefault(shieldType, List.of());
    }

    /** 获取参数默认值（未注册的类型/参数返回 null） */
    public static Double getDefaultValue(String shieldType, String paramKey) {
        for (ParamDef def : getParams(shieldType)) {
            if (def.key().equals(paramKey)) {
                return def.defaultValue();
            }
        }
        return null;
    }

    /**
     * 获取参数默认值，未注册时返回调用方给定的回退值。
     * <p>
     * 供运行时/ tooltip 在"物品定义与护盾类型都未声明该参数"时取最终回退，
     * 避免直接依赖 Config 中的全局数值项。
     */
    public static double defaultValueOf(String shieldType, String paramKey, double fallback) {
        Double value = getDefaultValue(shieldType, paramKey);
        return value != null ? value : fallback;
    }
}
