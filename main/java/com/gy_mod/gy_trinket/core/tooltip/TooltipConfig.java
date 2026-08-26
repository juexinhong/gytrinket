package com.gy_mod.gy_trinket.core.tooltip;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * 工具提示配置模型
 * 统一管理物品工具提示的显示规则
 *
 * 定义类数据（物品集合、工具提示规则）现由 datapack 提供：
 * - 物品集合匹配：持有系统名，从客户端同步后的 registryAccess 实时查询
 * - 规则定义：从 {@link DefsManager.TooltipRuleDef} 构建，格式化参数按类型解析（引用 Config 配置值）
 */
public class TooltipConfig {

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
                def.params().isEmpty() ? null : () -> resolveParams(def.params()));
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
     */
    private static Object[] resolveParams(List<DefsManager.TooltipParam> params) {
        Object[] result = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            DefsManager.TooltipParam p = params.get(i);
            result[i] = switch (p.type()) {
                case "value" -> Config.getValue(p.source());
                case "percentInt" -> (int) (((Number) Config.getValue(p.source())).doubleValue() * 100);
                case "percent" -> ((Number) Config.getValue(p.source())).doubleValue() * 100;
                case "seconds" -> ((Number) Config.getValue(p.source())).doubleValue() / 20.0;
                case "absPercentInt" -> (int) (Math.abs(((Number) Config.getValue(p.source())).doubleValue()) * 100);
                case "minusOnePercentInt" -> (int) ((((Number) Config.getValue(p.source())).doubleValue() - 1) * 100);
                case "literal" -> (int) Math.round(p.literal());
                case "text" -> p.text();
                default -> p.text();
            };
        }
        return result;
    }
}

