package com.gytrinket.gytrinket.core.tooltip;
import com.gytrinket.gytrinket.core.defs.DefsClientView;

import com.gytrinket.gytrinket.core.defs.DefsTypes;

import com.gytrinket.gytrinket.client.ClientItemAttributes;
import com.gytrinket.gytrinket.core.defs.MechanicValueDefs;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.engine.math.AttributeType;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.defs.ShieldValueDefs;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBullet;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
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
        List<DefsTypes.TooltipRuleDef> rules = DefsClientView.clientTooltipRules();
        // 数据源数量变化时重建缓存（datapack 可能晚于首次查询加载完成或 /reload 刷新）
        if (tooltipRules != null && tooltipRules.size() == rules.size()) {
            return;
        }
        tooltipRules = new ArrayList<>();
        for (DefsTypes.TooltipRuleDef def : rules) {
            tooltipRules.add(new TooltipConfig(def));
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        net.minecraft.resources.ResourceLocation itemKey = Registry.ITEM.getKey(stack.getItem());
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
                DefsClientView.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "attack_speed_multiplier", 1.0D),
                DefsClientView.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "orbit_speed_multiplier", 1.0D),
                DefsClientView.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "damage_multiplier", 1.0D),
                DefsClientView.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "attack_range_multiplier", 1.0D),
                DefsClientView.clientResolveMechanicValueForItem(id, "orbit_array_required_items",
                    "target_range_multiplier", 1.0D)
            }
        );
    }

    /** 判断物品是否声明了指定特殊机制集合（数据包声明或配置面板物品级覆盖均计入） */
    private static boolean hasSpecialMechanicSet(String itemId, String mechanicSet) {
        List<String> sets = DefsClientView.clientSpecialMechanicSets(itemId);
        if (sets.contains(mechanicSet)) {
            return true;
        }
        com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride ov =
                DefsClientView.getClientSpecialMechanicOverride(itemId);
        return ov != null && !ov.removed() && ov.values() != null && ov.values().containsKey(mechanicSet);
    }

    /**
     * 统计模块硬编码机制命中数（与 {@link #addModuleTooltips} 的判定一致，用于折叠决策）
     */
    private static int countModuleMechanicHits(String itemId) {
        int count = 0;
        if (DefsClientView.itemSetContains("drone_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("assault_drone_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("defense_drone_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("wingman_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("wingman_interceptor_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("wingman_nano_regen_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("swarm_module_items", itemId)) count++;
        if (DefsClientView.itemSetContains("barrier_items", itemId)) count++;
        if (DefsClientView.itemSetContains("journey_module_items", itemId)) count++;
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

        String descKey = config.selectDescriptionKey(itemId);
        if (config.needsFormatting()) {
            addFormattedTooltip(event, itemId, descKey, ChatFormatting.GRAY, config.getFormatter());
        } else if (descKey != null) {
            addTooltip(event, descKey, ChatFormatting.GRAY);
        }
    }

    private static void addItemAttributesTooltip(ItemTooltipEvent event, String itemId) {
        // 数据源 = 服务端下发的属性静态表，未同步时回退本地 item_definitions（避免必须先开一次配置界面）
        ItemAttributeConfig config = ClientItemAttributes.getItemAttributes(itemId);
        if (config == null || config.getAttributes().isEmpty()) {
            return;
        }

        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.literal("属性:").withStyle(ChatFormatting.GOLD));

        for (var entry : config.getAttributes().entrySet()) {
            String attrName = entry.getKey();

            Component attrTooltip = Component.translatable(TOOLTIP_PREFIX + "attr." + attrName)
                .withStyle(ChatFormatting.WHITE);

            if (isDefaultTranslation(attrTooltip, TOOLTIP_PREFIX + "attr." + attrName)) {
                attrTooltip = Component.literal(attrName).withStyle(ChatFormatting.WHITE);
            }

            event.getToolTip().add(Component.literal("  +").withStyle(ChatFormatting.GREEN)
                .append(attrTooltip)
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatAttributeValue(attrName, entry.getValue())).withStyle(ChatFormatting.YELLOW)));
        }
    }

    /**
     * 格式化属性值显示
     * - 百分比/独立乘区属性：值×100，显示为百分数（最多保留两位小数，四舍五入）
     * - 常规属性（BASE）：最多保留两位小数，四舍五入
     */
    private static String formatAttributeValue(String attrName, double rawValue) {
        double value = rawValue;
        AttributeType type = getAttributeType(attrName);

        if (type == AttributeType.PERCENT || type == AttributeType.INDEPENDENT_MULTIPLY) {
            // 百分比显示：值×100，最多保留两位小数
            return formatDecimal(value * 100) + "%";
        } else {
            // 常规小数：最多保留两位小数
            return formatDecimal(value);
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
        AttributeType clientType = DefsClientView.clientAttributeType(attrName);
        if (clientType != null) {
            return clientType;
        }
        if (attrName.endsWith("_percent")) return AttributeType.PERCENT;
        if (attrName.endsWith("_independent")) return AttributeType.INDEPENDENT_MULTIPLY;
        return AttributeType.BASE;
    }

    private static void addShieldTypesTooltip(ItemTooltipEvent event, String itemId) {
        List<String> shieldTypes = DefsClientView.clientItemShieldTypes(itemId);
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

            // 物品自带的四项基础属性参数（与运行时 resolveShieldParam 取值链同源）
            addShieldBaseParamsTooltip(event, type, itemId);

            addShieldTypeDescriptionTooltip(event, type, itemId);

            // 穿盾机制开启时在类型描述下追加提示行（物品级覆盖 pierce_through ≥ 0.5）
            addShieldPierceThroughLine(event, type, itemId);
        }
    }

    /** 护盾类型开启穿盾机制时，在描述下追加一行"护盾会被过穿" */
    private static void addShieldPierceThroughLine(ItemTooltipEvent event, String type, String itemId) {
        if (DefsClientView.clientShieldTypeValueForItem(itemId, type, "pierce_through", 0.0D) < 0.5D) {
            return;
        }
        event.getToolTip().add(Component.literal("    ")
                .append(Component.translatable(TOOLTIP_PREFIX + "shield_pierce_line"))
                .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 护盾物品自带的四项基础属性参数显示（shield_base / shield_cooldown_time /
     * shield_hit_cooldown_extend / shield_hit_cooldown_extend_multiplier）。
     * 数值经 {@link DefsManager#clientResolveShieldParam} 按物品实例解析（与运行时同源），
     * 未命中任何覆写/定义层时回退 {@link ShieldValueDefs.ParamDef#defaultValue()} 静态默认。
     */
    private static void addShieldBaseParamsTooltip(ItemTooltipEvent event, String type, String itemId) {
        for (ShieldValueDefs.ParamDef def : ShieldValueDefs.getBaseParams()) {
            double value = DefsClientView.clientResolveShieldParam(itemId, type, def.key(), def.defaultValue());

            MutableComponent nameTooltip = Component.translatable(def.nameKey());
            if (isDefaultTranslation(nameTooltip, def.nameKey())) {
                nameTooltip = Component.literal(def.key());
            }

            event.getToolTip().add(Component.literal("    ")
                .append(nameTooltip.withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatDecimal(value)).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static void addShieldTypeDescriptionTooltip(ItemTooltipEvent event, String type, String itemId) {
        String descKey = TOOLTIP_PREFIX + "shield_type." + type + "_desc";
        // 必须取语言表原始模板：translatable().getString() 会把 %s 占位符吞掉（见 rawTranslation 注释）
        String rawDesc = rawTranslation(descKey);
        if (rawDesc.equals(descKey)) {
            return;
        }
        String formattedDesc = rawDesc;
        try {
            // 数值来源与运行时 resolveShieldTypeValueForItem 一致：
            // 物品级覆盖值优先，未覆盖回退 Config 全局默认，保证描述与实际生效数值相同
            if (type.equals("aura")) {
                formattedDesc = String.format(rawDesc,
                    (int) DefsClientView.clientShieldTypeValueForItem(itemId, type, "trigger_frequency",
                        ShieldValueDefs.defaultValueOf("aura", "trigger_frequency", 5.0)),
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "radius",
                        ShieldValueDefs.defaultValueOf("aura", "radius", 3.5)),
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "damage",
                        ShieldValueDefs.defaultValueOf("aura", "damage", 0.75)),
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "shield_cost",
                        ShieldValueDefs.defaultValueOf("aura", "shield_cost", 0.042)),
                    Config.getIgniteDefaultDamage(),
                    Config.getIgniteDefaultDuration());
            } else if (type.equals("reflect")) {
                formattedDesc = String.format(rawDesc,
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "speed_base_modifier",
                        ShieldValueDefs.defaultValueOf("reflect", "speed_base_modifier", 1.5)) * 100),
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "damage_effect_multiplier",
                        ShieldValueDefs.defaultValueOf("reflect", "damage_effect_multiplier", 1.0)));
            } else if (type.equals("amplification")) {
                formattedDesc = String.format(rawDesc,
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "base_amplification",
                        ShieldValueDefs.defaultValueOf("amplification", "base_amplification", 0.2)) * 100),
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "max_amplification",
                        ShieldValueDefs.defaultValueOf("amplification", "max_amplification", 1.0)) * 100),
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "movement_speed_bonus",
                        ShieldValueDefs.defaultValueOf("amplification", "movement_speed_bonus", 0.2)) * 100));
            } else if (type.equals("warp")) {
                formattedDesc = String.format(rawDesc,
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "explosion_damage",
                        ShieldValueDefs.defaultValueOf("warp", "explosion_damage", 7.5)));
            } else if (type.equals("siphon")) {
                formattedDesc = String.format(rawDesc,
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "tick_interval",
                        ShieldValueDefs.defaultValueOf("siphon", "tick_interval", 5.0)),
                    DefsClientView.clientShieldTypeValueForItem(itemId, type, "damage",
                        ShieldValueDefs.defaultValueOf("siphon", "damage", 0.3)),
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "heal_ratio",
                        ShieldValueDefs.defaultValueOf("siphon", "heal_ratio", 0.3)) * 100),
                    (int)(DefsClientView.clientShieldTypeValueForItem(itemId, type, "max_effect",
                        ShieldValueDefs.defaultValueOf("siphon", "max_effect", 0.4)) * 100));
            }
        } catch (Exception e) {
            formattedDesc = rawDesc;
        }
        event.getToolTip().add(Component.literal("    ").append(Component.literal(formattedDesc).withStyle(ChatFormatting.GRAY)));
    }

    /** 模块硬编码机制 tooltip：折叠时只显示机制标题（描述按 Shift 展开） */
    private static void addModuleTooltips(ItemTooltipEvent event, String itemId, boolean collapse) {
        if (DefsClientView.itemSetContains("drone_module_items", itemId)) {
            addTooltip(event, "drone_module", ChatFormatting.GRAY);
            if (!collapse) addDroneModuleDescTooltip(event, itemId);
        }

        if (DefsClientView.itemSetContains("assault_drone_module_items", itemId)) {
            addTooltip(event, "assault_drone_module", ChatFormatting.GOLD);
            if (!collapse) addTooltip(event, "assault_drone_module_desc", ChatFormatting.RED);
        }

        if (DefsClientView.itemSetContains("defense_drone_module_items", itemId)) {
            addTooltip(event, "defense_drone_module", ChatFormatting.BLUE);
            if (!collapse) addTooltip(event, "defense_drone_module_desc", ChatFormatting.RED);
        }

        if (itemId.equals("gytrinket:quick_reconstruction_module")) {
            addTooltip(event, "quick_reconstruction_module", ChatFormatting.GREEN);
            if (!collapse) addTooltip(event, "quick_reconstruction_module_desc", ChatFormatting.RED);
        }

        if (DefsClientView.itemSetContains("wingman_module_items", itemId)) {
            addTooltip(event, "wingman_module", ChatFormatting.GRAY);
            if (!collapse) addWingmanModuleDescTooltip(event);
        }

        if (DefsClientView.itemSetContains("wingman_interceptor_module_items", itemId)) {
            addTooltip(event, "wingman_interceptor_module", ChatFormatting.GRAY);
            if (!collapse) addInterceptorModuleDescTooltip(event);
        }

        if (DefsClientView.itemSetContains("wingman_nano_regen_module_items", itemId)) {
            addTooltip(event, "wingman_nano_regen_module", ChatFormatting.GREEN);
            if (!collapse) addNanoRegenModuleDescTooltip(event, itemId);
        }

        if (DefsClientView.itemSetContains("swarm_module_items", itemId)) {
            addTooltip(event, "swarm_module", ChatFormatting.GRAY);
            if (!collapse) addMothershipBodyDescTooltip(event);
        }

        if (DefsClientView.itemSetContains("barrier_items", itemId)) {
            addTooltip(event, "barrier", ChatFormatting.DARK_PURPLE);
            if (!collapse) addFormattedTooltip(event, itemId, "barrier_effect", ChatFormatting.DARK_PURPLE,
                (id) -> {
                    double maxDamage = TooltipConfig.mechanicValue(id, "barrierMaxDamage", 5);
                    return new Object[]{(int) maxDamage, (int) maxDamage};
                });
        }

        if (DefsClientView.itemSetContains("journey_module_items", itemId)) {
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
        // 原始模板（translatable().getString() 会吞掉 %s 占位符）
        String raw = rawTranslation(translationKey);

        if (!raw.equals(translationKey)) {
            String formattedText = raw;
            try {
                // 未覆盖回退值需与运行时实例参数解析一致（DroneInstanceParams fallback）：均取 Config 默认
                double fallbackCount = Config.getDroneMaxCount();
                double fallbackHealth = MechanicValueDefs.defaultValueOf("drone_module_items", "base_health", 5);
                double fallbackDamage = MechanicValueDefs.defaultValueOf("drone_module_items", "base_damage", 0.3);
                double fallbackInterval = MechanicValueDefs.defaultValueOf("drone_module_items", "attack_interval", 0.5);
                // 物品级覆盖值优先（client 覆盖层），未覆盖回退上述默认（与运行时实例参数解析一致）
                double maxCount = DefsClientView.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_count", fallbackCount);
                double maxHealth = DefsClientView.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_health", fallbackHealth);
                double damage = DefsClientView.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "base_damage", fallbackDamage);
                double interval = DefsClientView.clientResolveMechanicValueForItem(
                        itemId, "drone_module_items", "attack_interval", fallbackInterval);
                if (interval <= 0.0D) interval = fallbackInterval;
                double attackSpeed = 1.0 / interval;
                formattedText = String.format(raw,
                    (int) maxCount, (int) maxHealth, damage, formatDecimal(attackSpeed));
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal(raw).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * 僚机模块描述工具提示（需要特殊的动态参数计算）
     */
    private static void addWingmanModuleDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "wingman_module_desc";
        String raw = rawTranslation(translationKey);

        if (!raw.equals(translationKey)) {
            String formattedText = raw;
            try {
                ConstructType wingmanType = ConstructManager.getInstance().getConstructType(WingmanConstructTypes.WINGMAN);
                int maxCount = wingmanType != null ? wingmanType.getMaxCount() : Config.getWingmanMaxCount();
                double maxHealth = wingmanType != null ? wingmanType.getMaxHealth() : MechanicValueDefs.defaultValueOf("wingman_module_items", "base_health", 24);
                int explosiveCount = Config.getWingmanExplosiveCount();
                double explosiveDamage = MechanicValueDefs.defaultValueOf("wingman_module_items", "base_damage", 0.5);
                double explosionDamage = Config.getWingmanExplosionDamage();
                double explosionRadius = Config.getWingmanExplosionRadius();
                double attackSpeed = 1.0 / MechanicValueDefs.defaultValueOf("wingman_module_items", "attack_interval", 1);
                formattedText = String.format(raw,
                    maxCount, (int) maxHealth, explosiveCount, explosiveDamage,
                    explosionDamage, explosionRadius, formatDecimal(attackSpeed));
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal(raw).withStyle(ChatFormatting.GRAY));
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
            (id) -> new Object[]{TooltipConfig.mechanicValue(id, "wingmanNanoRegenPercent", MechanicValueDefs.defaultValueOf("wingman_nano_regen_module_items", "regen_percent", 0.02)) * 100}
        );
    }

    /**
     * 母舰机身描述工具提示（需要特殊的动态参数计算）
     */
    private static void addMothershipBodyDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "mothership_body_desc";
        String raw = rawTranslation(translationKey);

        if (!raw.equals(translationKey)) {
            String formattedText = raw;
            try {
                ConstructType swarmType = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
                int maxCount = swarmType != null ? swarmType.getMaxCount() : Config.getSwarmMaxCount();
                double maxHealth = swarmType != null ? swarmType.getMaxHealth() : MechanicValueDefs.defaultValueOf("swarm_module_items", "base_health", 1.6);
                double damage = MechanicValueDefs.defaultValueOf("swarm_module_items", "base_damage", 0.08);
                double attackSpeed = 1.0 / MechanicValueDefs.defaultValueOf("swarm_module_items", "attack_interval", 1.5);
                double attackRange = MechanicValueDefs.defaultValueOf("swarm_module_items", "attack_range", 4);
                formattedText = String.format(raw,
                    maxCount, maxHealth, damage, formatDecimal(attackSpeed), attackRange);
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal(raw).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void addAdaptiveArmorTooltip(ItemTooltipEvent event, String itemId) {
        boolean isAdaptiveArmorItem = DefsClientView.itemSetContains("adaptive_armor_items", itemId);
        boolean isBondItem = DefsClientView.itemSetContains("adaptive_armor_shield_effect_items", itemId);

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

    /**
     * 读取语言表中的原始模板（不做参数替换）。
     * <p>
     * 注意：不能写作 {@code Component.translatable(key).getString()}——Minecraft 载入语言文件时会把
     * {@code %d}/{@code %.1f} 统一转换成 {@code %s}（Language.UNSUPPORTED_FORMAT_PATTERN），
     * 而无参数的 TranslatableContents 会把 {@code %s} 当成参数占位符“吞掉”，得到丢失数值的文本。
     * 需要自己 {@code String.format} 的模板一律从这里取。
     */
    private static String rawTranslation(String key) {
        return net.minecraft.locale.Language.getInstance().getOrDefault(key);
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
