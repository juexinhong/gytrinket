package com.gy_mod.gy_trinket.core.tooltip;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Map;

/**
 * 工具提示配置模型
 * 统一管理物品工具提示的显示规则
 *
 * 定义类数据（物品集合、工具提示规则）现由 datapack 提供：
 * - 物品集合匹配：持有系统名，从客户端同步后的 registryAccess 实时查询
 * - 规则定义：从 {@link DefsManager.TooltipRuleDef} 构建，格式化参数按类型解析（引用 Config 配置值）
 */
public class TooltipConfig {

    /**
     * Config 数值来源 → 机制参数映射（用于读取物品级机制数值覆盖）
     * key: Config.getValue() 的 source 名；value: [机制集名, 参数键名]
     */
    private static final Map<String, String[]> MECHANIC_VALUE_SOURCES = Map.ofEntries(
            Map.entry("wingmanEvolutionBonusPerLevel", new String[]{"wingman_evolution_module_items", "bonus_per_level"}),
            Map.entry("wingmanNanoRegenPercent", new String[]{"wingman_nano_regen_module_items", "regen_percent"}),
            Map.entry("conversionRatio", new String[]{"conversion_items", "conversion_ratio"}),
            Map.entry("barrierMaxDamage", new String[]{"barrier_items", "barrier_max_damage"}),
            Map.entry("shieldTransferEffectPenaltyPerEntity", new String[]{"shield_transfer_items", "penalty_per_entity"}),
            Map.entry("electricDischargeBurnDuration", new String[]{"electric_discharge_items", "burn_duration"}),
            Map.entry("explosiveShieldDamage", new String[]{"explosive_shield_items", "base_damage"}),
            Map.entry("weaponizedShieldVulnerability", new String[]{"weaponized_shield_items", "vulnerability"}),
            Map.entry("nearDeathProtectionInvincibleDuration", new String[]{"near_death_protection_items", "invincible_duration"}),
            Map.entry("nearDeathProtectionCooldown", new String[]{"near_death_protection_items", "cooldown"}),
            Map.entry("selfDestructBaseDamage", new String[]{"self_destruct_items", "base_damage"}),
            Map.entry("selfDestructBaseRadius", new String[]{"self_destruct_items", "base_radius"}),
            Map.entry("secondaryExplosionDamageFraction", new String[]{"projectile_explosion_items", "damage_fraction"}),
            Map.entry("secondaryExplosionRadiusBase", new String[]{"projectile_explosion_items", "radius_base"}),
            Map.entry("secondaryExplosionRadiusDamageFraction", new String[]{"projectile_explosion_items", "radius_per_damage"}),
            Map.entry("counterPulseCooldown", new String[]{"counter_pulse_items", "cooldown"}),
            Map.entry("counterPulseBaseExplosionRadius", new String[]{"counter_pulse_items", "explosion_radius"}),
            Map.entry("counterPulseBaseExplosionDamage", new String[]{"counter_pulse_items", "explosion_damage"}),
            Map.entry("precisionConstructBonusPerLevel", new String[]{"precision_construct_items", "bonus_per_level"}),
            Map.entry("advancedEngineeringBonusPerLevel", new String[]{"advanced_engineering_items", "bonus_per_level"}),
            Map.entry("ghostFuselageFullStealthTicks", new String[]{"ghost_fuselage_items", "full_stealth_ticks"}),
            Map.entry("ghostFuselageBaseMaxDamageBonus", new String[]{"ghost_fuselage_items", "base_max_damage_bonus"}),
            Map.entry("ghostFuselageDecayRate", new String[]{"ghost_fuselage_items", "decay_rate"}),
            Map.entry("ghostFuselageMinDecay", new String[]{"ghost_fuselage_items", "min_decay"}),
            Map.entry("ghostFuselageStealthSpeedBonusPerLevel", new String[]{"ghost_fuselage_items", "stealth_speed_bonus_per_level"}),
            Map.entry("chargedShieldChargeRatio", new String[]{"charged_shield_items", "charge_ratio"}),
            Map.entry("chargedShieldMaxBonus", new String[]{"charged_shield_items", "max_bonus"}),
            Map.entry("chargedShieldMovementSpeedPenalty", new String[]{"charged_shield_items", "move_speed_penalty"}),
            Map.entry("grudgeConversionRatio", new String[]{"grudge_items", "conversion_ratio"}),
            Map.entry("grudgeFadePercent", new String[]{"grudge_items", "fade_percent"}),
            Map.entry("grudgeFadeBase", new String[]{"grudge_items", "fade_base"}),
            Map.entry("grudgeMovementSpeedPenalty", new String[]{"grudge_items", "move_speed_penalty"})
    );

