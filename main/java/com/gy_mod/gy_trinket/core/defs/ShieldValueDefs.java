package com.gy_mod.gy_trinket.core.defs;

import com.gy_mod.gy_trinket.config.Config;

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
 * 不跨物品合并），未覆盖回退此处默认值。基础四项的显示默认值优先取护盾类型定义
 * （{@link DefsManager#clientShieldTypeParamDefault}），此处硬编码仅为回退。
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
        register("aura", withBase(
                new ParamDef("trigger_frequency", nameKey("aura", "trigger_frequency"),
                        () -> Config.AURA_TRIGGER_FREQUENCY.get()),
                new ParamDef("radius", nameKey("aura", "radius"),
                        () -> Config.AURA_RADIUS.get()),
                new ParamDef("damage", nameKey("aura", "damage"),
                        () -> Config.AURA_DAMAGE.get()),
                new ParamDef("shield_cost", nameKey("aura", "shield_cost"),
                        () -> Config.AURA_SHIELD_COST.get())
        ));

        // ===== 虹吸护盾 (siphon) =====
        register("siphon", withBase(
                new ParamDef("tick_interval", nameKey("siphon", "tick_interval"),
                        () -> Config.SIPHON_TICK_INTERVAL.get()),
                new ParamDef("radius", nameKey("siphon", "radius"),
                        () -> Config.SIPHON_RADIUS.get()),
                new ParamDef("damage", nameKey("siphon", "damage"),
                        () -> Config.SIPHON_DAMAGE.get()),
                new ParamDef("heal_ratio", nameKey("siphon", "heal_ratio"),
                        () -> Config.SIPHON_HEAL_RATIO.get()),
                new ParamDef("duration_ticks", nameKey("siphon", "duration_ticks"),
                        () -> Config.SIPHON_DURATION_TICKS.get()),
                new ParamDef("effect_per_stack", nameKey("siphon", "effect_per_stack"),
                        () -> Config.SIPHON_EFFECT_PER_STACK.get()),
                new ParamDef("max_effect", nameKey("siphon", "max_effect"),
                        () -> Config.SIPHON_MAX_EFFECT.get()),
                new ParamDef("decay_ratio", nameKey("siphon", "decay_ratio"),
                        () -> Config.SIPHON_DECAY_RATIO.get())
        ));

        // ===== 反射护盾 (reflect) =====
        register("reflect", withBase(
                new ParamDef("speed_base_modifier", nameKey("reflect", "speed_base_modifier"),
                        Config::getReflectSpeedBaseModifier),
                new ParamDef("speed_extra_modifier", nameKey("reflect", "speed_extra_modifier"),
                        Config::getReflectSpeedExtraModifier),
                new ParamDef("damage_effect_multiplier", nameKey("reflect", "damage_effect_multiplier"),
                        Config::getReflectDamageEffectMultiplier)
        ));

        // ===== 增幅护盾 (amplification) =====
        register("amplification", withBase(
                new ParamDef("base_amplification", nameKey("amplification", "base_amplification"),
                        Config::getAmplificationBaseAmplification),
                new ParamDef("threat_amplification", nameKey("amplification", "threat_amplification"),
                        Config::getAmplificationThreatAmplification),
                new ParamDef("health_amplification_per_point", nameKey("amplification", "health_amplification_per_point"),
                        Config::getAmplificationHealthAmplificationPerPoint),
                new ParamDef("max_amplification", nameKey("amplification", "max_amplification"),
                        Config::getAmplificationMaxAmplification),
                new ParamDef("check_radius", nameKey("amplification", "check_radius"),
                        Config::getAmplificationCheckRadius),
                new ParamDef("movement_speed_bonus", nameKey("amplification", "movement_speed_bonus"),
                        Config::getAmplificationMovementSpeedBonus)
        ));

        // ===== 跃迁护盾 (warp) =====
        register("warp", withBase(
                new ParamDef("warp_distance", nameKey("warp", "warp_distance"),
                        () -> Config.WARP_SHIELD_WARP_DISTANCE.get()),
                new ParamDef("explosion_radius", nameKey("warp", "explosion_radius"),
                        () -> Config.WARP_SHIELD_EXPLOSION_RADIUS.get()),
                new ParamDef("explosion_damage", nameKey("warp", "explosion_damage"),
                        () -> Config.WARP_SHIELD_EXPLOSION_DAMAGE.get()),
                new ParamDef("invincible_duration", nameKey("warp", "invincible_duration"),
                        () -> Config.WARP_SHIELD_INVINCIBLE_DURATION.get())
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
}
