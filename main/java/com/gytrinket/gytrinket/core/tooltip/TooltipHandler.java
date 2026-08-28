package com.gytrinket.gytrinket.core.tooltip;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBullet;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "gytrinket", bus = EventBusSubscriber.Bus.GAME)
public class TooltipHandler {

    private static final String TOOLTIP_PREFIX = "tooltip.gytrinket.";

    // 数据驱动的工具提示规则列表（从 datapack tooltip_rules registry 惰性加载）
    private static List<TooltipConfig> tooltipRules = null;

    private static void ensureTooltipRules() {
        if (tooltipRules != null) {
            return;
        }
        tooltipRules = new ArrayList<>();
        for (DefsManager.TooltipRuleDef def : DefsManager.clientTooltipRules(getClientRegistryAccess())) {
            tooltipRules.add(new TooltipConfig(def));
        }
        gytrinket.LOGGER.info("工具提示规则已加载：{} 条", tooltipRules.size());
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        net.minecraft.resources.ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) {
            return;
        }

        String itemId = itemKey.toString();

        // 特殊工具提示（逻辑独特，保持原有方法）
        addItemAttributesTooltip(event, itemId);
        addShieldTypesTooltip(event, itemId);
        addModuleTooltips(event, itemId);
        addAdaptiveArmorTooltip(event, itemId);