    private final String itemSetName;
    private final String titleKey;
    private final String descriptionKey;
    private final ChatFormatting titleColor;
    private final TooltipFormatter formatter;

    /**
     * 从数据驱动的工具提示规则构建
     */
    public TooltipConfig(DefsManager.TooltipRuleDef def) {
        this(def.itemSet(), def.titleKey(), def.descriptionKey(),
                ChatFormatting.getByName(def.color()),
                def.params().isEmpty() ? null : (id) -> resolveParams(id, def.params()));
    }

    /**
     * 带标题和描述的工具提示配置（无格式化）
     */
    public TooltipConfig(String itemSetName,
                         String titleKey, String descriptionKey,
                         ChatFormatting titleColor) {
        this(itemSetName, titleKey, descriptionKey, titleColor, null);
    }

    /**
     * 带标题和格式化描述的工具提示配置
     */
    public TooltipConfig(String itemSetName,
                         String titleKey, String descriptionKey,
                         ChatFormatting titleColor,
                         TooltipFormatter formatter) {
        this.itemSetName = itemSetName;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.titleColor = titleColor;
        this.formatter = formatter;
    }

    public String getItemSetName() {
        return itemSetName;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public ChatFormatting getTitleColor() {
        return titleColor;
    }

    public boolean hasTitle() {
        return titleKey != null;
    }

    public boolean needsFormatting() {
        return formatter != null;
    }

    public TooltipFormatter getFormatter() {
        return formatter;
    }

    /**
     * 检查指定物品ID是否匹配此配置（从 DefsManager 静态缓存查询，首次访问时客户端惰性加载）
     */
    public boolean matchesItem(String itemId) {
        return DefsManager.itemSetContains(itemSetName, itemId);
    }

    /**
     * 解析数据驱动的格式化参数
     * 类型见 {@link DefsManager.TooltipParam} 注释
     * 数值参数优先读取当前物品定义的机制数值覆盖，未覆盖时回退 Config 默认值
     */
    private static Object[] resolveParams(String itemId, List<DefsManager.TooltipParam> params) {
        Object[] result = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            DefsManager.TooltipParam p = params.get(i);
            result[i] = switch (p.type()) {
                case "value" -> rawValue(itemId, p.source());
                case "percentInt" -> (int) (rawValue(itemId, p.source()) * 100);
                case "percent" -> rawValue(itemId, p.source()) * 100;
                case "seconds" -> rawValue(itemId, p.source()) / 20.0;
                case "absPercentInt" -> (int) (Math.abs(rawValue(itemId, p.source())) * 100);
                case "minusOnePercentInt" -> (int) ((rawValue(itemId, p.source()) - 1) * 100);
                case "literal" -> (int) Math.round(p.literal());
                case "text" -> p.text();
                default -> p.text();
            };
        }
        return result;
    }

    /**
     * 解析数值参数：优先物品级机制覆盖值，未覆盖时回退 Config 默认值
     */
    private static double rawValue(String itemId, String source) {
        Object configValue = Config.getValue(source);
        double fallback = configValue instanceof Number number ? number.doubleValue() : 0.0D;
        return mechanicValue(itemId, source, fallback);
    }

    /**
     * 读取物品定义的机制数值覆盖
     * @return 覆盖值；无映射或未定义覆盖时返回 fallback
     */
    public static double mechanicValue(String itemId, String source, double fallback) {
        String[] mapping = MECHANIC_VALUE_SOURCES.get(source);
        if (mapping == null) {
            return fallback;
        }
        DefsManager.SpecialMechanicOverride override = DefsManager.getClientSpecialMechanicOverride(itemId);
        if (override == null || override.removed() || override.values() == null) {
            return fallback;
        }
        Map<String, DefsManager.ParamValue> params = override.values().get(mapping[0]);
        if (params == null) {
            return fallback;
        }
        DefsManager.ParamValue pv = params.get(mapping[1]);
        return pv != null ? pv.value() : fallback;
    }
}

