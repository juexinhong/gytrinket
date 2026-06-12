package com.gy_mod.gy_trinket.core.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 工具提示配置模型
 * 统一管理物品工具提示的显示规则
 */
public class TooltipConfig {

    private final ForgeConfigSpec.ConfigValue<List<? extends String>> itemConfig;
    private final String titleKey;
    private final String descriptionKey;
    private final ChatFormatting titleColor;
    private final boolean hasTitle;
    private final TooltipFormatter formatter;

    /**
     * 带标题和描述的工具提示配置（无格式化）
     */
    public TooltipConfig(ForgeConfigSpec.ConfigValue<List<? extends String>> itemConfig,
                         String titleKey, String descriptionKey,
                         ChatFormatting titleColor) {
        this(itemConfig, titleKey, descriptionKey, titleColor, null);
    }

    /**
     * 带标题和格式化描述的工具提示配置
     */
    public TooltipConfig(ForgeConfigSpec.ConfigValue<List<? extends String>> itemConfig,
                         String titleKey, String descriptionKey,
                         ChatFormatting titleColor,
                         TooltipFormatter formatter) {
        this.itemConfig = itemConfig;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.titleColor = titleColor;
        this.hasTitle = true;
        this.formatter = formatter;
    }

    /**
     * 仅格式化描述（无标题）的工具提示配置
     */
    public static TooltipConfig descriptionOnly(ForgeConfigSpec.ConfigValue<List<? extends String>> itemConfig,
                                                String descriptionKey,
                                                ChatFormatting titleColor,
                                                TooltipFormatter formatter) {
        TooltipConfig config = new TooltipConfig(itemConfig, null, descriptionKey, titleColor, formatter);
        return config;
    }

    public ForgeConfigSpec.ConfigValue<List<? extends String>> getItemConfig() {
        return itemConfig;
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
        return hasTitle && titleKey != null;
    }

    public boolean needsFormatting() {
        return formatter != null;
    }

    public TooltipFormatter getFormatter() {
        return formatter;
    }

    /**
     * 检查指定物品ID是否匹配此配置
     */
    public boolean matchesItem(String itemId) {
        return itemConfig.get().contains(itemId);
    }
}
