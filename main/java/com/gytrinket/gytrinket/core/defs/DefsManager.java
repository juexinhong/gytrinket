package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 定义类数据管理器（datapack 数据驱动）
 *
 * 将原先写在 ModConfigSpec(TOML) 中的"定义类"配置迁移到 datapack registry：
 * <pre>
 *   data/&lt;命名空间&gt;/gytrinket/item_sets/&lt;系统名&gt;.json            -- 物品/实体集合
 *   data/&lt;命名空间&gt;/gytrinket/shield_types/&lt;类型名&gt;.json          -- 护盾类型兼容性
 *   data/&lt;命名空间&gt;/gytrinket/item_shield_types/&lt;物品名&gt;.json      -- 物品-&gt;护盾类型
 *   data/&lt;命名空间&gt;/gytrinket/attribute_definitions/&lt;文件&gt;.json     -- 属性定义（多文件合并）
 *   data/&lt;命名空间&gt;/gytrinket/item_dependencies/&lt;物品名&gt;.json       -- 禁用/依赖关系
 *   data/&lt;命名空间&gt;/gytrinket/special_mechanics/&lt;物品名&gt;.json       -- 特殊机制声明（路径定义：文件存在即声明）
 *   data/&lt;命名空间&gt;/gytrinket/upgrade_paths/&lt;基础物品名&gt;.json       -- 升级路径
 * </pre>
 *
 * 服务端：注册 9 个 datapack registry（含网络编解码，登录时自动同步到客户端），
 * 在 {@link AddReloadListenerEvent} 时读取并填充 {@link Config} 的集合（/reload 生效）。
 * 客户端：Tooltip 等显示逻辑通过 {@link #itemSetContains(RegistryAccess, String, String)} 等
 * 方法从同步后的 registryAccess 实时查询。
 */
@EventBusSubscriber(modid = gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DefsManager {

    private static final String REGISTRY_NAMESPACE = gytrinket.MODID;

    // ===== 运行时覆盖层（绕过数据包验证：编辑写入独立 JSON，玩家手动「应用」生效） =====

    /** 覆盖文件（config/gytrinket/gytrinket_ui_overrides.json，位于数据包目录之外，不触发数据包校验/安全模式） */
    private static final String OVERRIDES_FILE_NAME = "gytrinket_ui_overrides.json";

    /** 分层覆盖文件匹配：override_layer_数字.json（数字为阿拉伯数字，仅用于区分文件；同条目定义时数字越大覆盖优先级越高） */
    private static final java.util.regex.Pattern OVERRIDE_LAYER_FILE_PATTERN =
            java.util.regex.Pattern.compile("override_layer_(\\d+)\\.json", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** UI 覆盖文件拥有的条目（specialMechanics 的 itemId）：保存时仅写这些条目，避免分层文件条目被复制进 UI 文件后遮蔽外部分层文件的后续修改 */
    private static final Set<String> SPECIAL_MECHANIC_UI_OWNED = new HashSet<>();

    /** UI 覆盖文件拥有的条目（shieldTypes 的 itemId），作用同上 */
    private static final Set<String> SHIELD_TYPE_UI_OWNED = new HashSet<>();

    /** UI 覆盖文件拥有的条目（items 段的物品 id）：统一物品级覆盖的保存范围（旧段条目加载时升级为本集合） */
    private static final Set<String> ITEM_UI_OWNED = new HashSet<>();

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

    /**
     * 特殊机制覆盖条目：removed=true 表示撤销声明。
     * <p>
     * values：物品级机制数值覆盖（机制集合名 -> 参数键 -> ParamValue），仅由配置面板 UI 编辑产生；
     * 未覆盖的参数回退 Config 默认值（视为不可叠加）。同机制多物品时按 ParamValue.stackable 合并：
     * 可叠加求和、不可叠加各自比对，最终值 = max(可叠加和, 最大的不可叠加项)。
     */
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

    /**
     * 护盾类型覆盖条目：类型列表。
     * <p>
     * values：物品级护盾数值覆盖（护盾类型名 -> 参数键 -> ParamValue），仅由配置面板 UI 编辑产生；
     * 未覆盖的参数回退 Config 默认值。护盾类型数值为"每物品实例独立"：
     * 多实例并存各用各的数值，不参与叠/单合并。
     */
    public record ShieldTypeOverride(List<String> types,
                                     Map<String, Map<String, ParamValue>> values) {
    }

    private static final Map<String, SpecialMechanicOverride> SERVER_SPECIAL_MECHANIC_OVERRIDES = new HashMap<>();
    private static final Map<String, ShieldTypeOverride> SERVER_SHIELD_TYPE_OVERRIDES = new HashMap<>();
    /** 统一物品级覆盖（items 段）：物品 id -> ItemDefinition，对覆盖的物品完全接管（旧段/registry 不再为该物品生效） */
    private static final Map<String, ItemDefinition> SERVER_ITEM_OVERRIDES = new HashMap<>();
    /** 客户端：从服务端同步的覆盖数据（面板显示用） */
    private static final Map<String, SpecialMechanicOverride> CLIENT_SPECIAL_MECHANIC_OVERRIDES = new HashMap<>();
    private static final Map<String, ShieldTypeOverride> CLIENT_SHIELD_TYPE_OVERRIDES = new HashMap<>();

    // ===== registry 键 =====
    public static final ResourceKey<Registry<ItemSetDef>> ITEM_SETS_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "item_sets"));
    public static final ResourceKey<Registry<ShieldTypeDef>> SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "shield_types"));
    public static final ResourceKey<Registry<ItemShieldTypeDef>> ITEM_SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "item_shield_types"));
    public static final ResourceKey<Registry<AttributeDefs>> ATTRIBUTE_DEFS_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "attribute_definitions"));
    public static final ResourceKey<Registry<ItemDependencyDef>> ITEM_DEPENDENCIES_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "item_dependencies"));
    public static final ResourceKey<Registry<SpecialMechanicDef>> SPECIAL_MECHANICS_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "special_mechanics"));
    public static final ResourceKey<Registry<ModuleTreeDef>> MODULE_TREES_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "module_trees"));
    public static final ResourceKey<Registry<UpgradePathDef>> UPGRADE_PATHS_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "upgrade_paths"));
    public static final ResourceKey<Registry<TooltipRuleDef>> TOOLTIP_RULES_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "tooltip_rules"));
    public static final ResourceKey<Registry<ItemDefinitionsFile>> ITEM_DEFINITIONS_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, "item_definitions"));

    // ===== 数据模型 =====

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

    /** 物品-&gt;护盾类型条目：{ "item": "...", "types": [...] } */
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
     * 特殊机制条目（路径定义 + 分类声明二合一）。
     * <p>
     * 条目 id（文件名）= 物品 id，文件存在即声明该物品为特殊机制（快速装备只检查文件名）。
     * 文件内容 {"sets":[...]} 声明该物品所属的机制分类，用于替代对应的 item_sets 文件；
     * 内容可省略为 {}（仅声明特殊机制，不参与任何分类）。
     * 文件内容 {"removed":true} 表示撤销声明（用于世界数据包覆盖 JAR 内声明，实现运行时删除）。
     */
    public record SpecialMechanicDef(List<String> sets, boolean removed) {
        static final Codec<SpecialMechanicDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("sets", List.of()).forGetter(SpecialMechanicDef::sets),
                Codec.BOOL.optionalFieldOf("removed", false).forGetter(SpecialMechanicDef::removed)
        ).apply(inst, SpecialMechanicDef::new));
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

    /** 工具提示规则条目：{ "itemSet": "...", "titleKey": "...", "descKey": "...", "color": "...", "params": [...] } */
    public record TooltipRuleDef(String itemSet, String titleKey, String descriptionKey, String color, List<TooltipParam> params) {
        static final Codec<TooltipRuleDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("itemSet").forGetter(TooltipRuleDef::itemSet),
                Codec.STRING.fieldOf("titleKey").forGetter(TooltipRuleDef::titleKey),
                Codec.STRING.fieldOf("descKey").forGetter(TooltipRuleDef::descriptionKey),
                Codec.STRING.fieldOf("color").forGetter(TooltipRuleDef::color),
                TooltipParam.CODEC.listOf().optionalFieldOf("params", List.of()).forGetter(TooltipRuleDef::params)
        ).apply(inst, TooltipRuleDef::new));
    }

    /**
     * 统一物品级定义（权威结构）：机制声明 + 护盾类型 + 护盾数值 + 机制数值 + 物品属性，一结构承载。
     * <p>
     * 覆盖语义：
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

    // ===== 服务端加载缓存 =====
    private static final Map<String, Set<String>> ITEM_SETS = new HashMap<>();
    private static final Map<String, Set<String>> ENTITY_SETS = new HashMap<>();
    /** 声明为"特殊机制"的物品集合（specialMechanic=true 的物品集合条目并集），供快速装备等统一判定 */
    private static final Set<String> SPECIAL_MECHANIC_ITEMS = new HashSet<>();
    private static final Map<String, Boolean> SHIELD_TYPES = new HashMap<>();
    /** 护盾类型基础参数默认值缓存（shield_types/*.json 的 shieldValues 段）：四项基础属性的类型级回退层 */
    private static final Map<String, Map<String, ParamValue>> SHIELD_TYPE_PARAM_DEFAULTS = new HashMap<>();
    private static final Map<String, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final List<AttributeEntry> ATTRIBUTE_DEFS = new ArrayList<>();
    private static final Map<String, Set<String>> DISABLE_TARGETS = new HashMap<>();
    private static final Map<String, Set<String>> DEPENDENCIES = new HashMap<>();
    private static final Map<String, Set<String>> DISABLE_CATEGORIES = new HashMap<>();
    private static final Map<String, List<List<String>>> DEPENDENCIES_ALL = new HashMap<>();
    private static final Map<String, ModuleTreeDef> MODULE_TREES = new LinkedHashMap<>();
    private static final Map<String, List<String>> UPGRADE_PATHS = new HashMap<>();
    private static final List<TooltipRuleDef> TOOLTIP_RULES = new ArrayList<>();
    /** 统一物品定义合并缓存：物品 id -> 合并后的 ItemDefinition（registry 各层 + SERVER_ITEM_OVERRIDES 顶层） */
    private static final Map<String, ItemDefinition> ITEM_DEFINITIONS = new LinkedHashMap<>();

    private DefsManager() {}

    // ===== registry 注册（mod 总线，双端） =====
    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ITEM_SETS_KEY, ItemSetDef.CODEC, ItemSetDef.CODEC);
        event.dataPackRegistry(SHIELD_TYPES_KEY, ShieldTypeDef.CODEC, ShieldTypeDef.CODEC);
        event.dataPackRegistry(ITEM_SHIELD_TYPES_KEY, ItemShieldTypeDef.CODEC, ItemShieldTypeDef.CODEC);
        event.dataPackRegistry(ATTRIBUTE_DEFS_KEY, AttributeDefs.CODEC, AttributeDefs.CODEC);
        event.dataPackRegistry(ITEM_DEPENDENCIES_KEY, ItemDependencyDef.CODEC, ItemDependencyDef.CODEC);
        event.dataPackRegistry(SPECIAL_MECHANICS_KEY, SpecialMechanicDef.CODEC, SpecialMechanicDef.CODEC);
        event.dataPackRegistry(MODULE_TREES_KEY, ModuleTreeDef.CODEC, ModuleTreeDef.CODEC);
        event.dataPackRegistry(UPGRADE_PATHS_KEY, UpgradePathDef.CODEC, UpgradePathDef.CODEC);
        event.dataPackRegistry(TOOLTIP_RULES_KEY, TooltipRuleDef.CODEC, TooltipRuleDef.CODEC);
        event.dataPackRegistry(ITEM_DEFINITIONS_KEY, ItemDefinitionsFile.CODEC, ItemDefinitionsFile.CODEC);
        gytrinket.LOGGER.info("已注册 10 个定义类 datapack registry");
    }

    // ===== 服务端加载（forge 总线） =====
    @EventBusSubscriber(modid = gytrinket.MODID)
    public static class ReloadHandler {
        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            RegistryAccess registryAccess = event.getRegistryAccess();
            event.addListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                    return null;
                }

                @Override
                protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                    loadFrom(registryAccess);
                }
            });
        }
    }

    private static void loadFrom(RegistryAccess access) {
        // 每次定义加载（世界启动/重载/手动应用）先读入运行时覆盖文件，保证持久化与优先级
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            loadOverridesFromFile(server);
        }

        ITEM_SETS.clear();
        ENTITY_SETS.clear();
        SPECIAL_MECHANIC_ITEMS.clear();
        SHIELD_TYPES.clear();
        SHIELD_TYPE_PARAM_DEFAULTS.clear();
        ITEM_SHIELD_TYPES.clear();
        ATTRIBUTE_DEFS.clear();
        DISABLE_TARGETS.clear();
        DEPENDENCIES.clear();
        DISABLE_CATEGORIES.clear();
        DEPENDENCIES_ALL.clear();
        MODULE_TREES.clear();
        UPGRADE_PATHS.clear();
        ITEM_DEFINITIONS.clear();

        access.registry(ITEM_SETS_KEY).ifPresent(reg -> {
            for (Map.Entry<ResourceKey<ItemSetDef>, ItemSetDef> e : reg.entrySet()) {
                String setName = e.getKey().location().getPath();
                ItemSetDef def = e.getValue();
                if (!def.items().isEmpty()) {
                    ITEM_SETS.put(setName, new HashSet<>(def.items()));
                }
                if (!def.entities().isEmpty()) {
                    ENTITY_SETS.put(setName, new HashSet<>(def.entities()));
                }
            }
        });

        // 特殊机制（路径定义 + 分类声明）：文件名 = 物品 id（快速装备判定）；内容 sets 并入 ITEM_SETS 分类
        // 运行时覆盖（SERVER_SPECIAL_MECHANIC_OVERRIDES）优先：removed 撤销声明，sets 覆盖分类
        // 统一 items 覆写显式接管机制声明时（removed / mechanic 非 null）跳过本 registry
        access.registry(SPECIAL_MECHANICS_KEY).ifPresent(reg -> {
            Set<String> seen = new HashSet<>();
            for (Map.Entry<ResourceKey<SpecialMechanicDef>, SpecialMechanicDef> e : reg.entrySet()) {
                String itemId = e.getKey().location().toString();
                seen.add(itemId);
                if (itemOverrideOwnsMechanic(itemId)) {
                    continue;
                }
                SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
                if (ov != null) {
                    if (ov.removed()) {
                        continue;
                    }
                    SPECIAL_MECHANIC_ITEMS.add(itemId);
                    for (String setName : ov.sets()) {
                        if (setName == null || setName.isEmpty()) continue;
                        ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                    }
                    continue;
                }
                if (e.getValue().removed()) {
                    // 世界数据包覆盖：撤销 JAR 内声明（removed:true），不参与特殊机制与分类
                    continue;
                }
                SPECIAL_MECHANIC_ITEMS.add(itemId);
                for (String setName : e.getValue().sets()) {
                    if (setName == null || setName.isEmpty()) continue;
                    ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                }
            }
            // 覆盖：新增 JAR 中不存在的条目（机制声明已被 items 覆写显式接管的除外）
            for (var e : SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
                if (seen.contains(e.getKey()) || e.getValue().removed() || itemOverrideOwnsMechanic(e.getKey())) {
                    continue;
                }
                SPECIAL_MECHANIC_ITEMS.add(e.getKey());
                for (String setName : e.getValue().sets()) {
                    if (setName == null || setName.isEmpty()) continue;
                    ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(e.getKey());
                }
            }
        });

        access.registry(SHIELD_TYPES_KEY).ifPresent(reg -> {
            for (Map.Entry<ResourceKey<ShieldTypeDef>, ShieldTypeDef> e : reg.entrySet()) {
                SHIELD_TYPES.put(e.getKey().location().getPath(), e.getValue().compatible());
                if (!e.getValue().shieldValues().isEmpty()) {
                    SHIELD_TYPE_PARAM_DEFAULTS.put(e.getKey().location().getPath(), e.getValue().shieldValues());
                }
            }
        });

        access.registry(ITEM_SHIELD_TYPES_KEY).ifPresent(reg -> {
            for (ItemShieldTypeDef def : reg) {
                if (itemOverrideOwnsShieldTypes(def.item())) {
                    continue;
                }
                ITEM_SHIELD_TYPES.put(def.item(), new ArrayList<>(def.types()));
            }
        });

        // 运行时覆盖：护盾类型以覆盖为准（空列表 = 移除全部类型）。
        // 统一 items 覆写显式接管护盾类型时（removed / shieldTypes 非 null）跳过旧段覆盖
        for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            if (itemOverrideOwnsShieldTypes(e.getKey())) {
                continue;
            }
            ITEM_SHIELD_TYPES.put(e.getKey(), new ArrayList<>(e.getValue().types()));
        }

        access.registry(ATTRIBUTE_DEFS_KEY).ifPresent(reg -> {
            for (AttributeDefs defs : reg) {
                ATTRIBUTE_DEFS.addAll(defs.attributes());
            }
        });

        access.registry(ITEM_DEPENDENCIES_KEY).ifPresent(reg -> {
            for (ItemDependencyDef def : reg) {
                if (!def.disables().isEmpty()) {
                    DISABLE_TARGETS.put(def.item(), new HashSet<>(def.disables()));
                }
                if (!def.dependsOn().isEmpty()) {
                    DEPENDENCIES.put(def.item(), new HashSet<>(def.dependsOn()));
                }
                if (!def.disablesCategories().isEmpty()) {
                    DISABLE_CATEGORIES.put(def.item(), new HashSet<>(def.disablesCategories()));
                }
                if (!def.dependsOnAll().isEmpty()) {
                    DEPENDENCIES_ALL.put(def.item(), def.dependsOnAll());
                }
            }
        });

        access.registry(MODULE_TREES_KEY).ifPresent(reg -> {
            for (Map.Entry<ResourceKey<ModuleTreeDef>, ModuleTreeDef> e : reg.entrySet()) {
                MODULE_TREES.put(e.getKey().location().getPath(), e.getValue());
            }
        });

        access.registry(UPGRADE_PATHS_KEY).ifPresent(reg -> {
            for (UpgradePathDef def : reg) {
                UPGRADE_PATHS.put(def.base(), new ArrayList<>(def.upgrades()));
            }
        });

        access.registry(TOOLTIP_RULES_KEY).ifPresent(reg -> {
            TOOLTIP_RULES.clear();
            reg.forEach(TOOLTIP_RULES::add);
        });

        // 统一物品定义合并：item_definitions registry（按 key 排序防多文件同物品顺序不定）+ 顶层 SERVER_ITEM_OVERRIDES
        access.registry(ITEM_DEFINITIONS_KEY).ifPresent(reg -> {
            List<Map.Entry<ResourceKey<ItemDefinitionsFile>, ItemDefinitionsFile>> entries = new ArrayList<>(reg.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            for (var e : entries) {
                for (var item : e.getValue().items().entrySet()) {
                    ITEM_DEFINITIONS.merge(normalizeItemId(item.getKey()), item.getValue(), ItemDefinition::merge);
                }
            }
        });
        for (var e : SERVER_ITEM_OVERRIDES.entrySet()) {
            ITEM_DEFINITIONS.merge(e.getKey(), e.getValue(), ItemDefinition::merge);
        }

        // items 统一定义派生旧缓存：遍历合并后全量条目（JAR 数据 + 覆写），
        // 显式接管的字段以合并后定义为准（合并链保留下层 sets/shieldTypes）
        for (var e : ITEM_DEFINITIONS.entrySet()) {
            String itemId = e.getKey();
            ItemDefinition def = e.getValue();
            if (def != null && def.removed()) {
                // removed：整体撤销机制声明与护盾类型
                SPECIAL_MECHANIC_ITEMS.remove(itemId);
                ITEM_SHIELD_TYPES.remove(itemId);
                continue;
            }
            if (def != null && def.mechanic() != null) {
                if (def.mechanic()) {
                    SPECIAL_MECHANIC_ITEMS.add(itemId);
                    if (def.sets() != null) {
                        for (String setName : def.sets()) {
                            if (setName == null || setName.isEmpty()) continue;
                            ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                        }
                    }
                } else {
                    SPECIAL_MECHANIC_ITEMS.remove(itemId);
                }
            }
            if (def != null && def.shieldTypes() != null) {
                if (def.shieldTypes().isEmpty()) {
                    ITEM_SHIELD_TYPES.remove(itemId);
                } else {
                    ITEM_SHIELD_TYPES.put(itemId, new ArrayList<>(def.shieldTypes()));
                }
            }
        }

        gytrinket.LOGGER.info("定义类数据加载完成：物品集合 {} 项，护盾类型 {} 项，物品护盾类型 {} 项，属性定义 {} 项，禁用目标 {} 项，依赖 {} 项，类别禁用 {} 项，AND依赖 {} 项，模块树 {} 棵，升级路径 {} 项，工具提示规则 {} 项",
                ITEM_SETS.size(), SHIELD_TYPES.size(), ITEM_SHIELD_TYPES.size(),
                ATTRIBUTE_DEFS.size(), DISABLE_TARGETS.size(), DEPENDENCIES.size(),
                DISABLE_CATEGORIES.size(), DEPENDENCIES_ALL.size(), MODULE_TREES.size(), UPGRADE_PATHS.size(), TOOLTIP_RULES.size());

        // 填充 Config 集合并触发依赖定义数据的子系统重载
        Config.applyDefs();
    }

    /** items 覆写条目是否显式接管该物品的机制声明（removed 撤销，或 mechanic 非 null） */
    private static boolean itemOverrideOwnsMechanic(String itemId) {
        ItemDefinition def = SERVER_ITEM_OVERRIDES.get(itemId);
        return def != null && (def.removed() || def.mechanic() != null);
    }

    /** items 覆写条目是否显式接管该物品的护盾类型（removed 撤销，或 shieldTypes 非 null） */
    private static boolean itemOverrideOwnsShieldTypes(String itemId) {
        ItemDefinition def = SERVER_ITEM_OVERRIDES.get(itemId);
        return def != null && (def.removed() || def.shieldTypes() != null);
    }

    /** 物品 id 规范化：无命名空间时补 gytrinket: */
    private static String normalizeItemId(String id) {
        return id != null && !id.isEmpty() && !id.contains(":") ? gytrinket.MODID + ":" + id : id;
    }

    // ===== 服务端查询（供 Config.applyDefs 与各子系统使用） =====

    // ===== 运行时覆盖层：文件读写与生效 =====

    /** 覆写定义文件路径：config/gytrinket/gytrinket_ui_overrides.json（全局持久化，所有世界共享） */
    private static Path getOverridesFile(MinecraftServer server) {
        return FMLPaths.CONFIGDIR.get().resolve("gytrinket").resolve(OVERRIDES_FILE_NAME);
    }

    /** 覆盖目录：config/gytrinket（分层覆盖文件 override_layer_数字.json 与 UI 覆盖文件同目录） */
    private static Path getOverridesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("gytrinket");
    }

    /**
     * 读取覆盖文件到服务端内存。
     * 读取顺序：分层文件 override_layer_数字.json 按数字升序加载（同条目定义时数字大的覆盖数字小的），
     * 最后读取 UI 覆盖文件（UI 编辑条目优先级最高，保证编辑保存后立即生效）。
     * 文件不存在时忽略。
     */
    private static void loadOverridesFromFile(MinecraftServer server) {
        SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        SERVER_SHIELD_TYPE_OVERRIDES.clear();
        SERVER_ITEM_OVERRIDES.clear();
        // UI 拥有条目随内存重建：仅 UI 文件与 UI 编辑写入的条目会回存 UI 覆盖文件
        SPECIAL_MECHANIC_UI_OWNED.clear();
        SHIELD_TYPE_UI_OWNED.clear();
        ITEM_UI_OWNED.clear();
        Path dir = getOverridesDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        // 扫描分层覆盖文件并按数字升序排序：后加载条目覆盖先加载条目 → 数字越大优先级越高
        Map<Integer, Path> layeredFiles = new TreeMap<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                var m = OVERRIDE_LAYER_FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    layeredFiles.put(Integer.parseInt(m.group(1)), p);
                }
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("扫描定义覆盖目录失败: {}", dir, e);
        }
        for (Path file : layeredFiles.values()) {
            loadSingleOverridesFile(file, false);
        }
        if (!layeredFiles.isEmpty()) {
            gytrinket.LOGGER.info("已读取分层定义覆盖文件 {} 个", layeredFiles.size());
        }
        // UI 覆盖文件最后加载（最高优先级）
        loadSingleOverridesFile(getOverridesFile(server), true);
    }

    /** 解析单个覆盖文件并写入服务端覆盖内存（不存在时忽略，解析失败跳过该文件并继续后续层）；UI 文件条目额外登记为 UI 拥有 */
    private static void loadSingleOverridesFile(Path file, boolean isUiOverrideFile) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            // 物品级条目暂存：UI 文件条目（旧段升级 + items 直读，回存 UI 文件）与分层文件 items 条目（仅生效不回存）分开登记
            Map<String, ItemDefinition> upgraded = new LinkedHashMap<>();
            Map<String, ItemDefinition> layeredUpgraded = new LinkedHashMap<>();
            if (root.has("specialMechanics")) {
                for (var e : root.getAsJsonObject("specialMechanics").entrySet()) {
                    String itemId = e.getKey();
                    com.google.gson.JsonObject def = e.getValue().getAsJsonObject();
                    boolean removed = def.has("removed") && def.get("removed").getAsBoolean();
                    List<String> sets = new ArrayList<>();
                    if (def.has("sets") && def.get("sets").isJsonArray()) {
                        def.getAsJsonArray("sets").forEach(el -> sets.add(el.getAsString()));
                    }
                    Map<String, Map<String, ParamValue>> values = parseMechanicValues(def);
                    SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId,
                            removed ? SpecialMechanicOverride.removedState() : SpecialMechanicOverride.declared(sets, values));
                    if (isUiOverrideFile) {
                        SPECIAL_MECHANIC_UI_OWNED.add(itemId);
                        // 旧段升级：removed=true 仅精确撤销机制（mechanic=false 而非 removed，不误伤护盾定义）
                        upgraded.merge(itemId,
                                removed ? new ItemDefinition(false, false, null, null, null, values, null)
                                        : new ItemDefinition(true, false, sets, null, null, values, null),
                                ItemDefinition::merge);
                    }
                }
            }
            if (root.has("shieldTypes")) {
                for (var e : root.getAsJsonObject("shieldTypes").entrySet()) {
                    String itemId = e.getKey();
                    ShieldTypeOverride ov = parseShieldTypeOverride(e.getValue());
                    SERVER_SHIELD_TYPE_OVERRIDES.put(itemId, ov);
                    if (isUiOverrideFile) {
                        SHIELD_TYPE_UI_OWNED.add(itemId);
                        // 旧段升级：types 整体覆盖 + 各类型 values 拍平为 shieldValues（护盾物品均单类型无歧义）；独占标记弃用（多实例语义取代）
                        Map<String, ParamValue> shieldValues = new LinkedHashMap<>();
                        for (var typeEntry : ov.values().entrySet()) {
                            shieldValues.putAll(typeEntry.getValue());
                        }
                        upgraded.merge(itemId,
                                new ItemDefinition(null, false, null, ov.types(), shieldValues, null, null),
                                ItemDefinition::merge);
                    }
                }
            }
            // items 段（统一权威结构）
            Map<String, ItemDefinition> itemsTarget = isUiOverrideFile ? upgraded : layeredUpgraded;
            if (root.has("items")) {
                for (var e : root.getAsJsonObject("items").entrySet()) {
                    ItemDefinition def = parseItemDefinition(e.getValue());
                    if (def != null) {
                        itemsTarget.merge(normalizeItemId(e.getKey()), def, ItemDefinition::merge);
                    }
                }
            }
            for (var e : upgraded.entrySet()) {
                SERVER_ITEM_OVERRIDES.put(e.getKey(), e.getValue());
                ITEM_UI_OWNED.add(e.getKey());
            }
            for (var e : layeredUpgraded.entrySet()) {
                SERVER_ITEM_OVERRIDES.put(e.getKey(), e.getValue());
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("读取定义覆盖文件失败: {}", file, e);
        }
    }

    /** 解析 items 段物品定义为统一权威结构（全部字段可选；缺失 = 未覆盖，保留下层值） */
    private static ItemDefinition parseItemDefinition(com.google.gson.JsonElement el) {
        if (!el.isJsonObject()) return null;
        com.google.gson.JsonObject def = el.getAsJsonObject();
        Boolean mechanic = def.has("mechanic") && def.get("mechanic").isJsonPrimitive() ? def.get("mechanic").getAsBoolean() : null;
        boolean removed = def.has("removed") && def.get("removed").getAsBoolean();
        Map<String, ParamValue> shieldValues = def.has("shieldValues") && def.get("shieldValues").isJsonObject()
                ? parseParamMap(def.getAsJsonObject("shieldValues")) : null;
        Map<String, Map<String, ParamValue>> values = null;
        if (def.has("values") && def.get("values").isJsonObject()) {
            values = new LinkedHashMap<>();
            for (var setEntry : def.getAsJsonObject("values").entrySet()) {
                if (setEntry.getValue().isJsonObject()) {
                    Map<String, ParamValue> params = parseParamMap(setEntry.getValue().getAsJsonObject());
                    if (!params.isEmpty()) {
                        values.put(setEntry.getKey(), params);
                    }
                }
            }
        }
        Map<String, ParamValue> attributes = def.has("attributes") && def.get("attributes").isJsonObject()
                ? parseParamMap(def.getAsJsonObject("attributes")) : null;
        return new ItemDefinition(mechanic, removed, parseStringList(def, "sets"), parseStringList(def, "shieldTypes"),
                shieldValues, values, attributes);
    }

    /** 解析 ParamValue 映射：值可为简写数值或 {value, stackable} 对象 */
    private static Map<String, ParamValue> parseParamMap(com.google.gson.JsonObject obj) {
        Map<String, ParamValue> result = new LinkedHashMap<>();
        for (var e : obj.entrySet()) {
            try {
                if (e.getValue().isJsonPrimitive()) {
                    result.put(e.getKey(), ParamValue.of(e.getValue().getAsDouble()));
                } else if (e.getValue().isJsonObject()) {
                    com.google.gson.JsonObject pv = e.getValue().getAsJsonObject();
                    double value = pv.has("value") ? pv.get("value").getAsDouble() : 0.0;
                    boolean stackable = !pv.has("stackable") || pv.get("stackable").getAsBoolean();
                    result.put(e.getKey(), new ParamValue(value, stackable));
                }
            } catch (Exception ignored) {
                // 非法数值跳过
            }
        }
        return result;
    }

    /** 解析字符串数组字段（缺失或类型不符返回 null，表达"未覆盖"） */
    private static List<String> parseStringList(com.google.gson.JsonObject def, String name) {
        if (def.has(name) && def.get(name).isJsonArray()) {
            List<String> result = new ArrayList<>();
            def.getAsJsonArray(name).forEach(el -> result.add(el.getAsString()));
            return result;
        }
        return null;
    }

    /** 解析护盾类型覆盖条目：兼容旧格式（字符串数组），新格式含 values/stackables 数值段 */
    private static ShieldTypeOverride parseShieldTypeOverride(com.google.gson.JsonElement el) {
        if (el.isJsonArray()) {
            List<String> types = new ArrayList<>();
            el.getAsJsonArray().forEach(t -> types.add(t.getAsString()));
            return new ShieldTypeOverride(types, Map.of());
        }
        com.google.gson.JsonObject def = el.getAsJsonObject();
        List<String> types = new ArrayList<>();
        if (def.has("types") && def.get("types").isJsonArray()) {
            def.getAsJsonArray("types").forEach(t -> types.add(t.getAsString()));
        }
        // 数值段结构同机制数值：values={"<type>": {"<param>": value}} + stackables={"<type>": {"<param>": bool}}
        Map<String, Map<String, ParamValue>> values = parseMechanicValues(def);
        return new ShieldTypeOverride(types, values);
    }

    /** 解析机制数值覆盖段：values={"<set>": {"<param>": value}} + stackables={"<set>": {"<param>": bool}}，缺省叠加标记视为可叠加（兼容旧格式文件） */
    private static Map<String, Map<String, ParamValue>> parseMechanicValues(com.google.gson.JsonObject def) {
        Map<String, Map<String, ParamValue>> result = new LinkedHashMap<>();
        if (!def.has("values") || !def.get("values").isJsonObject()) {
            return result;
        }
        com.google.gson.JsonObject stackables = def.has("stackables") && def.get("stackables").isJsonObject()
                ? def.getAsJsonObject("stackables") : new com.google.gson.JsonObject();
        for (var setEntry : def.getAsJsonObject("values").entrySet()) {
            if (!setEntry.getValue().isJsonObject()) continue;
            Map<String, ParamValue> params = new LinkedHashMap<>();
            com.google.gson.JsonObject setStackables = stackables.has(setEntry.getKey())
                    && stackables.get(setEntry.getKey()).isJsonObject()
                    ? stackables.getAsJsonObject(setEntry.getKey()) : new com.google.gson.JsonObject();
            for (var paramEntry : setEntry.getValue().getAsJsonObject().entrySet()) {
                try {
                    boolean stackable = setStackables.has(paramEntry.getKey())
                            && setStackables.get(paramEntry.getKey()).isJsonPrimitive()
                            && setStackables.get(paramEntry.getKey()).getAsBoolean();
                    params.put(paramEntry.getKey(), new ParamValue(paramEntry.getValue().getAsDouble(), stackable));
                } catch (Exception ignored) {
                    // 非法数值跳过（回退 config 默认）
                }
            }
            if (!params.isEmpty()) {
                result.put(setEntry.getKey(), params);
            }
        }
        return result;
    }

    /** 将统一物品定义序列化到条目 JSON：仅写非 null 字段；ParamValue 全可叠加时省略 stackable */
    private static void writeItemDefinitionToJson(com.google.gson.JsonObject def, ItemDefinition d) {
        if (d.mechanic() != null) {
            def.addProperty("mechanic", d.mechanic());
        }
        if (d.removed()) {
            def.addProperty("removed", true);
        }
        if (d.sets() != null) {
            com.google.gson.JsonArray sets = new com.google.gson.JsonArray();
            d.sets().forEach(sets::add);
            def.add("sets", sets);
        }
        if (d.shieldTypes() != null) {
            com.google.gson.JsonArray types = new com.google.gson.JsonArray();
            d.shieldTypes().forEach(types::add);
            def.add("shieldTypes", types);
        }
        if (d.shieldValues() != null && !d.shieldValues().isEmpty()) {
            def.add("shieldValues", writeParamMap(d.shieldValues()));
        }
        if (d.values() != null && !d.values().isEmpty()) {
            com.google.gson.JsonObject valuesJson = new com.google.gson.JsonObject();
            for (var setEntry : d.values().entrySet()) {
                if (!setEntry.getValue().isEmpty()) {
                    valuesJson.add(setEntry.getKey(), writeParamMap(setEntry.getValue()));
                }
            }
            def.add("values", valuesJson);
        }
        if (d.attributes() != null && !d.attributes().isEmpty()) {
            def.add("attributes", writeParamMap(d.attributes()));
        }
    }

    /** 序列化 ParamValue 映射：可叠加写简写数值，不可叠加写 {value, stackable:false} */
    private static com.google.gson.JsonObject writeParamMap(Map<String, ParamValue> params) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        for (var e : params.entrySet()) {
            if (e.getValue().stackable()) {
                result.addProperty(e.getKey(), e.getValue().value());
            } else {
                com.google.gson.JsonObject pv = new com.google.gson.JsonObject();
                pv.addProperty("value", e.getValue().value());
                pv.addProperty("stackable", false);
                result.add(e.getKey(), pv);
            }
        }
        return result;
    }

    /** 将服务端内存中的覆盖数据写入覆盖文件（编辑操作持久化，不触发重载）；仅写 items 段（统一权威结构，仅 UI 拥有条目） */
    private static void saveOverridesToFile(MinecraftServer server) {
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonObject items = new com.google.gson.JsonObject();
            for (var e : SERVER_ITEM_OVERRIDES.entrySet()) {
                if (!ITEM_UI_OWNED.contains(e.getKey())) {
                    continue;
                }
                com.google.gson.JsonObject def = new com.google.gson.JsonObject();
                writeItemDefinitionToJson(def, e.getValue());
                items.add(e.getKey(), def);
            }
            root.add("items", items);
            Path file = getOverridesFile(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            gytrinket.LOGGER.info("已保存定义覆盖文件: {}", file);
        } catch (Exception e) {
            gytrinket.LOGGER.error("保存定义覆盖文件失败", e);
        }
    }

    /** 编辑操作：更新特殊机制声明（写入统一 items 覆写 + 覆盖文件，立即重新加载生效，不重载数据包） */
    public static void updateSpecialMechanicOverride(MinecraftServer server, String itemId, boolean removed) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        // 机制编辑仅改机制声明（removed=true 精确撤销机制 = mechanic:false，不误伤护盾定义）；其余字段保留既有
        ItemDefinition def = new ItemDefinition(removed ? Boolean.FALSE : Boolean.TRUE, false,
                removed ? null : List.of(),
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null);
        SERVER_ITEM_OVERRIDES.put(itemId, def);
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 服务端：物品当前生效的特殊机制集合（统一 items 覆写优先，其次旧段覆盖，最后数据驱动声明） */
    public static List<String> getEffectiveSpecialMechanicSets(MinecraftServer server, String itemId) {
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null) {
            if (item.removed()) {
                return new ArrayList<>();
            }
            if (item.mechanic() != null) {
                // mechanic 显式声明：true 时 sets 整体覆盖（null = 无分类），false 时无机制
                return item.mechanic() ? new ArrayList<>(item.sets() != null ? item.sets() : List.of()) : new ArrayList<>();
            }
            // mechanic=null：部分定义不接管机制，落到旧逻辑
        }
        SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return new ArrayList<>(ov.removed() ? List.of() : ov.sets());
        }
        // 数据驱动：统一物品定义（item_definitions，mechanic 显式声明时接管机制），其次 special_mechanics 路径定义
        ItemDefinition dataDef = ITEM_DEFINITIONS.get(itemId);
        if (dataDef != null) {
            if (dataDef.removed()) {
                return new ArrayList<>();
            }
            if (dataDef.mechanic() != null) {
                return dataDef.mechanic()
                        ? new ArrayList<>(dataDef.sets() != null ? dataDef.sets() : List.of())
                        : new ArrayList<>();
            }
            // mechanic=null：该定义不接管机制，继续走 special_mechanics 路径定义
        }
        var reg = server.registryAccess().registry(SPECIAL_MECHANICS_KEY).orElse(null);
        if (reg != null) {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc != null) {
                SpecialMechanicDef def = reg.get(loc);
                if (def != null && !def.removed()) {
                    return new ArrayList<>(def.sets());
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 服务端：玩家是否装备了声明指定特殊机制集合的物品（覆盖层优先）。
     * <p>
     * 用于替代硬性物品检查（Config.isXxxItem），使配置面板添加/移除的特殊机制同样生效：
     * 只要装备物品通过 special_mechanics 数据驱动或运行时覆盖声明了该集合即视为满足。
     */
    public static boolean playerHasEquippedMechanic(MinecraftServer server, UUID playerUUID, String mechanicSet) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (getEffectiveSpecialMechanicSets(server, itemId).contains(mechanicSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 服务端：解析玩家某机制参数的生效数值（物品级覆盖 + 多物品合并）。
     * <p>
     * 遍历所有声明该机制集合且未被禁用的装备物品，每件贡献一份参数值（有 UI 覆盖取覆盖值，
     * 否则取 fallback = Config 默认值，未覆盖物品视为不可叠加）。合并规则：
     * 可叠加项求和，不可叠加项各自独立参与比对，最终值 = max(可叠加和, 最大的不可叠加项)。
     * 未装备声明物品时返回 fallback（机制判定由调用方的 playerHasEquippedMechanic 负责）。
     */
    public static double resolveMechanicValue(MinecraftServer server, UUID playerUUID,
                                              String mechanicSet, String paramKey, double fallback) {
        if (server == null) return fallback;
        return mergeMechanicValue(playerUUID, PlayerStoreUtils.getEquippedStacks(playerUUID),
                itemId -> getEffectiveSpecialMechanicSets(server, itemId).contains(mechanicSet),
                DefsManager::effectiveMechanicValuesOf, mechanicSet, paramKey, fallback);
    }

    /** 物品级机制数值覆盖查询（统一 items 覆写优先，旧段覆盖回退；供多物品合并管线取值） */
    private static Map<String, Map<String, ParamValue>> effectiveMechanicValuesOf(String itemId) {
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.values() != null) {
            return item.values();
        }
        SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        return ov != null && !ov.removed() ? ov.values() : null;
    }

    /**
     * 服务端：按物品实例解析护盾类型参数值（单物品直接查询，不跨物品合并）。
     * <p>
     * 护盾类型数值为"每物品实例独立"：兼容时多实例并存、各用各的数值；
     * 不兼容时由冲突链决定生效实例。因此取值始终以具体物品为锚点，
     * 未定义该类型覆盖参数时返回 fallback（Config 默认值）。
     */
    public static double resolveShieldTypeValueForItem(MinecraftServer server, String itemId,
                                                       String shieldType, String paramKey, double fallback) {
        if (server == null || itemId == null) return fallback;
        // 统一 items 覆写优先：shieldValues 拍平结构按参数键查询（忽略类型分组）
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && item.shieldValues() != null) {
            ParamValue pv = item.shieldValues().get(paramKey);
            if (pv != null) return pv.value();
        }
        ShieldTypeOverride ov = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov == null) return fallback;
        Map<String, ParamValue> params = ov.values().get(shieldType);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /**
     * 服务端：解析护盾实例参数（shield_base、shield_cooldown_time、shield_hit_cooldown_extend 等）。
     * <p>
     * 查询顺序：统一 items 覆写（SERVER_ITEM_OVERRIDES.shieldValues）→
     * 权威物品定义（ITEM_DEFINITIONS.shieldValues，JAR 数据与覆写合并结果）→
     * 护盾类型定义默认值（SHIELD_TYPE_PARAM_DEFAULTS，shield_types/*.json 的 shieldValues 段，
     * 物品未声明该键时采用类型级默认）→
     * 旧护盾类型覆写段（SERVER_SHIELD_TYPE_OVERRIDES，任意类型组内同键参数）→
     * fallback（调用方传入的玩家全局聚合值：无护盾类型物品基础属性全局生效的通道）。
     * <p>
     * server 为 null 时跳过覆写/权威层（静态缓存不可用），仅走类型默认值层（loadFrom 静态缓存）。
     */
    public static double resolveShieldParam(MinecraftServer server, String itemId, String shieldTypeName,
                                            String paramKey, double fallback) {
        if (itemId == null) return fallback;
        if (server != null) {
            ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
            if (item != null && !item.removed() && item.shieldValues() != null) {
                ParamValue pv = item.shieldValues().get(paramKey);
                if (pv != null) return pv.value();
            }
            ItemDefinition def = ITEM_DEFINITIONS.get(itemId);
            if (def != null && !def.removed() && def.shieldValues() != null) {
                ParamValue pv = def.shieldValues().get(paramKey);
                if (pv != null) return pv.value();
            }
        }
        if (shieldTypeName != null) {
            Map<String, ParamValue> typeDefaults = SHIELD_TYPE_PARAM_DEFAULTS.get(shieldTypeName);
            if (typeDefaults != null) {
                ParamValue pv = typeDefaults.get(paramKey);
                if (pv != null) return pv.value();
            }
        }
        if (server != null) {
            ShieldTypeOverride ov = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
            if (ov != null) {
                for (Map<String, ParamValue> params : ov.values().values()) {
                    ParamValue pv = params.get(paramKey);
                    if (pv != null) return pv.value();
                }
            }
        }
        return fallback;
    }

    /**
     * 客户端：护盾类型定义携带的基础参数默认值（同步的数据包 registry 直查，编辑器默认值显示用）。
     * 未注册类型/未声明该参数返回 null（调用方回退 {@link ParamDef} 默认值）。
     */
    public static Double clientShieldTypeParamDefault(RegistryAccess access, String typeName, String paramKey) {
        if (access == null || typeName == null || paramKey == null) return null;
        return access.registry(SHIELD_TYPES_KEY)
                .flatMap(reg -> reg.getOptional(ResourceLocation.fromNamespaceAndPath(REGISTRY_NAMESPACE, typeName)))
                .map(def -> def.shieldValues().get(paramKey))
                .map(ParamValue::value)
                .orElse(null);
    }

    /**
     * 服务端：物品当前生效的属性贡献（属性聚合管线数据源）。
     * <p>
     * 查询顺序与 {@link #resolveShieldParam} 一致：统一 items 覆写
     * （SERVER_ITEM_OVERRIDES.attributes）→ 权威物品定义
     * （ITEM_DEFINITIONS.attributes，JAR 数据）。覆写 attributes 非 null
     * 即整体接管（<b>显式空 map = 覆盖为无属性</b>，支持 UI 删除权威属性）；
     * 条目被撤销（removed）或未声明 attributes 时回退权威；均无则返回 null
     * （该物品无属性贡献）。
     */
    public static Map<String, ParamValue> getEffectiveItemAttributes(String itemId) {
        if (itemId == null) return null;
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.attributes() != null) {
            return item.attributes();
        }
        ItemDefinition def = ITEM_DEFINITIONS.get(itemId);
        if (def != null && !def.removed() && def.attributes() != null) {
            return def.attributes();
        }
        return null;
    }

    /**
     * 服务端：枚举声明了物品属性的物品 id（配置同步物品列表的属性来源）。
     * <p>
     * 覆写层 attributes 非 null（含显式空接管）∪ 权威定义 attributes 非 null，
     * 均排除 removed 撤销条目。
     */
    public static Set<String> getEffectiveItemAttributeItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (var e : SERVER_ITEM_OVERRIDES.entrySet()) {
            if (!e.getValue().removed() && e.getValue().attributes() != null) {
                ids.add(e.getKey());
            }
        }
        for (var e : ITEM_DEFINITIONS.entrySet()) {
            if (!e.getValue().removed() && e.getValue().attributes() != null) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /**
     * 服务端：枚举玩家装备的、声明指定特殊机制集合的物品（覆盖层优先）。
     * <p>
     * 跳过空栈与被禁用的物品；按物品种类去重——同一物品装备多件只算一个实例，
     * 实例粒度为物品种类而非装备件数。
     */
    public static List<ItemStack> getEquippedMechanicStacks(MinecraftServer server, UUID playerUUID, String mechanicSet) {
        List<ItemStack> result = new ArrayList<>();
        if (server == null || playerUUID == null) return result;
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!seen.add(itemId)) continue;
            if (getEffectiveSpecialMechanicSets(server, itemId).contains(mechanicSet)) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * 服务端：按物品实例解析特殊机制参数值（单物品直接查询，不跨物品合并）。
     * <p>
     * 实例化特殊机制（仿护盾类型）：每件提供该机制的物品独立触发、各用各的数值；
     * 未定义该物品覆盖参数时返回 fallback（Config 默认值）。
     */
    public static double resolveMechanicValueForItem(MinecraftServer server, String itemId,
                                                     String mechanicSet, String paramKey, double fallback) {
        if (server == null || itemId == null) return fallback;
        // 统一 items 覆写优先
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.values() != null) {
            Map<String, ParamValue> params = item.values().get(mechanicSet);
            ParamValue pv = params == null ? null : params.get(paramKey);
            if (pv != null) return pv.value();
            return fallback;
        }
        SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov == null || ov.removed()) return fallback;
        Map<String, ParamValue> params = ov.values().get(mechanicSet);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /**
     * 多物品机制参数合并（服务端/客户端共用算法）：遍历装备物品逐件取值——有 UI 覆盖取覆盖值，
     * 否则取 fallback（未覆盖物品视为不可叠加）。可叠加项求和，不可叠加项比对取最大，
     * 最终值 = max(可叠加和, 最大的不可叠加项)。未装备声明物品时返回 fallback。
     */
    private static double mergeMechanicValue(UUID playerUUID, List<ItemStack> stacks,
                                             Predicate<String> hasMechanicSet,
                                             Function<String, Map<String, Map<String, ParamValue>>> valuesOf,
                                             String mechanicSet, String paramKey, double fallback) {
        return mergeParamValue(playerUUID, stacks, hasMechanicSet, itemId -> {
            Map<String, Map<String, ParamValue>> values = valuesOf.apply(itemId);
            return values == null ? null : values.get(mechanicSet);
        }, paramKey, fallback);
    }

    /** 多物品参数合并通用核心（机制/护盾类型共用）：paramsOf 给出物品在目标集合/类型下的参数覆盖（无则 null） */
    private static double mergeParamValue(UUID playerUUID, List<ItemStack> stacks,
                                          Predicate<String> hasSet,
                                          Function<String, Map<String, ParamValue>> paramsOf,
                                          String paramKey, double fallback) {
        double stackableSum = 0.0D;
        double bestNonStackable = Double.NEGATIVE_INFINITY;
        boolean hasProvider = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!hasSet.test(itemId)) continue;
            hasProvider = true;
            double value = fallback;
            boolean stackable = false;
            Map<String, ParamValue> params = paramsOf.apply(itemId);
            ParamValue pv = params == null ? null : params.get(paramKey);
            if (pv != null) {
                value = pv.value();
                stackable = pv.stackable();
            }
            if (stackable) {
                stackableSum += value;
            } else if (value > bestNonStackable) {
                bestNonStackable = value;
            }
        }
        if (!hasProvider) return fallback;
        return Math.max(stackableSum, bestNonStackable);
    }

    /** 拷贝物品既有的机制数值覆盖（统一 items 覆写优先，旧段覆盖回退；内层 map 深拷贝） */
    private static Map<String, Map<String, ParamValue>> copyExistingValues(String itemId) {
        Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
        ItemDefinition item = SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && item.values() != null) {
            for (var e : item.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            return values;
        }
        SpecialMechanicOverride existing = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (existing != null && !existing.removed()) {
            for (var e : existing.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }
        return values;
    }

    /**
     * 物品当前生效的合并 ItemDefinition（编辑 API 的保留字段基线，过渡期转译查询）：
     * 优先 SERVER_ITEM_OVERRIDES 条目；否则从 SPECIAL_MECHANICS registry + 旧段覆写 + 生效护盾类型合成。
     */
    private static ItemDefinition getEffectiveDefinition(MinecraftServer server, String itemId) {
        ItemDefinition own = SERVER_ITEM_OVERRIDES.get(itemId);
        if (own != null) {
            return own;
        }
        Boolean mechanic = null;
        List<String> sets = null;
        Map<String, Map<String, ParamValue>> values = null;
        var reg = server.registryAccess().registry(SPECIAL_MECHANICS_KEY).orElse(null);
        if (reg != null) {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            SpecialMechanicDef def = loc != null ? reg.get(loc) : null;
            if (def != null && !def.removed()) {
                mechanic = true;
                sets = def.sets();
            }
        }
        SpecialMechanicOverride smOv = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (smOv != null) {
            if (smOv.removed()) {
                mechanic = false;
                sets = null;
            } else {
                mechanic = true;
                sets = smOv.sets();
                values = smOv.values().isEmpty() ? null : smOv.values();
            }
        }
        List<String> shieldTypes = null;
        Map<String, ParamValue> shieldValues = null;
        ShieldTypeOverride stOv = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (stOv != null) {
            shieldTypes = stOv.types();
            if (!stOv.values().isEmpty()) {
                shieldValues = new LinkedHashMap<>();
                for (var typeEntry : stOv.values().entrySet()) {
                    shieldValues.putAll(typeEntry.getValue());
                }
            }
        } else {
            List<String> declared = ITEM_SHIELD_TYPES.get(itemId);
            if (declared != null) {
                shieldTypes = declared;
            }
        }
        if (mechanic == null && sets == null && values == null && shieldTypes == null && shieldValues == null) {
            return null;
        }
        return new ItemDefinition(mechanic, false, sets, shieldTypes, shieldValues, values, null);
    }

    /**
     * 编辑操作：为物品添加/移除指定特殊机制（set）。
     * 添加 = 当前集合 ∪ {set}；移除 = 当前集合 − {set}，移除后为空则精确撤销机制声明（mechanic=false）。
     * 写入统一 items 覆写 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateSpecialMechanicSet(MinecraftServer server, String itemId, String mechanicSet, boolean remove) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        List<String> current = new ArrayList<>(getEffectiveSpecialMechanicSets(server, itemId));
        // 保留该物品既有的机制数值覆盖（移除机制时同步删除对应数值）
        Map<String, Map<String, ParamValue>> values = copyExistingValues(itemId);
        List<String> updated = new ArrayList<>();
        if (remove) {
            for (String s : current) {
                if (!s.equals(mechanicSet)) {
                    updated.add(s);
                }
            }
            values.remove(mechanicSet);
        } else {
            updated.addAll(current);
            if (!updated.contains(mechanicSet)) {
                updated.add(mechanicSet);
            }
        }
        boolean declared = !remove || !updated.isEmpty();
        // 声明时 mechanic=true + sets 整体覆盖；全部移除时精确撤销 = mechanic:false（不误伤护盾定义）
        SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(declared ? Boolean.TRUE : Boolean.FALSE, false,
                declared ? updated : null,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                values,
                base != null ? base.attributes() : null));
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品某机制集合的数值覆盖（物品级数值，仅影响该物品）。
     * <p>
     * 目标物品必须已声明该机制集合（否则忽略）；空参数 map 表示清除该机制的数值覆盖（回退 Config 默认）。
     * stackables 与 params 平行（paramKey -> 是否可叠加），缺省视为可叠加。
     * 写入内存 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateSpecialMechanicValues(MinecraftServer server, String itemId,
                                                   String mechanicSet, Map<String, Double> params,
                                                   Map<String, Boolean> stackables) {
        List<String> current = getEffectiveSpecialMechanicSets(server, itemId);
        if (!current.contains(mechanicSet)) {
            return;
        }
        Map<String, Map<String, ParamValue>> values = copyExistingValues(itemId);
        if (params == null || params.isEmpty()) {
            values.remove(mechanicSet);
        } else {
            Map<String, ParamValue> paramValues = new LinkedHashMap<>();
            for (var e : params.entrySet()) {
                boolean stackable = stackables == null || stackables.getOrDefault(e.getKey(), Boolean.TRUE);
                paramValues.put(e.getKey(), new ParamValue(e.getValue(), stackable));
            }
            values.put(mechanicSet, paramValues);
        }
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(Boolean.TRUE, false, current,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                values,
                base != null ? base.attributes() : null));
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品护盾类型（写入内存 + 覆盖文件，立即重新加载生效，不重载数据包）。
     * 护盾全部按实例生效，独占/兼容设置已取消。
     * 既有的护盾数值覆盖被保留（拍平结构，无法按类型过滤时全保留）。
     */
    public static void updateShieldTypeOverride(MinecraftServer server, String itemId, List<String> types) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        Map<String, ParamValue> shieldValues = base != null ? base.shieldValues() : null;
        SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                new ArrayList<>(types),
                shieldValues,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null));
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品某护盾类型的数值覆盖（物品级数值，仅影响该物品实例）。
     * <p>
     * 目标物品必须已声明该护盾类型（否则忽略）；空参数 map 表示清除该类型的数值覆盖（回退 Config 默认）。
     * 护盾类型数值不参与叠/单合并（每物品实例独立），因此无 stackables 参数。
     * 写入内存 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateShieldTypeValues(MinecraftServer server, String itemId,
                                              String shieldType, Map<String, Double> params) {
        List<String> declared = ITEM_SHIELD_TYPES.get(itemId);
        if (declared == null || !declared.contains(shieldType)) {
            return;
        }
        ItemDefinition existing = SERVER_ITEM_OVERRIDES.get(itemId);
        if ((params == null || params.isEmpty()) && (existing == null || existing.shieldValues() == null)) {
            return; // 清除数值且无护盾数值覆盖：无需处理
        }
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        // shieldValues 按参数键拍平合并；params 为空表示清除该类型的全部数值覆盖（单类型现实）
        Map<String, ParamValue> shieldValues = base != null && base.shieldValues() != null
                ? new LinkedHashMap<>(base.shieldValues()) : new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            shieldValues.clear();
        } else {
            for (var e : params.entrySet()) {
                shieldValues.put(e.getKey(), new ParamValue(e.getValue(), false));
            }
        }
        SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                base != null ? base.shieldTypes() : null,
                shieldValues.isEmpty() ? null : shieldValues,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null));
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    // ======================== 物品属性编辑（统一 items 覆写，attributes 整体接管语义） ========================

    /**
     * 编辑操作：新增/更新物品属性（写入统一 items 覆写 + 覆盖文件，立即重新加载生效）。
     * <p>
     * 基线 = 当前生效属性（权威 + 覆写合并视图），整体写回覆写层（显式接管）；
     * 已有属性保留叠加设置，新增属性默认可叠加。权威属性物品由此获得覆写副本，
     * 后续删除操作能正确覆盖权威定义。
     */
    public static void updateItemAttribute(MinecraftServer server, String itemId, String attrName, double value) {
        Map<String, ParamValue> attrs = currentAttributeBaseline(itemId);
        ParamValue existing = attrs.get(attrName);
        attrs.put(attrName, new ParamValue(value, existing == null || existing.stackable()));
        writeItemAttributeOverride(server, itemId, attrs);
    }

    /**
     * 编辑操作：移除物品单条属性。剩余集（可为空）整体写回覆写层——
     * 显式空 map = 覆盖为无属性（权威属性也被删除）；属性本不存在时忽略。
     */
    public static void removeItemAttribute(MinecraftServer server, String itemId, String attrName) {
        Map<String, ParamValue> attrs = currentAttributeBaseline(itemId);
        if (attrs.remove(attrName) == null) {
            return;
        }
        writeItemAttributeOverride(server, itemId, attrs);
    }

    /**
     * 编辑操作：移除物品全部属性（UI 删除物品/清空属性）。
     * 写显式空接管（物品保留在配置列表、无属性行）；幂等。
     */
    public static void clearItemAttributes(MinecraftServer server, String itemId) {
        writeItemAttributeOverride(server, itemId, new LinkedHashMap<>());
    }

    /** 编辑操作：确保物品存在于配置列表（UI 添加物品；已声明属性时幂等忽略） */
    public static void ensureItemOverrideEntry(MinecraftServer server, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (getEffectiveItemAttributes(itemId) != null) {
            return;
        }
        writeItemAttributeOverride(server, itemId, new LinkedHashMap<>());
    }

    /** 当前生效属性基线副本（权威 + 覆写合并视图，编辑操作起点） */
    private static Map<String, ParamValue> currentAttributeBaseline(String itemId) {
        Map<String, ParamValue> attrs = getEffectiveItemAttributes(itemId);
        return attrs != null ? new LinkedHashMap<>(attrs) : new LinkedHashMap<>();
    }

    /** 属性整体接管写回（其余字段保留既有定义，不误伤机制/护盾声明） */
    private static void writeItemAttributeOverride(MinecraftServer server, String itemId,
                                                   Map<String, ParamValue> attributes) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                base != null ? base.values() : null,
                attributes));
        ITEM_UI_OWNED.add(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 应用运行时覆盖：读取覆盖文件 -> 重新加载定义（含覆盖合并）-> 触发 Config.applyDefs 及各子系统刷新（编辑后立即调用） */
    public static void applyOverrides(MinecraftServer server) {
        loadOverridesFromFile(server);
        loadFrom(server.registryAccess());
    }

    /** 重置运行时覆盖：清空内存覆盖 + 删除覆盖文件，恢复数据包默认定义（「恢复默认」按钮使用） */
    public static void resetOverrides(MinecraftServer server) {
        SERVER_ITEM_OVERRIDES.clear();
        ITEM_UI_OWNED.clear();
        SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        SERVER_SHIELD_TYPE_OVERRIDES.clear();
        try {
            Files.deleteIfExists(getOverridesFile(server));
        } catch (Exception e) {
            gytrinket.LOGGER.error("删除定义覆盖文件失败", e);
        }
        applyOverrides(server);
    }

    public static Set<String> getItemSet(String setName) {
        return ITEM_SETS.getOrDefault(setName, Set.of());
    }

    public static Set<String> getEntitySet(String setName) {
        return ENTITY_SETS.getOrDefault(setName, Set.of());
    }

    /** 获取所有声明为特殊机制的物品 id（special_mechanics 文件夹声明并集） */
    public static Set<String> getSpecialMechanicItems() {
        return SPECIAL_MECHANIC_ITEMS;
    }

    public static Map<String, Boolean> getShieldTypes() {
        return SHIELD_TYPES;
    }

    public static Map<String, List<String>> getItemShieldTypes() {
        return ITEM_SHIELD_TYPES;
    }

    public static List<AttributeEntry> getAttributeDefs() {
        return ATTRIBUTE_DEFS;
    }

    public static Map<String, Set<String>> getDisableTargets() {
        return DISABLE_TARGETS;
    }

    public static Map<String, Set<String>> getDependencies() {
        return DEPENDENCIES;
    }

    public static Map<String, Set<String>> getDisableCategories() {
        return DISABLE_CATEGORIES;
    }

    /** AND 依赖（OR 组）：物品 id -> 组列表，每组内 OR、组间 AND，组内元素可为 "category:xxx" 类别引用 */
    public static Map<String, List<List<String>>> getDependenciesAll() {
        return DEPENDENCIES_ALL;
    }

    /** 模块树：树 id -> 定义（含类别与分阶模块） */
    public static Map<String, ModuleTreeDef> getModuleTrees() {
        return MODULE_TREES;
    }

    /** 获取指定类别下所有模块树的终阶模块（最后一层的模块并集） */
    public static Set<String> getCategoryFinalModules(String category) {
        Set<String> result = new HashSet<>();
        for (ModuleTreeDef def : MODULE_TREES.values()) {
            if (!category.equals(def.category())) continue;
            if (def.tiers() == null || def.tiers().isEmpty()) continue;
            result.addAll(def.tiers().get(def.tiers().size() - 1));
        }
        return result;
    }

    public static Map<String, List<String>> getUpgradePaths() {
        return UPGRADE_PATHS;
    }

    // ===== 客户端查询（从同步后的 registryAccess 实时读取） =====

    /** 判断指定物品是否属于某个物品集合（客户端 tooltip 使用）：item_sets 文件夹 + 特殊机制路径 sets 声明 */
    public static boolean itemSetContains(RegistryAccess access, String setName, String itemId) {
        if (access == null) return false;
        // 1. item_sets 显式集合（不受覆盖层影响）
        Optional<Registry<ItemSetDef>> reg = access.registry(ITEM_SETS_KEY);
        if (reg.isPresent()) {
            for (Map.Entry<ResourceKey<ItemSetDef>, ItemSetDef> e : reg.get().entrySet()) {
                if (e.getKey().location().getPath().equals(setName) && e.getValue().items().contains(itemId)) {
                    return true;
                }
            }
        }
        // 2. 特殊机制声明（客户端覆盖层优先：面板编辑的机制立即反映到集合判定，tooltip 详细描述兼容覆写物品）
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return !ov.removed() && ov.sets().contains(setName);
        }
        // 3. 数据驱动：统一物品定义（item_definitions，mechanic 显式声明时接管机制）
        ItemDefinition dataDef = clientMergedItemDefinition(access, itemId);
        if (dataDef != null) {
            if (dataDef.removed()) {
                return false;
            }
            if (dataDef.mechanic() != null) {
                return dataDef.mechanic() && dataDef.sets() != null && dataDef.sets().contains(setName);
            }
            // mechanic=null：该定义不接管机制，继续走 special_mechanics 路径定义
        }
        Optional<Registry<SpecialMechanicDef>> smReg = access.registry(SPECIAL_MECHANICS_KEY);
        if (smReg.isPresent()) {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc != null) {
                SpecialMechanicDef def = smReg.get().get(loc);
                if (def != null && !def.removed() && def.sets().contains(setName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 客户端查询：物品是否声明为特殊机制（覆盖层优先，其次 item_definitions 显式声明，再次 special_mechanics 路径定义） */
    public static boolean clientIsSpecialMechanic(RegistryAccess access, String itemId) {
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return !ov.removed();
        }
        if (access == null) return false;
        // 数据驱动：统一物品定义（item_definitions，mechanic 显式声明时接管机制）
        ItemDefinition dataDef = clientMergedItemDefinition(access, itemId);
        if (dataDef != null) {
            if (dataDef.removed()) {
                return false;
            }
            if (dataDef.mechanic() != null) {
                return dataDef.mechanic();
            }
            // mechanic=null：该定义不接管机制，继续走 special_mechanics 路径定义
        }
        Optional<Registry<SpecialMechanicDef>> reg = access.registry(SPECIAL_MECHANICS_KEY);
        if (reg.isEmpty()) return false;
        for (Map.Entry<ResourceKey<SpecialMechanicDef>, SpecialMechanicDef> e : reg.get().entrySet()) {
            if (e.getValue().removed()) {
                continue;
            }
            if (e.getKey().location().toString().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客户端查询：物品声明为特殊机制时的机制名称列表。
     * <p>
     * 由该物品 special_mechanics 条目声明的 sets（客户端覆盖层优先），映射 tooltip_rules 的 titleKey 翻译得出；
     * 多个机制（多个 sets 均有标题）按顺序排列。未声明或无标题集合时返回空列表。
     */
    public static List<String> clientSpecialMechanicNames(RegistryAccess access, String itemId) {
        // 1. 取该物品声明的 sets（覆盖层 → item_definitions → special_mechanics，与 clientSpecialMechanicSets 同链）
        List<String> sets = clientSpecialMechanicSets(access, itemId);
        if (sets.isEmpty()) {
            return List.of();
        }
        // 2. 逐集合解析显示名（tooltip_rules 标题键优先，无条目则回退翻译集合名）
        List<String> names = new ArrayList<>();
        for (String set : sets) {
            names.add(clientMechanicDisplayName(access, set));
        }
        return names;
    }

    /** 客户端查询：所有可选的特殊机制集合名（仅 special_mechanics 路径定义声明的集合，覆盖层优先合并） */
    public static List<String> clientAllMechanicSets(RegistryAccess access) {
        if (access == null) return List.of();
        Set<String> sets = new LinkedHashSet<>();
        Optional<Registry<SpecialMechanicDef>> smReg = access.registry(SPECIAL_MECHANICS_KEY);
        if (smReg.isPresent()) {
            for (Map.Entry<ResourceKey<SpecialMechanicDef>, SpecialMechanicDef> e : smReg.get().entrySet()) {
                if (!e.getValue().removed()) {
                    sets.addAll(e.getValue().sets());
                }
            }
        }
        // 运行时覆盖层：面板添加/移除的机制同步进可选项
        for (SpecialMechanicOverride ov : CLIENT_SPECIAL_MECHANIC_OVERRIDES.values()) {
            if (!ov.removed()) {
                sets.addAll(ov.sets());
            }
        }
        return new ArrayList<>(sets);
    }

    /** 客户端查询：机制集合显示名（tooltip_rules titleKey 翻译；无条目则回退：去掉 _items 后缀按集合名翻译，再回退原集合名） */
    public static String clientMechanicDisplayName(RegistryAccess access, String mechanicSet) {
        if (mechanicSet == null || mechanicSet.isEmpty()) {
            return mechanicSet;
        }
        if (access != null) {
            Optional<Registry<TooltipRuleDef>> trReg = access.registry(TOOLTIP_RULES_KEY);
            if (trReg.isPresent()) {
                for (TooltipRuleDef rule : trReg.get()) {
                    if (mechanicSet.equals(rule.itemSet()) && rule.titleKey() != null && !rule.titleKey().isEmpty()) {
                        return translateMechanicTitle(rule.titleKey());
                    }
                }
            }
        }
        // 回退：去掉 _items 后缀按集合名翻译（无 tooltip_rules 条目的机制在 lang 中补充对应翻译键）
        String base = mechanicSet.endsWith("_items")
                ? mechanicSet.substring(0, mechanicSet.length() - "_items".length()) : mechanicSet;
        String key = "tooltip.gytrinket." + base;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? mechanicSet : translated;
    }

    /** 翻译机制标题键（tooltip.gytrinket.<titleKey>，缺翻译时回退原键名） */
    private static String translateMechanicTitle(String titleKey) {
        String key = "tooltip.gytrinket." + titleKey;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? titleKey : translated;
    }

    /** 客户端查询：物品当前生效的特殊机制集合（覆盖层优先，其次数据驱动声明） */
    public static List<String> clientSpecialMechanicSets(RegistryAccess access, String itemId) {
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return ov.removed() ? List.of() : List.copyOf(ov.sets());
        }
        // 数据驱动：统一物品定义（item_definitions，mechanic 显式声明时接管机制），其次 special_mechanics 路径定义
        if (access != null) {
            ItemDefinition dataDef = clientMergedItemDefinition(access, itemId);
            if (dataDef != null) {
                if (dataDef.removed()) {
                    return List.of();
                }
                if (dataDef.mechanic() != null) {
                    return dataDef.mechanic() ? List.copyOf(dataDef.sets() != null ? dataDef.sets() : List.of()) : List.of();
                }
                // mechanic=null：该定义不接管机制，继续走 special_mechanics 路径定义
            }
        }
        if (access == null) return List.of();
        Optional<Registry<SpecialMechanicDef>> smReg = access.registry(SPECIAL_MECHANICS_KEY);
        if (smReg.isPresent()) {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc != null) {
                SpecialMechanicDef def = smReg.get().get(loc);
                if (def != null && !def.removed()) {
                    return List.copyOf(def.sets());
                }
            }
        }
        return List.of();
    }

    /**
     * 客户端：合并数据包 item_definitions registry 中某物品的分层定义（按 key 排序，与服务端 loadFrom 同序）。
     * registry 不存在或物品无定义时返回 null。
     */
    private static ItemDefinition clientMergedItemDefinition(RegistryAccess access, String itemId) {
        return access.registry(ITEM_DEFINITIONS_KEY).map(reg -> {
            String norm = normalizeItemId(itemId);
            List<Map.Entry<ResourceKey<ItemDefinitionsFile>, ItemDefinitionsFile>> entries = new ArrayList<>(reg.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            ItemDefinition merged = null;
            for (var e : entries) {
                ItemDefinition def = e.getValue().items().get(norm);
                if (def != null) {
                    merged = merged == null ? def : ItemDefinition.merge(merged, def);
                }
            }
            return merged;
        }).orElse(null);
    }

    /**
     * 客户端：取某物品的生效属性映射（item_definitions registry 合并后的 attributes 段）。
     * <p>
     * 该注册表随数据包自动同步到客户端，不依赖自定义网络包；作为客户端静态属性表
     * （AttributeManager）未收到配置同步时的回退取值来源。access 为 null、无定义、
     * removed 或无 attributes 段时返回空表。
     */
    public static Map<String, Double> clientEffectiveItemAttributes(RegistryAccess access, String itemId) {
        if (access == null || itemId == null || itemId.isEmpty()) {
            return Map.of();
        }
        ItemDefinition def = clientMergedItemDefinition(access, itemId);
        if (def == null || def.removed() || def.attributes() == null || def.attributes().isEmpty()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        def.attributes().forEach((name, pv) -> result.put(name, pv.value()));
        return result;
    }

    /**
     * 客户端：解析玩家某机制参数的生效数值（与 {@link #resolveMechanicValue} 同一语义的客户端镜像）。
     * <p>
     * 供纯客户端效果读取（如幽灵机身移动衰减）：遍历本地装备物品逐件合并——可叠加求和、
     * 不可叠加比对取最大；未覆盖物品按 fallback（Config 默认）不可叠加参与。player 为 null 或未装备时返回 fallback。
     */
    public static double clientResolveMechanicValue(Player player, String mechanicSet, String paramKey, double fallback) {
        if (player == null) return fallback;
        return mergeMechanicValue(player.getUUID(), PlayerStoreUtils.getAllEquippedStacks(player),
                itemId -> clientSpecialMechanicSets(player.level() != null ? player.level().registryAccess() : null, itemId)
                        .contains(mechanicSet),
                itemId -> {
                    SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
                    return ov == null || ov.removed() ? null : ov.values();
                },
                mechanicSet, paramKey, fallback);
    }

    /** 客户端查询：护盾类型名 -> 是否兼容（shield_types 定义） */
    public static Map<String, Boolean> clientShieldTypes(RegistryAccess access) {
        if (access == null) return Map.of();
        Optional<Registry<ShieldTypeDef>> reg = access.registry(SHIELD_TYPES_KEY);
        if (reg.isEmpty()) return Map.of();
        Map<String, Boolean> result = new HashMap<>();
        for (Map.Entry<ResourceKey<ShieldTypeDef>, ShieldTypeDef> e : reg.get().entrySet()) {
            result.put(e.getKey().location().getPath(), e.getValue().compatible());
        }
        return result;
    }

    /** 获取物品的护盾类型列表（客户端 tooltip 使用，覆盖层优先） */
    public static List<String> clientItemShieldTypes(RegistryAccess access, String itemId) {
        ShieldTypeOverride ov = CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov != null) {
            return List.copyOf(ov.types());
        }
        if (access == null) return List.of();
        Optional<Registry<ItemShieldTypeDef>> reg = access.registry(ITEM_SHIELD_TYPES_KEY);
        if (reg.isPresent()) {
            for (ItemShieldTypeDef def : reg.get()) {
                if (def.item().equals(itemId)) {
                    return List.copyOf(def.types());
                }
            }
        }
        // 回退：物品定义（item_definitions/*.json）的 shieldTypes 声明——本项目数据只写在 item_definitions，
        // 服务端 loadFrom 从此派生 ITEM_SHIELD_TYPES，客户端需对齐同一链路
        return clientItemDefinitionShieldTypes(access, itemId);
    }

    /**
     * 从 item_definitions registry 解析物品的 shieldTypes 声明（客户端镜像服务端派生逻辑）：
     * 文件按 registry key 排序后遍历，后声明覆盖前声明；removed 撤销全部类型。
     */
    private static List<String> clientItemDefinitionShieldTypes(RegistryAccess access, String itemId) {
        Optional<Registry<ItemDefinitionsFile>> reg = access.registry(ITEM_DEFINITIONS_KEY);
        if (reg.isEmpty()) return List.of();
        List<Map.Entry<ResourceKey<ItemDefinitionsFile>, ItemDefinitionsFile>> entries =
                new ArrayList<>(reg.get().entrySet());
        entries.sort(Map.Entry.comparingByKey());
        String normalized = normalizeItemId(itemId);
        List<String> declared = null;
        for (var e : entries) {
            ItemDefinition def = e.getValue().items().get(normalized);
            if (def == null) continue;
            if (def.removed()) {
                declared = null;
                continue;
            }
            if (def.shieldTypes() != null) {
                declared = def.shieldTypes();
            }
        }
        if (declared == null || declared.isEmpty()) return List.of();
        return List.copyOf(declared);
    }

    /** 客户端查询：物品的护盾类型覆盖条目（无覆盖时返回 null，调用方回退类型级默认兼容性） */
    public static ShieldTypeOverride getClientShieldTypeOverride(String itemId) {
        return CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
    }

    /**
     * 客户端：按物品解析护盾类型参数生效值（tooltip 描述用）。
     * 有物品级覆盖取覆盖值，否则回退 fallback（Config 默认值）——
     * 与服务端 {@link #resolveShieldTypeValueForItem} 语义一致，
     * 保证物品描述显示的数值与实际生效数值相同
     */
    public static double clientShieldTypeValueForItem(String itemId, String shieldType,
                                                      String paramKey, double fallback) {
        ShieldTypeOverride ov = CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov == null) return fallback;
        Map<String, ParamValue> params = ov.values().get(shieldType);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /**
     * 客户端：按物品解析护盾实例基础参数生效值（tooltip 显示用）。
     * 查询顺序与服务端 {@link #resolveShieldParam} 的客户端可见层对齐：
     * UI 覆写（CLIENT_SHIELD_TYPE_OVERRIDES，同步自服务端 getServerShieldTypeOverrides：
     * items 统一结构转译段 + 旧护盾类型覆写段）→
     * 权威物品定义（item_definitions registry，按 key 排序合并，客户端镜像）→
     * 护盾类型定义默认值（shield_types registry，{@link #clientShieldTypeParamDefault}）→
     * fallback（调用方传入：{@link com.gytrinket.gytrinket.core.defs.ShieldValueDefs} 静态默认），
     * 保证物品描述显示的数值与实际生效数值同源
     */
    public static double clientResolveShieldParam(RegistryAccess access, String itemId, String shieldTypeName,
                                                  String paramKey, double fallback) {
        if (itemId == null) return fallback;
        // UI 覆写层：优先取声明类型组内的覆盖值，未命中再扫全部组（兼容旧段"任意类型组内同键参数"语义）
        ShieldTypeOverride ov = CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov != null && ov.values() != null) {
            ParamValue pv = null;
            if (shieldTypeName != null) {
                Map<String, ParamValue> params = ov.values().get(shieldTypeName);
                pv = params == null ? null : params.get(paramKey);
            }
            if (pv == null) {
                for (Map<String, ParamValue> params : ov.values().values()) {
                    ParamValue cand = params.get(paramKey);
                    if (cand != null) {
                        pv = cand;
                        break;
                    }
                }
            }
            if (pv != null) return pv.value();
        }
        if (access != null) {
            // 权威物品定义层：文件按 registry key 排序后遍历，后声明覆盖前声明（镜像服务端 loadFrom 合并链）
            Optional<Registry<ItemDefinitionsFile>> reg = access.registry(ITEM_DEFINITIONS_KEY);
            if (reg.isPresent()) {
                List<Map.Entry<ResourceKey<ItemDefinitionsFile>, ItemDefinitionsFile>> entries =
                        new ArrayList<>(reg.get().entrySet());
                entries.sort(Map.Entry.comparingByKey());
                String normalized = normalizeItemId(itemId);
                for (var e : entries) {
                    ItemDefinition def = e.getValue().items().get(normalized);
                    if (def == null) continue;
                    if (def.removed()) return fallback;
                    if (def.shieldValues() != null) {
                        ParamValue pv = def.shieldValues().get(paramKey);
                        if (pv != null) return pv.value();
                    }
                }
            }
            // 护盾类型定义默认值层（shield_types/*.json 的 shieldValues 段）
            Double typeDefault = clientShieldTypeParamDefault(access, shieldTypeName, paramKey);
            if (typeDefault != null) return typeDefault;
        }
        return fallback;
    }

    /** 客户端查询：物品的特殊机制覆盖条目（含机制集合与数值覆盖；无覆盖时返回 null） */
    public static SpecialMechanicOverride getClientSpecialMechanicOverride(String itemId) {
        return CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
    }

    /**
     * 客户端：按物品解析特殊机制参数生效值（tooltip/客户端模拟用）。
     * 有物品级覆盖取覆盖值，否则回退 fallback ——
     * 与服务端 {@link #resolveMechanicValueForItem} 语义一致，
     * 保证物品描述显示的数值与实际生效数值相同
     */
    public static double clientResolveMechanicValueForItem(String itemId, String mechanicSet,
                                                           String paramKey, double fallback) {
        if (itemId == null) return fallback;
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov == null || ov.removed()) return fallback;
        Map<String, ParamValue> params = ov.values().get(mechanicSet);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /** 客户端：从服务端同步的覆盖数据更新本地覆盖层（面板显示实时生效） */
    public static void setClientOverrides(Map<String, SpecialMechanicOverride> specialMechanics, Map<String, ShieldTypeOverride> shieldTypes) {
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.clear();
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.putAll(specialMechanics);
        CLIENT_SHIELD_TYPE_OVERRIDES.clear();
        CLIENT_SHIELD_TYPE_OVERRIDES.putAll(shieldTypes);
    }

    /**
     * 服务端：获取当前覆盖数据（供同步到客户端，过渡期将 items 统一结构转译回旧分段结构）。
     * items 条目优先（mechanic 非 null 才产生条目）；旧分段条目仅在未被 items 字段级接管时补充。
     */
    public static Map<String, SpecialMechanicOverride> getServerSpecialMechanicOverrides() {
        Map<String, SpecialMechanicOverride> result = new LinkedHashMap<>();
        for (var e : SERVER_ITEM_OVERRIDES.entrySet()) {
            ItemDefinition def = e.getValue();
            if (def.mechanic() == null) continue;
            if (def.mechanic()) {
                result.put(e.getKey(), SpecialMechanicOverride.declared(def.sets(), def.values()));
            } else {
                result.put(e.getKey(), SpecialMechanicOverride.removedState());
            }
        }
        for (var e : SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
            if (!result.containsKey(e.getKey()) && !itemOverrideOwnsMechanic(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /**
     * 服务端：获取当前覆盖数据（供同步到客户端，过渡期将 items 统一结构转译回旧分段结构）。
     * items 条目仅 shieldTypes 非 null 才产生条目（独占列表恒空，多实例语义已取代兼容设置）。
     */
    public static Map<String, ShieldTypeOverride> getServerShieldTypeOverrides() {
        Map<String, ShieldTypeOverride> result = new LinkedHashMap<>();
        for (var e : SERVER_ITEM_OVERRIDES.entrySet()) {
            ItemDefinition def = e.getValue();
            if (def.shieldTypes() == null) continue;
            Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
            if (def.shieldValues() != null) {
                for (String type : def.shieldTypes()) {
                    values.put(type, def.shieldValues());
                }
            }
            result.put(e.getKey(), new ShieldTypeOverride(new ArrayList<>(def.shieldTypes()), values));
        }
        for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            if (!result.containsKey(e.getKey()) && !itemOverrideOwnsShieldTypes(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /** 统一物品级覆盖的只读视图（p2 阶段 AttributeManager / 护盾多实例接线使用） */
    public static Map<String, ItemDefinition> getItemDefinitions() {
        return SERVER_ITEM_OVERRIDES;
    }

    /** 查询属性的组合方式（客户端 tooltip 格式化使用），未找到返回 null */
    public static AttributeType clientAttributeType(RegistryAccess access, String attrName) {
        if (access == null) return null;
        Optional<Registry<AttributeDefs>> reg = access.registry(ATTRIBUTE_DEFS_KEY);
        if (reg.isEmpty()) return null;
        for (AttributeDefs defs : reg.get()) {
            for (AttributeEntry e : defs.attributes()) {
                if (e.name().equals(attrName)) {
                    try {
                        return AttributeType.valueOf(e.combine());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /** 获取服务端加载的工具提示规则 */
    public static List<TooltipRuleDef> getTooltipRules() {
        return TOOLTIP_RULES;
    }

    /** 从客户端同步的 registry 读取工具提示规则 */
    public static List<TooltipRuleDef> clientTooltipRules(RegistryAccess access) {
        if (access == null) return List.of();
        Optional<Registry<TooltipRuleDef>> reg = access.registry(TOOLTIP_RULES_KEY);
        if (reg.isEmpty()) return List.of();
        List<TooltipRuleDef> result = new ArrayList<>();
        reg.get().forEach(result::add);
        return result;
    }
}