        // 数据驱动的通用工具提示（机制详细描述：超过 1 个机制时折叠只剩标题，按 Shift 展开）
        ensureTooltipRules();
        List<TooltipConfig> matchedRules = new ArrayList<>();
        for (TooltipConfig config : tooltipRules) {
            if (config.matchesItem(itemId)) {
                matchedRules.add(config);
            }
        }
        boolean collapse = matchedRules.size() > 1 && !net.minecraft.client.gui.screens.Screen.hasShiftDown();
        for (TooltipConfig config : matchedRules) {
            addConfiguredTooltip(event, itemId, config, collapse);
        }
    }

    /**
     * 通用的配置驱动工具提示处理方法
     * 替代所有重复的 add*Tooltip 方法
     * @param collapse 多个特殊机制时折叠：只显示标题行（详细描述在按住 Shift 时展开）
     */
    private static void addConfiguredTooltip(ItemTooltipEvent event, String itemId, TooltipConfig config, boolean collapse) {
        if (!config.matchesItem(itemId)) {
            return;
        }

        if (collapse) {
            if (config.hasTitle()) {
                addTooltip(event, config.getTitleKey(), config.getTitleColor());
            }
            return;
        }

        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));

        if (config.hasTitle()) {
            addTooltip(event, config.getTitleKey(), config.getTitleColor());
        }

        if (config.needsFormatting()) {
            addFormattedTooltip(event, config.getDescriptionKey(), ChatFormatting.GRAY, config.getFormatter());
        } else if (config.getDescriptionKey() != null) {
            addTooltip(event, config.getDescriptionKey(), ChatFormatting.GRAY);
        }
    }

    private static void addItemAttributesTooltip(ItemTooltipEvent event, String itemId) {
        List<? extends String> itemAttributesConfig = Config.ITEM_ATTRIBUTES_CONFIG.get();

        for (String configLine : itemAttributesConfig) {
            if (configLine.startsWith(itemId + "|")) {
                String attributesPart = configLine.substring(itemId.length() + 1);
                String[] attrPairs = attributesPart.split("\\|");

                event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.literal("属性:").withStyle(ChatFormatting.GOLD));

                for (String attrPair : attrPairs) {
                    String[] parts = attrPair.split("=");
                    if (parts.length == 2) {
                        String attrName = parts[0];
                        String attrValue = parts[1];

                        Component attrTooltip = Component.translatable(TOOLTIP_PREFIX + "attr." + attrName)
                            .withStyle(ChatFormatting.WHITE);

                        if (isDefaultTranslation(attrTooltip, TOOLTIP_PREFIX + "attr." + attrName)) {
                            attrTooltip = Component.literal(attrName).withStyle(ChatFormatting.WHITE);
                        }

                        event.getToolTip().add(Component.literal("  +").withStyle(ChatFormatting.GREEN)
                            .append(attrTooltip)
                            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(formatAttributeValue(attrName, attrValue)).withStyle(ChatFormatting.YELLOW)));
                    }
                }
                break;
            }
        }
    }

    /**
     * 格式化属性值显示
     * - 百分比/独立乘区属性：值×100，显示为百分数（最多保留两位小数，四舍五入）
     * - 常规属性（BASE）：最多保留两位小数，四舍五入
     */
    private static String formatAttributeValue(String attrName, String rawValue) {
        try {
            double value = Double.parseDouble(rawValue);
            AttributeType type = getAttributeType(attrName);

            if (type == AttributeType.PERCENT || type == AttributeType.INDEPENDENT_MULTIPLY) {
                // 百分比显示：值×100，最多保留两位小数
                return formatDecimal(value * 100) + "%";
            } else {
                // 常规小数：最多保留两位小数
                return formatDecimal(value);
            }
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    /**
     * 四舍五入到两位小数，并去掉末尾多余的0和小数点
     * 示例：8.00->"8", 8.50->"8.5", 16.6666->"16.67", 20.01->"20.01"
     */
    private static String formatDecimal(double value) {
        String formatted = String.format("%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    /**
     * 查询属性类型
     * 优先从AttributeManager查询，后备从属性名后缀推断
     */
    private static AttributeType getAttributeType(String attrName) {
        AttributeDefinition def = AttributeManager.getAttributeDefinition(attrName);
        if (def != null) {
            return def.getType();
        }
        AttributeType clientType = DefsManager.clientAttributeType(getClientRegistryAccess(), attrName);
        if (clientType != null) {
            return clientType;
        }
        if (attrName.endsWith("_percent")) return AttributeType.PERCENT;
        if (attrName.endsWith("_independent")) return AttributeType.INDEPENDENT_MULTIPLY;
        return AttributeType.BASE;
    }

    private static void addShieldTypesTooltip(ItemTooltipEvent event, String itemId) {
        List<String> shieldTypes = DefsManager.clientItemShieldTypes(getClientRegistryAccess(), itemId);
        if (shieldTypes.isEmpty()) {
            return;
        }

        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.literal("护盾类型:").withStyle(ChatFormatting.GOLD));

        for (String type : shieldTypes) {
            Component typeTooltip = Component.translatable(TOOLTIP_PREFIX + "shield_type." + type)
                .withStyle(ChatFormatting.WHITE);

            if (isDefaultTranslation(typeTooltip, TOOLTIP_PREFIX + "shield_type." + type)) {
                typeTooltip = Component.literal(type).withStyle(ChatFormatting.WHITE);
            }

            event.getToolTip().add(Component.literal("  +").withStyle(ChatFormatting.GREEN)
                .append(typeTooltip));

            addShieldTypeDescriptionTooltip(event, type);
        }
    }

    /**
     * 获取客户端当前的注册表访问（用于读取同步到客户端的定义类数据）
     */
    private static net.minecraft.core.RegistryAccess getClientRegistryAccess() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    private static void addShieldTypeDescriptionTooltip(ItemTooltipEvent event, String type) {
        String descKey = TOOLTIP_PREFIX + "shield_type." + type + "_desc";
        Component descTooltip = Component.translatable(descKey);

        if (!isDefaultTranslation(descTooltip, descKey)) {
            String formattedDesc = descTooltip.getString();
            try {
                if (type.equals("aura")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        Config.getIgniteDefaultDamage(),
                        Config.getIgniteDefaultDuration());
                } else if (type.equals("reflect")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        (int)(Config.getReflectSpeedBaseModifier() * 100),
                        Config.getReflectDamageEffectMultiplier());
                } else if (type.equals("amplification")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        (int)(Config.getAmplificationBaseAmplification() * 100),
                        (int)(Config.getAmplificationMaxAmplification() * 100),
                        (int)(Config.getAmplificationMovementSpeedBonus() * 100));
                } else if (type.equals("warp")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        Config.getWarpShieldExplosionDamage());
                } else if (type.equals("siphon")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        Config.SIPHON_TICK_INTERVAL.get(),
                        Config.SIPHON_DAMAGE.get(),
                        (int)(Config.SIPHON_HEAL_RATIO.get() * 100),
                        (int)(Config.SIPHON_MAX_EFFECT.get() * 100));
                }
                event.getToolTip().add(Component.literal("    ").append(Component.literal(formattedDesc).withStyle(ChatFormatting.GRAY)));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal("    ").append(descTooltip.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private static void addModuleTooltips(ItemTooltipEvent event, String itemId) {
        if (DefsManager.itemSetContains(getClientRegistryAccess(), "drone_module_items", itemId)) {
            addTooltip(event, "drone_module", ChatFormatting.GRAY);
            addDroneModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "assault_drone_module_items", itemId)) {
            addTooltip(event, "assault_drone_module", ChatFormatting.GOLD);
            addTooltip(event, "assault_drone_module_desc", ChatFormatting.RED);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "defense_drone_module_items", itemId)) {
            addTooltip(event, "defense_drone_module", ChatFormatting.BLUE);
            addTooltip(event, "defense_drone_module_desc", ChatFormatting.RED);
        }

        if (itemId.equals("gytrinket:quick_reconstruction_module")) {
            addTooltip(event, "quick_reconstruction_module", ChatFormatting.GREEN);
            addTooltip(event, "quick_reconstruction_module_desc", ChatFormatting.RED);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "wingman_module_items", itemId)) {
            addTooltip(event, "wingman_module", ChatFormatting.GRAY);
            addWingmanModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "wingman_interceptor_module_items", itemId)) {
            addTooltip(event, "wingman_interceptor_module", ChatFormatting.GRAY);
            addInterceptorModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "wingman_nano_regen_module_items", itemId)) {
            addTooltip(event, "wingman_nano_regen_module", ChatFormatting.GREEN);
            addNanoRegenModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "swarm_module_items", itemId)) {
            addTooltip(event, "swarm_module", ChatFormatting.GRAY);
            addMothershipBodyDescTooltip(event);
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "barrier_items", itemId)) {
            addTooltip(event, "barrier", ChatFormatting.DARK_PURPLE);
            addFormattedTooltip(event, "barrier_effect", ChatFormatting.DARK_PURPLE,
                () -> new Object[]{5, 5});
        }

        if (DefsManager.itemSetContains(getClientRegistryAccess(), "journey_module_items", itemId)) {
            addTooltip(event, "journey_module", ChatFormatting.GOLD);
            addJourneyModuleDescTooltip(event);
        }
    }

    /**
     * 征途模块描述工具提示（动态参数：最大层数、持续秒数、消退间隔/数量、攻速/移速每层加成）
     */
    private static void addJourneyModuleDescTooltip(ItemTooltipEvent event) {
        addFormattedTooltip(event, "journey_module_desc", ChatFormatting.GRAY,
            () -> new Object[]{
                Config.getJourneyMaxStacks(),
                Config.getJourneyDurationTicks() / 20.0,
                Config.getJourneyDecayIntervalTicks(),
                Config.getJourneyDecayPerInterval(),
                Config.getJourneyAttackSpeedPerStack() * 100,
                Config.getJourneyMovementSpeedPerStack() * 100
            }
        );
    }

    /**
     * 无人机模块描述工具提示（需要特殊的动态参数计算）
     */
    private static void addDroneModuleDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "drone_module_desc";
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                ConstructType droneType = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
                int maxCount = droneType != null ? droneType.getMaxCount() : Config.getDroneMaxCount();
                double maxHealth = droneType != null ? droneType.getMaxHealth() : Config.getDroneBaseHealth();
                double attackSpeed = 1.0 / Config.ORBIT_ATTACK_INTERVAL.get();
                formattedText = String.format(formattedText,
                    maxCount, (int) maxHealth, DroneBullet.getBaseDamage(), formatDecimal(attackSpeed));
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * 僚机模块描述工具提示（需要特殊的动态参数计算）
     */
    private static void addWingmanModuleDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "wingman_module_desc";
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                ConstructType wingmanType = ConstructManager.getInstance().getConstructType(WingmanConstructTypes.WINGMAN);
                int maxCount = wingmanType != null ? wingmanType.getMaxCount() : Config.getWingmanMaxCount();
                double maxHealth = wingmanType != null ? wingmanType.getMaxHealth() : Config.getWingmanBaseHealth();
                int explosiveCount = Config.getWingmanExplosiveCount();
                double explosiveDamage = Config.getWingmanExplosiveDamage();
                double explosionDamage = Config.getWingmanExplosionDamage();
                double explosionRadius = Config.getWingmanExplosionRadius();
                double attackSpeed = 1.0 / Config.getWingmanAttackInterval();
                formattedText = String.format(formattedText,
                    maxCount, (int) maxHealth, explosiveCount, explosiveDamage,
                    explosionDamage, explosionRadius, formatDecimal(attackSpeed));
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * 拦截机模块描述工具提示
     */
    private static void addInterceptorModuleDescTooltip(ItemTooltipEvent event) {
        addTooltip(event, "interceptor_module_desc", ChatFormatting.GRAY);
    }

    /**
     * 纳米再生模块描述工具提示
     */
    private static void addNanoRegenModuleDescTooltip(ItemTooltipEvent event) {
        addFormattedTooltip(event, "nano_regen_module_desc", ChatFormatting.GRAY,
            () -> new Object[]{Config.getWingmanNanoRegenPercent() * 100}
        );
    }

    /**
     * 母舰机身描述工具提示（需要特殊的动态参数计算）
     */
    private static void addMothershipBodyDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "mothership_body_desc";
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                ConstructType swarmType = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
                int maxCount = swarmType != null ? swarmType.getMaxCount() : Config.getSwarmMaxCount();
                double maxHealth = swarmType != null ? swarmType.getMaxHealth() : Config.getSwarmBaseHealth();
                double damage = Config.getSwarmBaseDamage();
                double attackSpeed = 1.0 / Config.getSwarmAttackInterval();
                double attackRange = Config.getSwarmAttackRange();
                formattedText = String.format(formattedText,
                    maxCount, maxHealth, damage, formatDecimal(attackSpeed), attackRange);
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void addAdaptiveArmorTooltip(ItemTooltipEvent event, String itemId) {
        boolean isAdaptiveArmorItem = DefsManager.itemSetContains(getClientRegistryAccess(), "adaptive_armor_items", itemId);
        boolean isBondItem = DefsManager.itemSetContains(getClientRegistryAccess(), "adaptive_armor_shield_effect_items", itemId);

        if (isAdaptiveArmorItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_enabled", ChatFormatting.GREEN);
            addTooltip(event, "adaptive_armor_effect", ChatFormatting.GRAY);
        }

        if (isBondItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_shield_effect", ChatFormatting.LIGHT_PURPLE);
            addTooltip(event, "adaptive_armor_bond_effect", ChatFormatting.GRAY);
        }
    }

    /**
     * 带格式化参数的工具提示
     */
    private static void addFormattedTooltip(ItemTooltipEvent event, String key, ChatFormatting color,
                                            TooltipFormatter formatter) {
        String translationKey = TOOLTIP_PREFIX + key;
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                formattedText = String.format(formattedText, formatter.formatParameters());
                event.getToolTip().add(Component.literal(formattedText).withStyle(color));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(color));
            }
        }
    }

    private static void addTooltip(ItemTooltipEvent event, String key, ChatFormatting color) {
        String translationKey = TOOLTIP_PREFIX + key;
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            event.getToolTip().add(tooltip.withStyle(color));
        }
    }

    private static boolean isDefaultTranslation(Component component, String key) {
        return component.getString().equals(key);
    }
}
