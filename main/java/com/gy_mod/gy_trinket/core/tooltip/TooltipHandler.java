package com.gy_mod.gy_trinket.core.tooltip;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeDefinition;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeType;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TooltipHandler {

    private static final String TOOLTIP_PREFIX = "tooltip.gytrinket.";

    // 数据驱动的工具提示规则列表（从 datapack tooltip_rules 惰性加载）
    private static List<TooltipConfig> tooltipRules = null;

    private static void ensureTooltipRules() {
        List<DefsManager.TooltipRuleDef> rules = DefsManager.clientTooltipRules();
        // 数据源数量变化时重建缓存（datapack 可能晚于首次查询加载完成或 /reload 刷新）
        if (tooltipRules != null && tooltipRules.size() == rules.size()) {
            return;
        }
        tooltipRules = new ArrayList<>();
        for (DefsManager.TooltipRuleDef def : rules) {
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
        addAdaptiveArmorTooltip(event, itemId);

        // 机制折叠决策：统计 tooltip_rules 匹配项 + 模块硬编码机制命中数，超过 1 个机制时折叠只剩标题
        ensureTooltipRules();
        List<TooltipConfig> matchedRules = new ArrayList<>();
        for (TooltipConfig config : tooltipRules) {
            if (config.matchesItem(itemId)) {
                matchedRules.add(config);
            }
        }
        boolean collapse = (matchedRules.size() + countModuleMechanicHits(itemId)) > 1
                && !net.minecraft.client.gui.screens.Screen.hasShiftDown();

        addModuleTooltips(event, itemId, collapse);
        for (TooltipConfig config : matchedRules) {
            addConfiguredTooltip(event, itemId, config, collapse);
        }

        // 环绕阵列特殊机制效果句（无 datapack tooltip rule，配置面板可给任意物品添加；与其他阵列一样用一句描述）
        addOrbitArrayEffectTooltip(event, itemId, collapse);

        // 代币物品自定义描述（config 随机构建的 tokenItemDescription 附属配置项）
        addTokenItemDescriptionTooltip(event, itemId);
    }

    /**
     * 代币物品自定义描述：config 随机构建（构建池）的代币附属配置项 tokenItemDescription，
     * 可随意填写文本；非空且当前物品与配置的代币物品 ID 一致时，
     * 以特殊机制描述的样式（空行分隔 + 灰色文本）追加显示。
     */
    private static void addTokenItemDescriptionTooltip(ItemTooltipEvent event, String itemId) {
        String desc = Config.getRandomBuildTokenItemDescription();
        if (desc == null || desc.isBlank()) {
            return;
        }
        String tokenItemId = Config.getRandomBuildTokenItemId();
        if (tokenItemId == null || !itemId.equalsIgnoreCase(tokenItemId.trim())) {
            return;
        }
        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
        for (String line : desc.split("\n")) {
            if (!line.isBlank()) {
                event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * 环绕阵列特殊机制描述：像其他阵列特殊机制一样，用带动态倍率的一句话描述。
     * 覆盖值来自该物品自己的定义（clientResolveMechanicValueForItem），未覆盖时回退 MechanicValueDefs 默认（1.0）。
     */
    private static void addOrbitArrayEffectTooltip(ItemTooltipEvent event, String itemId, boolean collapse) {
        if (!hasSpecialMechanicSet(itemId, "orbit_array_required_items")) {
            return;
        }
        if (collapse) {
            addTooltip(event, "orbit_array_required", ChatFormatting.GOLD);
            return;
        }
        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
        addTooltip(event, "orbit_array_required", ChatFormatting.GOLD);
        addFormattedTooltip(event, itemId, "orbit_array_effect", ChatFormatting.GRAY,
            (id) -> new Object[]{
                DefsManager.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "attack_speed_multiplier", 1.0D),
                DefsManager.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "orbit_speed_multiplier", 1.0D),
                DefsManager.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "damage_multiplier", 1.0D),
                DefsManager.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "attack_range_multiplier", 1.0D),
                DefsManager.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "target_range_multiplier", 1.0D)
            }
        );
    }

    /** 判断物品是否声明了指定特殊机制集合（数据包声明或配置面板物品级覆盖均计入） */
    private static boolean hasSpecialMechanicSet(String itemId, String mechanicSet) {
        List<String> sets = DefsManager.clientSpecialMechanicSets(itemId);
        if (sets.contains(mechanicSet)) {
            return true;
        }
        com.gy_mod.gy_trinket.core.defs.DefsManager.SpecialMechanicOverride ov =
                DefsManager.getClientSpecialMechanicOverride(itemId);
        return ov != null && !ov.removed() && ov.values() != null && ov.values().containsKey(mechanicSet);
    }

    /**
     * 统计模块硬编码机制命中数（与 {@link #addModuleTooltips} 的判定一致，用于折叠决策）
     */
    private static int countModuleMechanicHits(String itemId) {
        int count = 0;
        if (DefsManager.itemSetContains("drone_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("assault_drone_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("defense_drone_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("wingman_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("wingman_interceptor_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("wingman_nano_regen_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("swarm_module_items", itemId)) count++;
        if (DefsManager.itemSetContains("barrier_items", itemId)) count++;
        if (DefsManager.itemSetContains("journey_module_items", itemId)) count++;
        if (itemId.equals("gytrinket:quick_reconstruction_module")) count++;
        return count;
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
            addFormattedTooltip(event, itemId, config.getDescriptionKey(), ChatFormatting.GRAY, config.getFormatter());
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
        AttributeType clientType = DefsManager.clientAttributeType(attrName);
        if (clientType != null) {
            return clientType;
        }
        if (attrName.endsWith("_percent")) return AttributeType.PERCENT;
        if (attrName.endsWith("_independent")) return AttributeType.INDEPENDENT_MULTIPLY;
        return AttributeType.BASE;
    }

    private static void addShieldTypesTooltip(ItemTooltipEvent event, String itemId) {
        List<String> shieldTypes = DefsManager.clientItemShieldTypes(itemId);
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

            addShieldTypeDescriptionTooltip(event, type, itemId);

            // 穿盾机制开启时在类型描述下追加提示行（物品级覆盖 pierce_through ≥ 0.5）
            addShieldPierceThroughLine(event, type, itemId);
        }
    }

    /** 护盾类型开启穿盾机制时，在描述下追加一行"护盾会被过穿" */
    private static void addShieldPierceThroughLine(ItemTooltipEvent event, String type, String itemId) {
        if (DefsManager.clientShieldTypeValueForItem(itemId, type, "pierce_through", 0.0D) < 0.5D) {
            return;
        }
        event.getToolTip().add(Component.literal("    ")
                .append(Component.translatable(TOOLTIP_PREFIX + "shield_pierce_line"))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void addShieldTypeDescriptionTooltip(ItemTooltipEvent event, String type, String itemId) {
        String descKey = TOOLTIP_PREFIX + "shield_type." + type + "_desc";
        Component descTooltip = Component.translatable(descKey);

        if (!isDefaultTranslation(descTooltip, descKey)) {
            String formattedDesc = descTooltip.getString();
            try {
                // 数值来源与运行时 resolveShieldTypeValueForItem 一致：
                // 物品级覆盖值优先，未覆盖回退 Config 全局默认，保证描述与实际生效数值相同
                if (type.equals("aura")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        (int) DefsManager.clientShieldTypeValueForItem(itemId, type, "trigger_frequency",
                            Config.AURA_TRIGGER_FREQUENCY.get()),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "radius",
                            Config.AURA_RADIUS.get()),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "damage",
                            Config.AURA_DAMAGE.get()),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "shield_cost",
                            Config.AURA_SHIELD_COST.get()),
                        Config.getIgniteDefaultDamage(),
                        Config.getIgniteDefaultDuration());
                } else if (type.equals("reflect")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "speed_base_modifier",
                            Config.getReflectSpeedBaseModifier()) * 100),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "damage_effect_multiplier",
                            Config.getReflectDamageEffectMultiplier()));
                } else if (type.equals("amplification")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "base_amplification",
                            Config.getAmplificationBaseAmplification()) * 100),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "max_amplification",
                            Config.getAmplificationMaxAmplification()) * 100),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "movement_speed_bonus",
                            Config.getAmplificationMovementSpeedBonus()) * 100));
                } else if (type.equals("warp")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "explosion_damage",
                            Config.getWarpShieldExplosionDamage()));
                } else if (type.equals("siphon")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "tick_interval",
                            Config.SIPHON_TICK_INTERVAL.get()),
                        DefsManager.clientShieldTypeValueForItem(itemId, type, "damage",
                            Config.SIPHON_DAMAGE.get()),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "heal_ratio",
                            Config.SIPHON_HEAL_RATIO.get()) * 100),
                        (int)(DefsManager.clientShieldTypeValueForItem(itemId, type, "max_effect",
                            Config.SIPHON_MAX_EFFECT.get()) * 100));
                }
                event.getToolTip().add(Component.literal("    ").append(Component.literal(formattedDesc).withStyle(ChatFormatting.GRAY)));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal("    ").append(descTooltip.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    /** 模块硬编码机制 tooltip：折叠时只显示机制标题（描述按 Shift 展开） */
    private static void addModuleTooltips(ItemTooltipEvent event, String itemId, boolean collapse) {
        if (DefsManager.itemSetContains("drone_module_items", itemId)) {
            addTooltip(event, "drone_module", ChatFormatting.GRAY);
            if (!collapse) addDroneModuleDescTooltip(event, itemId);
        }

        if (DefsManager.itemSetContains("assault_drone_module_items", itemId)) {
            addTooltip(event, "assault_drone_module", ChatFormatting.GOLD);
            if (!collapse) addTooltip(event, "assault_drone_module_desc", ChatFormatting.RED);
        }

        if (DefsManager.itemSetContains("defense_drone_module_items", itemId)) {
            addTooltip(event, "defense_drone_module", ChatFormatting.BLUE);
            if (!collapse) addTooltip(event, "defense_drone_module_desc", ChatFormatting.RED);
        }

        if (itemId.equals("gytrinket:quick_reconstruction_module")) {
            addTooltip(event, "quick_reconstruction_module", ChatFormatting.GREEN);
            if (!collapse) addTooltip(event, "quick_reconstruction_module_desc", ChatFormatting.RED);
        }

        if (DefsManager.itemSetContains("wingman_module_items", itemId)) {
            addTooltip(event, "wingman_module", ChatFormatting.GRAY);
            if (!collapse) addWingmanModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains("wingman_interceptor_module_items", itemId)) {
            addTooltip(event, "wingman_interceptor_module", ChatFormatting.GRAY);
            if (!collapse) addInterceptorModuleDescTooltip(event);
        }

        if (DefsManager.itemSetContains("wingman_nano_regen_module_items", itemId)) {
            addTooltip(event, "wingman_nano_regen_module", ChatFormatting.GREEN);
            if (!collapse) addNanoRegenModuleDescTooltip(event, itemId);
        }

        if (DefsManager.itemSetContains("swarm_module_items", itemId)) {
            addTooltip(event, "swarm_module", ChatFormatting.GRAY);
            if (!collapse) addMothershipBodyDescTooltip(event);
        }

        if (DefsManager.itemSetContains("barrier_items", itemId)) {
            addTooltip(event, "barrier", ChatFormatting.DARK_PURPLE);
            if (!collapse) addFormattedTooltip(event, itemId, "barrier_effect", ChatFormatting.DARK_PURPLE,
                (id) -> {
                    double maxDamage = TooltipConfig.mechanicValue(id, "barrierMaxDamage", 5);
                    return new Object[]{(int) maxDamage, (int) maxDamage};
                });
        }

        if (DefsManager.itemSetContains("journey_module_items", itemId)) {
            addTooltip(event, "journey_module", ChatFormatting.GOLD);
            if (!collapse) addJourneyModuleDescTooltip(event, itemId);
        }
    }

    /**
     * 征途模块描述工具提示（动态参数：最大层数、持续秒数、消退间隔/数量、攻速/移速每层加成）
     */
    private static void addJourneyModuleDescTooltip(ItemTooltipEvent event, String itemId) {
        addFormattedTooltip(event, itemId, "journey_module_desc", ChatFormatting.GRAY,
            (id) -> new Object[]{
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
     * 无人机模块描述工具提示（需要特殊的动态参数计算）。
     * 数值来源与运行时 DroneInstanceParams 一致：物品级覆盖值优先，未覆盖回退类型/Config 默认，
     * 保证描述显示与实际生效数值相同（参考护盾类型描述实现方式）。
     */
    private static void addDroneModuleDescTooltip(ItemTooltipEvent event, String itemId) {
        String translationKey = TOOLTIP_PREFIX + "drone_module_desc";
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                // 未覆盖回退值需与运行时实例参数解析一致（DroneInstanceParams fallback）：均取 Config 默认
                double fallbackCount = Config.getDroneMaxCount();
                double fallbackHealth = Config.getDroneBaseHealth();
                double fallbackDamage = Config.getDroneBaseDamage();
                double fallbackInterval = Config.DRONE_ATTACK_INTERVAL.get();
                // 物品级覆盖值优先（client 覆盖层），未覆盖回退上述默认（与运行时实例参数解析一致）
                double maxCount = DefsManager.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_count", fallbackCount);
                double maxHealth = DefsManager.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_health", fallbackHealth);
                double damage = DefsManager.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_damage", fallbackDamage);
                double interval = DefsManager.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "attack_interval", fallbackInterval);
                if (interval <= 0.0D) interval = fallbackInterval;
                double attackSpeed = 1.0 / interval;
                formattedText = String.format(formattedText,
                    (int) maxCount, (int) maxHealth, damage, formatDecimal(attackSpeed));
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
    private static void addNanoRegenModuleDescTooltip(ItemTooltipEvent event, String itemId) {
        addFormattedTooltip(event, itemId, "nano_regen_module_desc", ChatFormatting.GRAY,
            (id) -> new Object[]{TooltipConfig.mechanicValue(id, "wingmanNanoRegenPercent", Config.getWingmanNanoRegenPercent()) * 100}
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
        boolean isAdaptiveArmorItem = DefsManager.itemSetContains("adaptive_armor_items", itemId);
        boolean isBondItem = DefsManager.itemSetContains("adaptive_armor_shield_effect_items", itemId);

        if (isAdaptiveArmorItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_enabled", ChatFormatting.GREEN);
            addTooltip(event, "adaptive_armor_effect", ChatFormatting.GRAY);
        }

        if (isBondItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_bond", ChatFormatting.LIGHT_PURPLE);
            addTooltip(event, "adaptive_armor_bond_effect", ChatFormatting.GRAY);
        }
    }

    /**
     * 带格式化参数的工具提示
     * 直接使用 Component.translatable(key, args) 让Minecraft翻译系统完成格式化
     * 避免 getString() + String.format() 两步处理导致的 %% 和 %s 冲突问题
     */
    private static void addFormattedTooltip(ItemTooltipEvent event, String itemId, String key, ChatFormatting color,
                                            TooltipFormatter formatter) {
        String translationKey = TOOLTIP_PREFIX + key;
        try {
            Object[] args = formatter.formatParameters(itemId);
            MutableComponent tooltip = Component.translatable(translationKey, args);
            if (!isDefaultTranslation(tooltip, translationKey)) {
                event.getToolTip().add(tooltip.withStyle(color));
            }
        } catch (Exception e) {
            MutableComponent tooltip = Component.translatable(translationKey);
            if (!isDefaultTranslation(tooltip, translationKey)) {
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
