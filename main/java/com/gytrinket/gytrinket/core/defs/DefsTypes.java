package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义类数据模型（datapack JSON 的结构化映射）。
 * <p>
 * P5-5c 从 {@link DefsManager} 拆出：这里是纯数据 + Codec，不含加载/查询逻辑。
 * 定义文件路径（详见 {@link DefsManager}）：
 * <pre>
 *   gytrinket/item_sets、shield_types、item_shield_types、attribute_definitions、
 *   item_dependencies、module_trees、upgrade_paths、tooltip_rules、special_mechanics、item_definitions
 * </pre>
 */
public final class DefsTypes {

    private static final String REGISTRY_NAMESPACE = gytrinket.MODID;

    // ===== registry 键（保留，兼容引用；Forge 1.20.1 中不参与实际注册） =====
    public static final ResourceKey<Registry<ItemSetDef>> ITEM_SETS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_sets"));
    public static final ResourceKey<Registry<ShieldTypeDef>> SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "shield_types"));
    public static final ResourceKey<Registry<ItemShieldTypeDef>> ITEM_SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_shield_types"));
    public static final ResourceKey<Registry<AttributeDefs>> ATTRIBUTE_DEFS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "attribute_definitions"));
    public static final ResourceKey<Registry<ItemDependencyDef>> ITEM_DEPENDENCIES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_dependencies"));
    public static final ResourceKey<Registry<ModuleTreeDef>> MODULE_TREES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "module_trees"));
    public static final ResourceKey<Registry<UpgradePathDef>> UPGRADE_PATHS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "upgrade_paths"));
    public static final ResourceKey<Registry<TooltipRuleDef>> TOOLTIP_RULES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "tooltip_rules"));

    private DefsTypes() {}

    /** 物品集合条目：{ "items": [...], "entities": [...] }，条目 id = 系统名 */
    public record ItemSetDef(List<String> items, List<String> entities) {
        static final Codec<ItemSetDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(ItemSetDef::items),
                Codec.STRING.listOf().optionalFieldOf("entities", List.of()).forGetter(ItemSetDef::entities)
        ).apply(inst, ItemSetDef::new));
    }

    /**
     * 护盾类型条目：{ "compatible": true|false, "shieldValues": { 基础参数默认值 } }，条目 id = 类型名。
     * <p>
     * shieldValues 携带四项基础属性的类型级默认值（shield_base / shield_cooldown_time /
     * shield_hit_cooldown_extend / shield_hit_cooldown_extend_multiplier）：物品 shieldValues
     * 未定义的键回退到类型默认值，再回退调用方全局聚合（无护盾类型物品基础属性全局生效）。
     */
    public record ShieldTypeDef(boolean compatible, Map<String, ParamValue> shieldValues) {
        static final Codec<ShieldTypeDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("compatible", true).forGetter(ShieldTypeDef::compatible),
                Codec.unboundedMap(Codec.STRING, ParamValue.CODEC).optionalFieldOf("shieldValues", Map.of())
                        .forGetter(ShieldTypeDef::shieldValues)
        ).apply(inst, ShieldTypeDef::new));
    }

    /** 物品->护盾类型条目：{ "item": "...", "types": [...] } */
    public record ItemShieldTypeDef(String item, List<String> types) {
        static final Codec<ItemShieldTypeDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("item").forGetter(ItemShieldTypeDef::item),
                Codec.STRING.listOf().fieldOf("types").forGetter(ItemShieldTypeDef::types)
        ).apply(inst, ItemShieldTypeDef::new));
    }

    /** 属性定义条目：{ "name": "...", "combine": "...", "group": "..." } */
    public record AttributeEntry(String name, String combine, String group) {
        static final Codec<AttributeEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("name").forGetter(AttributeEntry::name),
                Codec.STRING.fieldOf("combine").forGetter(AttributeEntry::combine),
                Codec.STRING.optionalFieldOf("group", "").forGetter(AttributeEntry::group)
        ).apply(inst, AttributeEntry::new));
    }

    /** 属性定义集合条目：{ "attributes": [...] }，多个文件条目合并加载 */
    public record AttributeDefs(List<AttributeEntry> attributes) {
        static final Codec<AttributeDefs> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                AttributeEntry.CODEC.listOf().fieldOf("attributes").forGetter(AttributeDefs::attributes)
        ).apply(inst, AttributeDefs::new));
    }

    /**
     * 禁用/依赖条目：{ "item": "...", "disables": [...], "dependsOn": [...], "disablesCategories": [...], "dependsOnAll": [[...],[...]] }
     * disables          -- 互斥：装备 item 时，列表中已装备的目标被禁用（双方同时装备时）
     * dependsOn         -- OR 依赖：装备 item 需要列表中"任意一个"依赖已装备且未被禁用
     * disablesCategories-- 类别禁用：装备 item 时，整个类别（如 shields）的物品全部被禁用
     * dependsOnAll      -- AND 依赖（OR 组）：外层列表 = 必须全部满足的组，每组内"任意一个"满足即可；
     *                      组内元素支持物品 id 或类别引用 "category:xxx"（如 category:construct_final）
     */
    public record ItemDependencyDef(String item, List<String> disables, List<String> dependsOn,
                                    List<String> disablesCategories, List<List<String>> dependsOnAll) {
        static final Codec<ItemDependencyDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("item").forGetter(ItemDependencyDef::item),
                Codec.STRING.listOf().optionalFieldOf("disables", List.of()).forGetter(ItemDependencyDef::disables),
                Codec.STRING.listOf().optionalFieldOf("dependsOn", List.of()).forGetter(ItemDependencyDef::dependsOn),
                Codec.STRING.listOf().optionalFieldOf("disablesCategories", List.of()).forGetter(ItemDependencyDef::disablesCategories),
                Codec.STRING.listOf().listOf().optionalFieldOf("dependsOnAll", List.of()).forGetter(ItemDependencyDef::dependsOnAll)
        ).apply(inst, ItemDependencyDef::new));
    }

    /**
     * 模块树条目：{ "category": "...", "tiers": [[一阶...],[二阶...],[终阶...]] }
     * category -- 树所属类别（如 construct 构造体类）
     * tiers    -- 按阶数排列的模块分组（每层内为并列/抉择模块），最后一层为该树的终阶模块
     */
    public record ModuleTreeDef(String category, List<List<String>> tiers) {
        static final Codec<ModuleTreeDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("category", "").forGetter(ModuleTreeDef::category),
                Codec.STRING.listOf().listOf().optionalFieldOf("tiers", List.of()).forGetter(ModuleTreeDef::tiers)
        ).apply(inst, ModuleTreeDef::new));
    }

    /** 升级路径条目：{ "base": "...", "upgrades": [...] }，条目 id = 基础物品路径 */
    public record UpgradePathDef(String base, List<String> upgrades) {
        static final Codec<UpgradePathDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("base").forGetter(UpgradePathDef::base),
                Codec.STRING.listOf().fieldOf("upgrades").forGetter(UpgradePathDef::upgrades)
        ).apply(inst, UpgradePathDef::new));
    }

    /**
     * 工具提示参数条目：
     * type 取值：
     *   value        -- 直接使用配置值（保留 int/double 类型）
     *   int          -- 配置值四舍五入取整（对应语言文件的 %d）
     *   percentInt   -- 配置值×100 取整（%d 或 %s 显示）
     *   percent      -- 配置值×100（double）
     *   seconds      -- 配置值÷20（double）
     *   absPercentInt-- |配置值|×100 取整
     *   minusOnePercentInt -- (配置值-1)×100 取整
     *   literal      -- 固定数值（取整为 int）
     *   text         -- 固定字符串
     */
    public record TooltipParam(String type, String source, String text, Double literal) {
        static final Codec<TooltipParam> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("type").forGetter(TooltipParam::type),
                Codec.STRING.optionalFieldOf("source", "").forGetter(TooltipParam::source),
                Codec.STRING.optionalFieldOf("text", "").forGetter(TooltipParam::text),
                Codec.DOUBLE.optionalFieldOf("literal", 0.0).forGetter(TooltipParam::literal)
        ).apply(inst, TooltipParam::new));
    }

    /** 工具提示规则条目：{ "itemSet": "...", "titleKey": "...", "descKey": "...", "color": "...", "params": [...], "branches": [...] } */
    public record TooltipRuleDef(String itemSet, String titleKey, String descriptionKey, String color, List<TooltipParam> params, List<TooltipBranch> branches) {
        static final Codec<TooltipRuleDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("itemSet").forGetter(TooltipRuleDef::itemSet),
                Codec.STRING.fieldOf("titleKey").forGetter(TooltipRuleDef::titleKey),
                Codec.STRING.fieldOf("descKey").forGetter(TooltipRuleDef::descriptionKey),
                Codec.STRING.fieldOf("color").forGetter(TooltipRuleDef::color),
                TooltipParam.CODEC.listOf().optionalFieldOf("params", List.of()).forGetter(TooltipRuleDef::params),
                TooltipBranch.CODEC.listOf().optionalFieldOf("branches", List.of()).forGetter(TooltipRuleDef::branches)
        ).apply(inst, TooltipRuleDef::new));
    }

    /** 描述分支：物品机制参数（Config source 名）的覆盖值等于 value 时，描述改用该键（用于按转化方向等参数切换文案） */
    public record TooltipBranch(String paramName, double value, String descriptionKey) {
        static final Codec<TooltipBranch> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("param").forGetter(TooltipBranch::paramName),
                Codec.DOUBLE.fieldOf("value").forGetter(TooltipBranch::value),
                Codec.STRING.fieldOf("descKey").forGetter(TooltipBranch::descriptionKey)
        ).apply(inst, TooltipBranch::new));
    }

    /**
     * 特殊机制条目（路径定义 + 分类声明二合一）：
     * 条目 id（文件名）= 物品 id，文件内容 {"sets":[...]} 声明该物品所属的机制分类；
     * 内容可省略为 {}（仅声明特殊机制，不参与任何分类）。
     */
    public record SpecialMechanicDef(List<String> sets, boolean removed) {
        static final Codec<SpecialMechanicDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("sets", List.of()).forGetter(SpecialMechanicDef::sets),
                Codec.BOOL.optionalFieldOf("removed", false).forGetter(SpecialMechanicDef::removed)
        ).apply(inst, SpecialMechanicDef::new));
    }

    /** 机制参数覆盖值：数值 + 是否可叠加（false = 各物品独立比对取最大，true = 多物品求和） */
    public record ParamValue(double value, boolean stackable) {
        /** 全写形式 {"value":...,"stackable":...} */
        private static final Codec<ParamValue> RECORD_CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("value").forGetter(ParamValue::value),
                Codec.BOOL.optionalFieldOf("stackable", true).forGetter(ParamValue::stackable)
        ).apply(inst, ParamValue::new));
        /** 双形式兼容：数据文件可写简写数值 {@code "shield_base": 6.0}，或全写 {@code {"value":6.0,"stackable":false}}；编码时按 stackable 选择形式 */
        public static final Codec<ParamValue> CODEC = Codec.either(
                Codec.DOUBLE.xmap(ParamValue::of, ParamValue::value),
                RECORD_CODEC)
                .xmap(e -> e.map(v -> v, v -> v),
                        v -> v.stackable()
                                ? com.mojang.datafixers.util.Either.<ParamValue, ParamValue>left(v)
                                : com.mojang.datafixers.util.Either.<ParamValue, ParamValue>right(v));
        public static ParamValue of(double value) { return new ParamValue(value, true); }
    }

    /** 特殊机制覆盖条目：removed=true 表示撤销声明。
     *  values：机制集合名 -> (参数键 -> ParamValue)，仅 UI 编辑产生；无覆盖的参数回退 Config 默认值（视为不可叠加）。
     *  同机制多物品合并：可叠加求和、不可叠加各自比对，最终值 = max(可叠加和, 最大的不可叠加项) */
    public record SpecialMechanicOverride(List<String> sets, boolean removed,
                                          Map<String, Map<String, ParamValue>> values) {
        public SpecialMechanicOverride(List<String> sets, boolean removed) {
            this(sets, removed, Map.of());
        }
        public static SpecialMechanicOverride removedState() { return new SpecialMechanicOverride(List.of(), true, Map.of()); }
        public static SpecialMechanicOverride declared(List<String> sets) { return new SpecialMechanicOverride(sets, false, Map.of()); }
        public static SpecialMechanicOverride declared(List<String> sets, Map<String, Map<String, ParamValue>> values) {
            return new SpecialMechanicOverride(sets, false,
                    values == null ? Map.of() : Collections.unmodifiableMap(values));
        }
    }

    /** 护盾类型覆盖条目（UI 编辑产生）；护盾全部按实例生效，独占/兼容设置已取消。
     *  values：护盾类型 -> (参数键 -> 覆盖值)，仅 UI 编辑产生；护盾类型数值为"每物品实例独立"，
     *  不参与叠/单合并，无覆盖的参数回退 Config 默认值 */
    public record ShieldTypeOverride(List<String> types,
                                     Map<String, Map<String, Double>> values) {
    }

    /**
     * 统一物品定义（item_definitions 数据文件 + 覆写层 items 段共用模型），一个物品一条。
     * <ul>
     *   <li>mechanic / removed 为布尔（恒有确定值，不做 null 合并）；</li>
     *   <li>sets / shieldTypes 为列表（null = 未覆盖，保留下层值；非 null = 整体覆盖）；</li>
     *   <li>shieldValues / attributes 按参数键合并（null = 未覆盖）；</li>
     *   <li>values 按机制集合键整体替换（null = 未覆盖；编辑某机制集合时提交该集合完整参数组）。</li>
     * </ul>
     * mechanic 为显式声明（40 机制物品与 24 护盾物品是不相交集合，"条目存在"≠"机制声明"）。
     */
    public record ItemDefinition(Boolean mechanic, boolean removed,
                                 List<String> sets, List<String> shieldTypes,
                                 Map<String, ParamValue> shieldValues,
                                 Map<String, Map<String, ParamValue>> values,
                                 Map<String, ParamValue> attributes) {
        static final Codec<ItemDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                nullable(Codec.BOOL, "mechanic").forGetter(i -> java.util.Optional.ofNullable(i.mechanic)),
                Codec.BOOL.optionalFieldOf("removed", false).forGetter(ItemDefinition::removed),
                nullable(Codec.STRING.listOf(), "sets").forGetter(i -> java.util.Optional.ofNullable(i.sets)),
                nullable(Codec.STRING.listOf(), "shieldTypes").forGetter(i -> java.util.Optional.ofNullable(i.shieldTypes)),
                nullable(Codec.unboundedMap(Codec.STRING, ParamValue.CODEC), "shieldValues").forGetter(i -> java.util.Optional.ofNullable(i.shieldValues)),
                nullable(Codec.unboundedMap(
                        Codec.STRING,
                        Codec.unboundedMap(Codec.STRING, ParamValue.CODEC)), "values").forGetter(i -> java.util.Optional.ofNullable(i.values)),
                nullable(Codec.unboundedMap(Codec.STRING, ParamValue.CODEC), "attributes").forGetter(i -> java.util.Optional.ofNullable(i.attributes))
        ).apply(inst, ItemDefinition::fromOptional));

        /**
         * nullable optional 字段：字段缺失 -> null（未覆盖语义），字段存在 -> 值。
         * DFU 不允许 DataResult 携带 null 中间态（RecordCodecBuilder ap 组合时 Optional.of(null) 直接 NPE），
         * 解码链用 Optional 承载"未覆盖"，经 fromOptional 构造时才落为 null。
         */
        private static <A> MapCodec<java.util.Optional<A>> nullable(Codec<A> codec, String name) {
            return codec.optionalFieldOf(name);
        }

        private static ItemDefinition fromOptional(java.util.Optional<Boolean> mechanic, boolean removed,
                                                   java.util.Optional<List<String>> sets, java.util.Optional<List<String>> shieldTypes,
                                                   java.util.Optional<Map<String, ParamValue>> shieldValues,
                                                   java.util.Optional<Map<String, Map<String, ParamValue>>> values,
                                                   java.util.Optional<Map<String, ParamValue>> attributes) {
            return new ItemDefinition(mechanic.orElse(null), removed, sets.orElse(null), shieldTypes.orElse(null),
                    shieldValues.orElse(null), values.orElse(null), attributes.orElse(null));
        }

        /** 全空定义（未覆盖任何字段） */
        public static ItemDefinition empty() {
            return new ItemDefinition(null, false, null, null, null, null, null);
        }

        /** UI 禁用态定义（removed=true，本模组对该物品无任何效果） */
        public static ItemDefinition removedState() {
            return new ItemDefinition(null, true, null, null, null, null, null);
        }

        /** 上层（base）与覆盖（ov）合并：标量/列表直接覆盖；shieldValues/attributes 按键合并；values 按机制集合键整体替换 */
        public static ItemDefinition merge(ItemDefinition base, ItemDefinition ov) {
            Boolean mechanic = ov.mechanic() != null ? ov.mechanic() : base.mechanic();
            boolean removed = base.removed() || ov.removed();
            List<String> sets = ov.sets() != null ? ov.sets() : base.sets();
            List<String> shieldTypes = ov.shieldTypes() != null ? ov.shieldTypes() : base.shieldTypes();
            Map<String, ParamValue> shieldValues = mergeParamKeyMap(base.shieldValues(), ov.shieldValues());
            Map<String, Map<String, ParamValue>> values = mergeValues(base.values(), ov.values());
            Map<String, ParamValue> attributes = mergeParamKeyMap(base.attributes(), ov.attributes());
            return new ItemDefinition(mechanic, removed, sets, shieldTypes, shieldValues, values, attributes);
        }

        /** 参数键映射合并：覆盖条目 putAll 到副本（null 安全） */
        private static Map<String, ParamValue> mergeParamKeyMap(Map<String, ParamValue> base, Map<String, ParamValue> ov) {
            if (base == null && ov == null) return null;
            Map<String, ParamValue> merged = new LinkedHashMap<>();
            if (base != null) merged.putAll(base);
            if (ov != null) merged.putAll(ov);
            return merged;
        }

        /** 机制数值合并：按机制集合键整体替换（UI"编辑机制 A 数值 = 提交 A 完整参数集"语义） */
        private static Map<String, Map<String, ParamValue>> mergeValues(Map<String, Map<String, ParamValue>> base,
                                                                        Map<String, Map<String, ParamValue>> ov) {
            if (base == null && ov == null) return null;
            Map<String, Map<String, ParamValue>> merged = new LinkedHashMap<>();
            if (base != null) merged.putAll(base);
            if (ov != null) merged.putAll(ov);
            return merged;
        }
    }

    /** 统一物品定义文件：{ "items": { 物品id: ItemDefinition } }，一文件可含多物品 */
    public record ItemDefinitionsFile(Map<String, ItemDefinition> items) {
        static final Codec<ItemDefinitionsFile> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.unboundedMap(Codec.STRING, ItemDefinition.CODEC).optionalFieldOf("items", Map.of())
                        .forGetter(ItemDefinitionsFile::items)
        ).apply(inst, ItemDefinitionsFile::new));
    }
}
