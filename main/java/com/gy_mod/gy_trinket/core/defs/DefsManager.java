package com.gy_mod.gy_trinket.core.defs;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeType;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 定义类数据管理器（datapack 数据驱动）
 *
 * 将原先写在 TOML 中的"定义类"配置迁移到 datapack：
 * <pre>
 *   data/&lt;命名空间&gt;/gytrinket/item_sets/&lt;系统名&gt;.json            -- 物品/实体集合
 *   data/&lt;命名空间&gt;/gytrinket/shield_types/&lt;类型名&gt;.json          -- 护盾类型兼容性
 *   data/&lt;命名空间&gt;/gytrinket/item_shield_types/&lt;物品名&gt;.json      -- 物品-&gt;护盾类型
 *   data/&lt;命名空间&gt;/gytrinket/attribute_definitions/&lt;文件&gt;.json     -- 属性定义（多文件合并）
 *   data/&lt;命名空间&gt;/gytrinket/item_dependencies/&lt;物品名&gt;.json       -- 禁用/依赖关系
 *   data/&lt;命名空间&gt;/gytrinket/module_trees/&lt;树名&gt;.json             -- 模块树
 *   data/&lt;命名空间&gt;/gytrinket/upgrade_paths/&lt;基础物品名&gt;.json       -- 升级路径
 *   data/&lt;命名空间&gt;/gytrinket/tooltip_rules/&lt;规则名&gt;.json           -- 工具提示规则
 * </pre>
 *
 * Forge 1.20.1 没有 NeoForge 的 DataPackRegistry / registry 网络同步机制，因此：
 * - 服务端：在 {@link AddReloadListenerEvent} 注册数据包重载监听器，apply 阶段调用
 *   {@link #loadFrom(ResourceManager)} 直接读取 mod 内置的 datapack JSON 并填充静态缓存，
 *   最后调用 {@link Config#applyDefs()} 把结果灌入 Config 并触发依赖子系统重载（/reload 生效）。
 * - 客户端：Tooltip 等显示逻辑通过 {@link #itemSetContains(String, String)} 等方法读取静态缓存，
 *   首次访问时用 {@link net.minecraft.client.Minecraft#getResourceManager()} 惰性加载。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class DefsManager {

    private static final String REGISTRY_NAMESPACE = gytrinket.MODID;

    // ===== registry 键（保留，兼容引用；Forge 1.20.1 中不参与实际注册） =====
    public static final ResourceKey<net.minecraft.core.Registry<ItemSetDef>> ITEM_SETS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_sets"));
    public static final ResourceKey<net.minecraft.core.Registry<ShieldTypeDef>> SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "shield_types"));
    public static final ResourceKey<net.minecraft.core.Registry<ItemShieldTypeDef>> ITEM_SHIELD_TYPES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_shield_types"));
    public static final ResourceKey<net.minecraft.core.Registry<AttributeDefs>> ATTRIBUTE_DEFS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "attribute_definitions"));
    public static final ResourceKey<net.minecraft.core.Registry<ItemDependencyDef>> ITEM_DEPENDENCIES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "item_dependencies"));
    public static final ResourceKey<net.minecraft.core.Registry<ModuleTreeDef>> MODULE_TREES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "module_trees"));
    public static final ResourceKey<net.minecraft.core.Registry<UpgradePathDef>> UPGRADE_PATHS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "upgrade_paths"));
    public static final ResourceKey<net.minecraft.core.Registry<TooltipRuleDef>> TOOLTIP_RULES_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(REGISTRY_NAMESPACE, "tooltip_rules"));

    // ===== 数据模型 =====

    /** 物品集合条目：{ "items": [...], "entities": [...] }，条目 id = 系统名 */
    public record ItemSetDef(List<String> items, List<String> entities) {
        static final Codec<ItemSetDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(ItemSetDef::items),
                Codec.STRING.listOf().optionalFieldOf("entities", List.of()).forGetter(ItemSetDef::entities)
        ).apply(inst, ItemSetDef::new));
    }

    /** 护盾类型条目：{ "compatible": true|false }，条目 id = 类型名 */
    public record ShieldTypeDef(boolean compatible) {
        static final Codec<ShieldTypeDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("compatible", true).forGetter(ShieldTypeDef::compatible)
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

    // ===== 运行时覆盖层（绕过数据包验证：编辑写入独立 JSON，玩家手动「应用」生效） =====

    /** 覆盖文件（config/gytrinket/gytrinket_ui_overrides.json，位于数据包目录之外，不触发数据包校验/安全模式） */
    private static final String OVERRIDES_FILE_NAME = "gytrinket_ui_overrides.json";

    /** 机制参数覆盖值：数值 + 是否可叠加（false = 各物品独立比对取最大，true = 多物品求和） */
    public record ParamValue(double value, boolean stackable) {
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

    private static final Map<String, SpecialMechanicOverride> SERVER_SPECIAL_MECHANIC_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, ShieldTypeOverride> SERVER_SHIELD_TYPE_OVERRIDES = new ConcurrentHashMap<>();
    /** 客户端：从服务端同步的覆盖数据（面板显示用） */
    private static final Map<String, SpecialMechanicOverride> CLIENT_SPECIAL_MECHANIC_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, ShieldTypeOverride> CLIENT_SHIELD_TYPE_OVERRIDES = new ConcurrentHashMap<>();

    /** 护盾类型覆盖条目：类型列表 + 其中被标记为独占（不兼容）的类型子集（物品级兼容开关，UI 编辑产生）。
     *  values：护盾类型 -> (参数键 -> 覆盖值)，仅 UI 编辑产生；护盾类型数值为"每物品实例独立"，
     *  不参与叠/单合并（兼容=多实例并存各用各的数值，不兼容=冲突链决定生效实例），无覆盖的参数回退 Config 默认值 */
    public record ShieldTypeOverride(List<String> types, List<String> exclusiveTypes,
                                     Map<String, Map<String, Double>> values) {
        public ShieldTypeOverride(List<String> types, List<String> exclusiveTypes) {
            this(types, exclusiveTypes, Map.of());
        }
    }

    /** 声明为"特殊机制"的物品集合（special_mechanics 文件夹声明并集），供快速装备等统一判定 */
    private static final Set<String> SPECIAL_MECHANIC_ITEMS = ConcurrentHashMap.newKeySet();

    /** 物品声明的特殊机制集合（itemId -> 机制集合名列表；special_mechanics 定义，覆盖优先）。
     *  与 1.21.1 的 SPECIAL_MECHANICS registry 语义对齐：只含机制声明，不含 item_sets 普通集合 */
    private static final Map<String, List<String>> SPECIAL_MECHANIC_SETS = new ConcurrentHashMap<>();

    // ===== 加载缓存（服务端与客户端共用） =====
    private static final Map<String, Set<String>> ITEM_SETS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ENTITY_SETS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SHIELD_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ITEM_SHIELD_TYPES = new ConcurrentHashMap<>();
    /** 物品级护盾类型兼容覆盖（仅 UI 运行时覆盖层产生）：物品 -> 独占（不兼容）类型集合；存在条目 = 该物品兼容性为显式模式 */
    private static final Map<String, Set<String>> ITEM_SHIELD_TYPE_EXCLUSIVE_OVERRIDES = new ConcurrentHashMap<>();
    private static final List<AttributeEntry> ATTRIBUTE_DEFS = new ArrayList<>();
    private static final Map<String, Set<String>> DISABLE_TARGETS = new HashMap<>();
    private static final Map<String, Set<String>> DEPENDENCIES = new HashMap<>();
    private static final Map<String, Set<String>> DISABLE_CATEGORIES = new HashMap<>();
    private static final Map<String, List<List<String>>> DEPENDENCIES_ALL = new HashMap<>();
    private static final Map<String, ModuleTreeDef> MODULE_TREES = new LinkedHashMap<>();
    private static final Map<String, List<String>> UPGRADE_PATHS = new HashMap<>();
    private static final List<TooltipRuleDef> TOOLTIP_RULES = new CopyOnWriteArrayList<>();

    /** 客户端惰性加载标记 */
    private static boolean clientLoaded = false;

    private DefsManager() {}

    // ===== 服务端加载（forge 事件总线） =====
    @Mod.EventBusSubscriber(modid = gytrinket.MODID)
    public static class ReloadHandler {
        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                    return null;
                }

                @Override
                protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                    loadFrom(resourceManager);
                }
            });
        }
    }

    /**
     * 从资源管理器读取全部定义类 JSON 并填充静态缓存，最后调用 {@link Config#applyDefs()}。
     * 服务端在数据包重载（/reload）时调用；客户端在首次查询时惰性调用。
     */
    private static void loadFrom(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return;
        }
        // 资源管理器可能处于数据包尚未就绪的瞬时状态（例如客户端刚进入世界时）。
        // 此时找不到定义类数据；若已有缓存数据则保留，避免空结果清空后导致
        // 工具提示/禁用判定等机制全部失效。
        boolean hasItemSets = !resourceManager.listResources("gytrinket/item_sets", p -> p.getPath().endsWith(".json")).isEmpty();
        if (!hasItemSets && (!ITEM_SETS.isEmpty() || !TOOLTIP_RULES.isEmpty() || !SHIELD_TYPES.isEmpty())) {
            return;
        }
        ITEM_SETS.clear();
        ENTITY_SETS.clear();
        SPECIAL_MECHANIC_ITEMS.clear();
        SPECIAL_MECHANIC_SETS.clear();
        SHIELD_TYPES.clear();
        ITEM_SHIELD_TYPES.clear();
        ITEM_SHIELD_TYPE_EXCLUSIVE_OVERRIDES.clear();
        ATTRIBUTE_DEFS.clear();
        DISABLE_TARGETS.clear();
        DEPENDENCIES.clear();
        DISABLE_CATEGORIES.clear();
        DEPENDENCIES_ALL.clear();
        MODULE_TREES.clear();
        UPGRADE_PATHS.clear();
        TOOLTIP_RULES.clear();

        // 物品集合
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_sets", p -> p.getPath().endsWith(".json")).entrySet()) {
            String id = fileId(e.getKey());
            ItemSetDef def = parseResource(e.getValue(), ItemSetDef.CODEC);
            if (def == null) continue;
            if (!def.items().isEmpty()) {
                ITEM_SETS.put(id, new HashSet<>(def.items()));
            }
            if (!def.entities().isEmpty()) {
                ENTITY_SETS.put(id, new HashSet<>(def.entities()));
            }
        }

        // 护盾类型
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/shield_types", p -> p.getPath().endsWith(".json")).entrySet()) {
            ShieldTypeDef def = parseResource(e.getValue(), ShieldTypeDef.CODEC);
            if (def != null) {
                SHIELD_TYPES.put(fileId(e.getKey()), def.compatible());
            }
        }

        // 物品->护盾类型
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_shield_types", p -> p.getPath().endsWith(".json")).entrySet()) {
            ItemShieldTypeDef def = parseResource(e.getValue(), ItemShieldTypeDef.CODEC);
            if (def != null) {
                ITEM_SHIELD_TYPES.put(def.item(), new ArrayList<>(def.types()));
            }
        }

        // 运行时覆盖：护盾类型以覆盖为准（覆盖条目无论独占集合是否为空都写入，空集合 = 显式全部兼容）
        for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            ITEM_SHIELD_TYPES.put(e.getKey(), new ArrayList<>(e.getValue().types()));
            ITEM_SHIELD_TYPE_EXCLUSIVE_OVERRIDES.put(e.getKey(), new HashSet<>(e.getValue().exclusiveTypes()));
        }

        // 特殊机制（路径定义 + 分类声明）：文件名 = 物品 id（完整注册名，带命名空间；快速装备/判定用）
        // 运行时覆盖（SERVER_SPECIAL_MECHANIC_OVERRIDES）优先：removed 撤销声明，sets 覆盖分类
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/special_mechanics", p -> p.getPath().endsWith(".json")).entrySet()) {
            SpecialMechanicDef def = parseResource(e.getValue(), SpecialMechanicDef.CODEC);
            if (def == null) continue;
            // 必须带命名空间前缀（如 gytrinket:journey_module），否则与物品注册名/覆盖 key 不匹配
            String itemId = e.getKey().getNamespace() + ":" + fileId(e.getKey());
            SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
            if (ov != null) {
                if (ov.removed()) {
                    continue;
                }
                SPECIAL_MECHANIC_ITEMS.add(itemId);
                SPECIAL_MECHANIC_SETS.put(itemId, new ArrayList<>(ov.sets()));
                for (String setName : ov.sets()) {
                    if (setName == null || setName.isEmpty()) continue;
                    ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                }
                continue;
            }
            if (def.removed()) {
                continue;
            }
            SPECIAL_MECHANIC_ITEMS.add(itemId);
            SPECIAL_MECHANIC_SETS.put(itemId, new ArrayList<>(def.sets()));
            for (String setName : def.sets()) {
                if (setName == null || setName.isEmpty()) continue;
                ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
            }
        }

        // 运行时覆盖：新增 JAR/资源中不存在的条目
        for (var e : SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
            if (SPECIAL_MECHANIC_ITEMS.contains(e.getKey()) || e.getValue().removed()) {
                continue;
            }
            SPECIAL_MECHANIC_ITEMS.add(e.getKey());
            SPECIAL_MECHANIC_SETS.put(e.getKey(), new ArrayList<>(e.getValue().sets()));
            for (String setName : e.getValue().sets()) {
                if (setName == null || setName.isEmpty()) continue;
                ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(e.getKey());
            }
        }

        // 属性定义（多文件合并）
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/attribute_definitions", p -> p.getPath().endsWith(".json")).entrySet()) {
            AttributeDefs defs = parseResource(e.getValue(), AttributeDefs.CODEC);
            if (defs != null) {
                ATTRIBUTE_DEFS.addAll(defs.attributes());
            }
        }

        // 禁用/依赖
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_dependencies", p -> p.getPath().endsWith(".json")).entrySet()) {
            ItemDependencyDef def = parseResource(e.getValue(), ItemDependencyDef.CODEC);
            if (def == null) continue;
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

        // 模块树
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/module_trees", p -> p.getPath().endsWith(".json")).entrySet()) {
            ModuleTreeDef def = parseResource(e.getValue(), ModuleTreeDef.CODEC);
            if (def != null) {
                MODULE_TREES.put(fileId(e.getKey()), def);
            }
        }

        // 升级路径
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/upgrade_paths", p -> p.getPath().endsWith(".json")).entrySet()) {
            UpgradePathDef def = parseResource(e.getValue(), UpgradePathDef.CODEC);
            if (def != null) {
                UPGRADE_PATHS.put(def.base(), new ArrayList<>(def.upgrades()));
            }
        }

        // 工具提示规则
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/tooltip_rules", p -> p.getPath().endsWith(".json")).entrySet()) {
            TooltipRuleDef def = parseResource(e.getValue(), TooltipRuleDef.CODEC);
            if (def != null) {
                TOOLTIP_RULES.add(def);
            }
        }

        gytrinket.LOGGER.info("定义类数据加载完成：物品集合 {} 项，护盾类型 {} 项，物品护盾类型 {} 项，属性定义 {} 项，禁用目标 {} 项，依赖 {} 项，类别禁用 {} 项，AND依赖 {} 项，模块树 {} 棵，升级路径 {} 项，工具提示规则 {} 项",
                ITEM_SETS.size(), SHIELD_TYPES.size(), ITEM_SHIELD_TYPES.size(),
                ATTRIBUTE_DEFS.size(), DISABLE_TARGETS.size(), DEPENDENCIES.size(),
                DISABLE_CATEGORIES.size(), DEPENDENCIES_ALL.size(), MODULE_TREES.size(), UPGRADE_PATHS.size(), TOOLTIP_RULES.size());

        // 仅当加载到实际数据时才视为完成；否则保持可重试（客户端资源管理器可能尚未就绪）
        clientLoaded = !ITEM_SETS.isEmpty() || !SHIELD_TYPES.isEmpty() || !TOOLTIP_RULES.isEmpty() || !ATTRIBUTE_DEFS.isEmpty();

        // 填充 Config 集合并触发依赖定义数据的子系统重载
        Config.applyDefs();
    }

    /** 从资源 key 提取文件名（去掉目录与 .json 后缀） */
    private static String fileId(ResourceLocation key) {
        String path = key.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    /** 用 Codec 解析单个 JSON 资源，失败返回 null */
    private static <T> T parseResource(Resource resource, Codec<T> codec) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return codec.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> gytrinket.LOGGER.error("定义类数据解析失败: {}", err))
                    .orElse(null);
        } catch (Exception e) {
            gytrinket.LOGGER.error("读取定义类数据失败", e);
            return null;
        }
    }

    /** 客户端惰性加载：首次查询时从客户端资源管理器读取 */
    private static void ensureClientLoaded() {
        if (clientLoaded) {
            return;
        }
        clientLoaded = true;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                loadFrom(mc.getResourceManager());
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("客户端定义类数据加载失败", e);
        }
    }

    // ===== 查询（服务端与客户端共用，读静态缓存） =====

    public static Set<String> getItemSet(String setName) {
        return ITEM_SETS.getOrDefault(setName, Set.of());
    }

    public static Set<String> getEntitySet(String setName) {
        return ENTITY_SETS.getOrDefault(setName, Set.of());
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

    /** 获取已加载的工具提示规则 */
    public static List<TooltipRuleDef> getTooltipRules() {
        return TOOLTIP_RULES;
    }

    // ===== 客户端查询（只读不可变快照，对齐 1.21.1 的不可变注册表 + 覆盖层合并语义） =====

    /**
     * 客户端定义数据不可变快照。
     * 由网络线程在 {@link #applyClientSync} 时一次性构建并原子发布；
     * 渲染线程（tooltip/面板）只读该快照，从根本上杜绝 ConcurrentModificationException。
     */
    private static final class ClientSnapshot {
        final Map<String, Boolean> shieldTypes;
        final Set<String> specialMechanicItems;
        final Map<String, Set<String>> effectiveSets;
        final List<TooltipRuleDef> tooltipRules;
        final List<AttributeEntry> attributeDefs;
        final Map<String, SpecialMechanicOverride> smOverrides;
        final Map<String, ShieldTypeOverride> stOverrides;
        final Map<String, List<String>> itemShieldTypes;
        final Map<String, Set<String>> itemSets;

        ClientSnapshot(Map<String, Boolean> shieldTypes,
                       Set<String> specialMechanicItems,
                       Map<String, Set<String>> effectiveSets,
                       List<TooltipRuleDef> tooltipRules,
                       List<AttributeEntry> attributeDefs,
                       Map<String, SpecialMechanicOverride> smOverrides,
                       Map<String, ShieldTypeOverride> stOverrides,
                       Map<String, List<String>> itemShieldTypes,
                       Map<String, Set<String>> itemSets) {
            this.shieldTypes = shieldTypes;
            this.specialMechanicItems = specialMechanicItems;
            this.effectiveSets = effectiveSets;
            this.tooltipRules = tooltipRules;
            this.attributeDefs = attributeDefs;
            this.smOverrides = smOverrides;
            this.stOverrides = stOverrides;
            this.itemShieldTypes = itemShieldTypes;
            this.itemSets = itemSets;
        }
    }

    /** 客户端不可变快照（原子发布；null = 尚未同步，回退读静态缓存） */
    private static final AtomicReference<ClientSnapshot> CLIENT_SNAPSHOT = new AtomicReference<>();

    /** 判断指定物品是否属于某个物品集合（客户端 tooltip 使用，快照优先） */
    public static boolean itemSetContains(String setName, String itemId) {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            Set<String> effective = snap.effectiveSets.get(itemId);
            if (effective != null && effective.contains(setName)) {
                return true;
            }
            return snap.itemSets.getOrDefault(setName, Set.of()).contains(itemId);
        }
        // 快照未构建（尚未同步）：回退静态缓存（联机客户端此时数据为空，单人可读到服务端数据）
        Set<String> eff2 = CLIENT_EFFECTIVE_SETS.get(itemId);
        if (eff2 != null && eff2.contains(setName)) {
            return true;
        }
        return ITEM_SETS.getOrDefault(setName, Set.of()).contains(itemId);
    }

    /** 获取物品的护盾类型列表（客户端 tooltip 使用，覆盖层优先） */
    public static List<String> clientItemShieldTypes(String itemId) {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            if (snap.stOverrides.containsKey(itemId)) {
                return List.copyOf(snap.stOverrides.get(itemId).types());
            }
            return snap.itemShieldTypes.getOrDefault(itemId, List.of());
        }
        ShieldTypeOverride ov = CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov != null) {
            return List.copyOf(ov.types());
        }
        return ITEM_SHIELD_TYPES.getOrDefault(itemId, List.of());
    }

    /** 客户端查询：物品级护盾类型覆盖条目（含独占集合），无覆盖返回 null */
    public static ShieldTypeOverride getClientShieldTypeOverride(String itemId) {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return snap.stOverrides.get(itemId);
        }
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
        ShieldTypeOverride ov = getClientShieldTypeOverride(itemId);
        if (ov == null) return fallback;
        Map<String, Double> params = ov.values().get(shieldType);
        Double v = params == null ? null : params.get(paramKey);
        return v != null ? v : fallback;
    }

    /** 客户端查询：物品的特殊机制覆盖条目（含机制集合与数值覆盖；无覆盖时返回 null） */
    public static SpecialMechanicOverride getClientSpecialMechanicOverride(String itemId) {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return snap.smOverrides.get(itemId);
        }
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
        SpecialMechanicOverride ov = getClientSpecialMechanicOverride(itemId);
        if (ov == null || ov.removed()) return fallback;
        Map<String, ParamValue> params = ov.values().get(mechanicSet);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /** 服务端查询：物品级独占（不兼容）类型集合覆盖（供 Config.applyDefs 填充物品级兼容缓存） */
    public static Map<String, Set<String>> getItemShieldTypeExclusiveOverrides() {
        return ITEM_SHIELD_TYPE_EXCLUSIVE_OVERRIDES;
    }

    /** 查询属性的组合方式（客户端 tooltip 格式化使用），未找到返回 null */
    public static AttributeType clientAttributeType(String attrName) {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        List<AttributeEntry> defs = snap != null ? snap.attributeDefs : ATTRIBUTE_DEFS;
        for (AttributeEntry e : defs) {
            if (e.name().equals(attrName)) {
                try {
                    return AttributeType.valueOf(e.combine());
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 读取工具提示规则（客户端 tooltip 使用） */
    public static List<TooltipRuleDef> clientTooltipRules() {
        ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        return snap != null ? snap.tooltipRules : TOOLTIP_RULES;
    }

    // ===== 运行时覆盖层：文件读写与生效 =====

    /** 覆写定义文件路径：config/gytrinket/gytrinket_ui_overrides.json（全局持久化，所有世界共享） */
    private static Path getOverridesFile(MinecraftServer server) {
        return FMLPaths.CONFIGDIR.get().resolve("gytrinket").resolve(OVERRIDES_FILE_NAME);
    }

    /** 读取覆盖文件到服务端内存（不存在时忽略） */
    private static void loadOverridesFromFile(MinecraftServer server) {
        SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        SERVER_SHIELD_TYPE_OVERRIDES.clear();
        Path file = getOverridesFile(server);
        if (!Files.exists(file)) {
            return;
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
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
                }
            }
            if (root.has("shieldTypes")) {
                for (var e : root.getAsJsonObject("shieldTypes").entrySet()) {
                    SERVER_SHIELD_TYPE_OVERRIDES.put(e.getKey(), parseShieldTypeOverride(e.getValue()));
                }
            }
            gytrinket.LOGGER.info("已读取定义覆盖文件：特殊机制 {} 项，护盾类型 {} 项",
                    SERVER_SPECIAL_MECHANIC_OVERRIDES.size(), SERVER_SHIELD_TYPE_OVERRIDES.size());
        } catch (Exception e) {
            gytrinket.LOGGER.error("读取定义覆盖文件失败: {}", file, e);
        }
    }

    /** 解析机制数值覆盖段：values={set: {param: value}} + stackables={set: {param: bool}}；非法值跳过（回退默认），缺省叠加标记视为可叠加（兼容旧格式） */
    private static Map<String, Map<String, ParamValue>> parseMechanicValues(com.google.gson.JsonObject def) {
        Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
        if (!def.has("values") || !def.get("values").isJsonObject()) {
            return values;
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
                    // 非法数值：跳过该参数（运行时回退 Config 默认值）
                }
            }
            if (!params.isEmpty()) {
                values.put(setEntry.getKey(), params);
            }
        }
        return values;
    }

    /** 解析护盾类型覆盖条目：兼容旧格式（字符串数组，无独占集合）与新格式（{types:[], exclusiveTypes:[], values:{}} 对象） */
    private static ShieldTypeOverride parseShieldTypeOverride(com.google.gson.JsonElement el) {
        if (el.isJsonArray()) {
            List<String> types = new ArrayList<>();
            el.getAsJsonArray().forEach(t -> types.add(t.getAsString()));
            return new ShieldTypeOverride(types, List.of());
        }
        com.google.gson.JsonObject def = el.getAsJsonObject();
        List<String> types = new ArrayList<>();
        if (def.has("types") && def.get("types").isJsonArray()) {
            def.getAsJsonArray("types").forEach(t -> types.add(t.getAsString()));
        }
        List<String> exclusive = new ArrayList<>();
        if (def.has("exclusiveTypes") && def.get("exclusiveTypes").isJsonArray()) {
            def.getAsJsonArray("exclusiveTypes").forEach(t -> exclusive.add(t.getAsString()));
        }
        return new ShieldTypeOverride(types, exclusive, parseShieldValues(def));
    }

    /** 解析护盾数值覆盖段：values={type: {param: value}}；非法值跳过（回退 Config 默认值） */
    private static Map<String, Map<String, Double>> parseShieldValues(com.google.gson.JsonObject def) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        if (!def.has("values") || !def.get("values").isJsonObject()) {
            return values;
        }
        for (var typeEntry : def.getAsJsonObject("values").entrySet()) {
            if (!typeEntry.getValue().isJsonObject()) continue;
            Map<String, Double> params = new LinkedHashMap<>();
            for (var paramEntry : typeEntry.getValue().getAsJsonObject().entrySet()) {
                try {
                    params.put(paramEntry.getKey(), paramEntry.getValue().getAsDouble());
                } catch (Exception ignored) {
                    // 非法数值：跳过该参数（运行时回退 Config 默认值）
                }
            }
            if (!params.isEmpty()) {
                values.put(typeEntry.getKey(), params);
            }
        }
        return values;
    }

    /** 将服务端内存中的覆盖数据写入覆盖文件（编辑操作持久化，不触发重载） */
    private static void saveOverridesToFile(MinecraftServer server) {
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonObject sm = new com.google.gson.JsonObject();
            for (var e : SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
                com.google.gson.JsonObject def = new com.google.gson.JsonObject();
                def.addProperty("removed", e.getValue().removed());
                com.google.gson.JsonArray sets = new com.google.gson.JsonArray();
                e.getValue().sets().forEach(sets::add);
                def.add("sets", sets);
                Map<String, Map<String, ParamValue>> values = e.getValue().values();
                if (values != null && !values.isEmpty()) {
                    com.google.gson.JsonObject valuesJson = new com.google.gson.JsonObject();
                    com.google.gson.JsonObject stackablesJson = new com.google.gson.JsonObject();
                    for (var setEntry : values.entrySet()) {
                        com.google.gson.JsonObject paramsJson = new com.google.gson.JsonObject();
                        com.google.gson.JsonObject setStackables = new com.google.gson.JsonObject();
                        boolean anyNonStackable = false;
                        for (var paramEntry : setEntry.getValue().entrySet()) {
                            paramsJson.addProperty(paramEntry.getKey(), paramEntry.getValue().value());
                            setStackables.addProperty(paramEntry.getKey(), paramEntry.getValue().stackable());
                            if (!paramEntry.getValue().stackable()) {
                                anyNonStackable = true;
                            }
                        }
                        valuesJson.add(setEntry.getKey(), paramsJson);
                        if (anyNonStackable) {
                            stackablesJson.add(setEntry.getKey(), setStackables);
                        }
                    }
                    def.add("values", valuesJson);
                    if (stackablesJson.size() > 0) {
                        def.add("stackables", stackablesJson);
                    }
                }
                sm.add(e.getKey(), def);
            }
            root.add("specialMechanics", sm);
            com.google.gson.JsonObject st = new com.google.gson.JsonObject();
            for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
                com.google.gson.JsonObject def = new com.google.gson.JsonObject();
                com.google.gson.JsonArray types = new com.google.gson.JsonArray();
                e.getValue().types().forEach(types::add);
                def.add("types", types);
                com.google.gson.JsonArray exclusive = new com.google.gson.JsonArray();
                e.getValue().exclusiveTypes().forEach(exclusive::add);
                def.add("exclusiveTypes", exclusive);
                Map<String, Map<String, Double>> shieldValues = e.getValue().values();
                if (shieldValues != null && !shieldValues.isEmpty()) {
                    com.google.gson.JsonObject valuesJson = new com.google.gson.JsonObject();
                    for (var ve : shieldValues.entrySet()) {
                        com.google.gson.JsonObject paramsJson = new com.google.gson.JsonObject();
                        ve.getValue().forEach(paramsJson::addProperty);
                        valuesJson.add(ve.getKey(), paramsJson);
                    }
                    def.add("values", valuesJson);
                }
                st.add(e.getKey(), def);
            }
            root.add("shieldTypes", st);
            Path file = getOverridesFile(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            gytrinket.LOGGER.info("已保存定义覆盖文件: {}", file);
        } catch (Exception e) {
            gytrinket.LOGGER.error("保存定义覆盖文件失败", e);
        }
    }

    /** 编辑操作：更新特殊机制声明（写入内存 + 覆盖文件，立即重新加载生效，不重载数据包） */
    public static void updateSpecialMechanicOverride(MinecraftServer server, String itemId, boolean removed) {
        if (removed) {
            SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.removedState());
        } else {
            SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(List.of()));
        }
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 物品当前生效的特殊机制集合（覆盖优先，其次数据驱动声明）。
     *  与 1.21.1 语义一致：按物品 id 直接查它的机制声明（special_mechanics 路径定义），
     *  而非遍历 ITEM_SETS——避免把 item_sets 普通集合（如 primary_shield_amplification_items）误当机制 */
    public static List<String> getEffectiveSpecialMechanicSets(String itemId) {
        SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return new ArrayList<>(ov.removed() ? List.of() : ov.sets());
        }
        List<String> sets = SPECIAL_MECHANIC_SETS.get(itemId);
        return sets == null ? new ArrayList<>() : new ArrayList<>(sets);
    }

    /**
     * 服务端：玩家是否装备了声明指定特殊机制集合的物品（覆盖层优先）。
     */
    public static boolean playerHasEquippedMechanic(MinecraftServer server, UUID playerUUID, String mechanicSet) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet)) {
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
     * server==null 时直接回退（客户端上下文防御）。
     */
    public static double resolveMechanicValue(MinecraftServer server, UUID playerUUID, String mechanicSet, String paramKey, double fallback) {
        if (server == null) return fallback;
        return mergeMechanicValue(playerUUID, PlayerStoreUtils.getEquippedStacks(playerUUID),
                itemId -> getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet),
                SERVER_SPECIAL_MECHANIC_OVERRIDES::get, mechanicSet, paramKey, fallback);
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
            if (getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet)) {
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
    public static double resolveMechanicValueForItem(MinecraftServer server, String itemId, String mechanicSet, String paramKey, double fallback) {
        if (server == null || itemId == null) return fallback;
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
                                             Function<String, SpecialMechanicOverride> overrideOf,
                                             String mechanicSet, String paramKey, double fallback) {
        double stackableSum = 0.0D;
        double bestNonStackable = Double.NEGATIVE_INFINITY;
        boolean hasProvider = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!hasMechanicSet.test(itemId)) continue;
            hasProvider = true;
            double value = fallback;
            boolean stackable = false;
            SpecialMechanicOverride ov = overrideOf.apply(itemId);
            if (ov != null && !ov.removed()) {
                Map<String, ParamValue> params = ov.values().get(mechanicSet);
                ParamValue pv = params == null ? null : params.get(paramKey);
                if (pv != null) {
                    value = pv.value();
                    stackable = pv.stackable();
                }
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

    /** 拷贝物品既有的机制数值覆盖（编辑操作前保留其他机制的数值，内层 map 浅拷贝） */
    private static Map<String, Map<String, ParamValue>> copyExistingValues(String itemId) {
        Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
        SpecialMechanicOverride existing = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (existing != null && !existing.removed()) {
            for (var e : existing.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }
        return values;
    }

    /**
     * 编辑操作：为物品添加/移除指定特殊机制（set）。
     * 添加 = 当前集合 ∪ {set}；移除 = 当前集合 − {set}，移除后为空则整体撤销声明。
     */
    public static void updateSpecialMechanicSet(MinecraftServer server, String itemId, String mechanicSet, boolean remove) {
        List<String> current = getEffectiveSpecialMechanicSets(itemId);
        // 保留既有机制数值覆盖：加/移 set 不影响其他机制的数值；移除 set 时同步删除其数值
        Map<String, Map<String, ParamValue>> values = copyExistingValues(itemId);
        List<String> updated = new ArrayList<>();
        if (remove) {
            for (String s : current) {
                if (!s.equals(mechanicSet)) {
                    updated.add(s);
                }
            }
            values.remove(mechanicSet);
            if (updated.isEmpty()) {
                SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.removedState());
            } else {
                SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(updated, values));
            }
        } else {
            updated.addAll(current);
            if (!updated.contains(mechanicSet)) {
                updated.add(mechanicSet);
            }
            SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(updated, values));
        }
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品某机制集合的数值覆盖（物品级数值，仅 UI 产生）。
     * params 为 null/空 = 清除该机制的数值覆盖（全部回退 Config 默认值）；
     * stackables 与 params 平行（paramKey -> 是否可叠加），缺省视为可叠加；
     * 物品未声明该机制集合时忽略。
     */
    public static void updateSpecialMechanicValues(MinecraftServer server, String itemId, String mechanicSet, Map<String, Double> params, Map<String, Boolean> stackables) {
        SpecialMechanicOverride current = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        List<String> sets = current != null && !current.removed() ? current.sets() : getEffectiveSpecialMechanicSets(itemId);
        if (!sets.contains(mechanicSet)) {
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
        SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(sets, values));
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 编辑操作：更新物品护盾类型（写入内存 + 覆盖文件，立即重新加载生效，不重载数据包）；独占集合必须为类型子集。
     *  保留既有护盾数值覆盖；不再声明的类型同步删除其数值 */
    public static void updateShieldTypeOverride(MinecraftServer server, String itemId, List<String> types, List<String> exclusiveTypes) {
        List<String> exclusive = new ArrayList<>(exclusiveTypes);
        exclusive.removeIf(t -> !types.contains(t));
        Map<String, Map<String, Double>> values = copyExistingShieldValues(itemId);
        values.keySet().removeIf(t -> !types.contains(t));
        SERVER_SHIELD_TYPE_OVERRIDES.put(itemId, new ShieldTypeOverride(new ArrayList<>(types), exclusive, values));
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 拷贝物品既有的护盾数值覆盖（编辑操作前保留其他类型的数值，内层 map 浅拷贝） */
    private static Map<String, Map<String, Double>> copyExistingShieldValues(String itemId) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        ShieldTypeOverride existing = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (existing != null && existing.values() != null) {
            for (var e : existing.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }
        return values;
    }

    /**
     * 编辑操作：更新物品某护盾类型的数值覆盖（物品级数值，仅 UI 产生）。
     * params 为 null/空 = 清除该护盾类型的数值覆盖（全部回退 Config 默认值）；
     * 物品未声明该护盾类型时忽略。护盾类型数值为"每物品实例独立"，不参与叠/单合并。
     */
    public static void updateShieldTypeValues(MinecraftServer server, String itemId, String shieldType, Map<String, Double> params) {
        List<String> currentTypes = ITEM_SHIELD_TYPES.getOrDefault(itemId, List.of());
        if (!currentTypes.contains(shieldType)) {
            return;
        }
        ShieldTypeOverride current = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        Map<String, Map<String, Double>> values = copyExistingShieldValues(itemId);
        if (params == null || params.isEmpty()) {
            values.remove(shieldType);
        } else {
            values.put(shieldType, new LinkedHashMap<>(params));
        }
        SERVER_SHIELD_TYPE_OVERRIDES.put(itemId, new ShieldTypeOverride(
                current != null ? current.types() : new ArrayList<>(currentTypes),
                current != null ? current.exclusiveTypes() : List.of(),
                values));
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 服务端：解析某物品实例在指定护盾类型上的参数生效值（物品级覆盖，不跨物品合并）。
     * 物品未覆盖该参数时返回 fallback（调用方传入 Config 默认值）。server==null 时直接回退（客户端上下文防御）。
     */
    public static double resolveShieldTypeValueForItem(MinecraftServer server, String itemId, String shieldType, String paramKey, double fallback) {
        if (server == null) return fallback;
        ShieldTypeOverride ov = SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov == null || ov.values() == null) return fallback;
        Map<String, Double> params = ov.values().get(shieldType);
        if (params == null) return fallback;
        Double v = params.get(paramKey);
        return v == null ? fallback : v;
    }

    /** 编辑操作：移除物品护盾类型覆盖（恢复数据包默认，仅该物品） */
    public static void removeShieldTypeOverride(MinecraftServer server, String itemId) {
        SERVER_SHIELD_TYPE_OVERRIDES.remove(itemId);
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 应用运行时覆盖：读取覆盖文件 -> 重新加载定义（含覆盖合并）-> 触发 Config.applyDefs 及各子系统刷新（编辑后立即调用） */
    public static void applyOverrides(MinecraftServer server) {
        loadOverridesFromFile(server);
        loadFrom(server.getResourceManager());
    }

    /** 重置运行时覆盖：清空内存覆盖 + 删除覆盖文件，恢复数据包默认定义（「恢复默认」按钮使用） */
    public static void resetOverrides(MinecraftServer server) {
        SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        SERVER_SHIELD_TYPE_OVERRIDES.clear();
        try {
            Files.deleteIfExists(getOverridesFile(server));
        } catch (Exception e) {
            gytrinket.LOGGER.error("删除定义覆盖文件失败", e);
        }
        applyOverrides(server);
    }

    /** 获取所有声明为特殊机制的物品 id（special_mechanics 文件夹声明并集） */
    public static Set<String> getSpecialMechanicItems() {
        return SPECIAL_MECHANIC_ITEMS;
    }

    // ===== 客户端查询（只读不可变快照） =====

    /** 客户端查询：物品是否声明为特殊机制（覆盖层优先） */
    public static boolean clientIsSpecialMechanic(String itemId) {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            SpecialMechanicOverride ov = snap.smOverrides.get(itemId);
            if (ov != null) {
                return !ov.removed();
            }
            return snap.specialMechanicItems.contains(itemId);
        }
        SpecialMechanicOverride ov2 = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov2 != null) {
            return !ov2.removed();
        }
        return SPECIAL_MECHANIC_ITEMS.contains(itemId);
    }

    /** 客户端查询：物品当前生效的特殊机制集合（覆盖层优先，其次服务端同步的生效集合） */
    public static List<String> clientSpecialMechanicSets(String itemId) {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            SpecialMechanicOverride ov = snap.smOverrides.get(itemId);
            if (ov != null) {
                return ov.removed() ? List.of() : List.copyOf(ov.sets());
            }
            Set<String> sets = snap.effectiveSets.get(itemId);
            return sets == null ? List.of() : List.copyOf(sets);
        }
        SpecialMechanicOverride ov2 = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov2 != null) {
            return ov2.removed() ? List.of() : List.copyOf(ov2.sets());
        }
        Set<String> sets2 = CLIENT_EFFECTIVE_SETS.get(itemId);
        return sets2 == null ? List.of() : List.copyOf(sets2);
    }

    /** 客户端查询：所有可选的特殊机制集合名（覆盖层优先合并） */
    public static List<String> clientAllMechanicSets() {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            Set<String> sets = new LinkedHashSet<>();
            for (String itemId : snap.specialMechanicItems) {
                sets.addAll(clientSpecialMechanicSets(itemId));
            }
            for (SpecialMechanicOverride ov : snap.smOverrides.values()) {
                if (!ov.removed()) {
                    sets.addAll(ov.sets());
                }
            }
            return new ArrayList<>(sets);
        }
        Set<String> sets = new LinkedHashSet<>();
        for (String itemId : SPECIAL_MECHANIC_ITEMS) {
            sets.addAll(clientSpecialMechanicSets(itemId));
        }
        for (SpecialMechanicOverride ov : CLIENT_SPECIAL_MECHANIC_OVERRIDES.values()) {
            if (!ov.removed()) {
                sets.addAll(ov.sets());
            }
        }
        return new ArrayList<>(sets);
    }

    /** 客户端查询：机制集合显示名（tooltip_rules titleKey 翻译；无条目则回退：去掉 _items 后缀按集合名翻译，再回退原集合名） */
    public static String clientMechanicDisplayName(String mechanicSet) {
        if (mechanicSet == null || mechanicSet.isEmpty()) {
            return mechanicSet;
        }
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        List<TooltipRuleDef> rules = snap != null ? snap.tooltipRules : getTooltipRules();
        TooltipRuleDef matched = null;
        for (TooltipRuleDef rule : rules) {
            if (mechanicSet.equals(rule.itemSet()) && rule.titleKey() != null && !rule.titleKey().isEmpty()) {
                matched = rule;
                break;
            }
        }
        if (matched != null) {
            return translateMechanicTitle(matched.titleKey());
        }
        // 回退1：去掉 _items 后缀按集合名翻译（如 journey_module_items → tooltip.gytrinket.journey_module）
        String base = mechanicSet.endsWith("_items")
                ? mechanicSet.substring(0, mechanicSet.length() - "_items".length()) : mechanicSet;
        String key = "tooltip.gytrinket." + base;
        String translated = net.minecraft.network.chat.Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        // 回退2：集合名含 _required 时再尝试去掉（如 pursuit_array_required_items → tooltip.gytrinket.pursuit_array）
        if (base.endsWith("_required")) {
            String base2 = base.substring(0, base.length() - "_required".length());
            String key2 = "tooltip.gytrinket." + base2;
            String t2 = net.minecraft.network.chat.Component.translatable(key2).getString();
            if (!t2.equals(key2)) {
                return t2;
            }
        }
        return mechanicSet;
    }

    /** 翻译机制标题键（tooltip.gytrinket.<titleKey>，缺翻译时回退原键名） */
    private static String translateMechanicTitle(String titleKey) {
        String key = "tooltip.gytrinket." + titleKey;
        String translated = net.minecraft.network.chat.Component.translatable(key).getString();
        return translated.equals(key) ? titleKey : translated;
    }

    /** 客户端查询：物品声明为特殊机制时的机制名称列表 */
    public static List<String> clientSpecialMechanicNames(String itemId) {
        List<String> sets = clientSpecialMechanicSets(itemId);
        if (sets.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String set : sets) {
            names.add(clientMechanicDisplayName(set));
        }
        return names;
    }

    /**
     * 客户端：解析机制数值参数的物品级覆盖值（与服务端 resolveMechanicValue 语义一致）。
     * 遍历本地装备物品逐件合并——可叠加求和、不可叠加比对取最大；未覆盖物品按
     * fallback（Config 默认）不可叠加参与。player 为 null 或未装备时返回 fallback。
     * 客户端机制声明读快照（clientSpecialMechanicSets），数值读客户端覆盖层。
     */
    public static double clientResolveMechanicValue(Player player, String mechanicSet, String paramKey, double fallback) {
        if (player == null) return fallback;
        return mergeMechanicValue(player.getUUID(), PlayerStoreUtils.getAllEquippedStacks(player),
                itemId -> clientSpecialMechanicSets(itemId).contains(mechanicSet),
                CLIENT_SPECIAL_MECHANIC_OVERRIDES::get, mechanicSet, paramKey, fallback);
    }

    /** 客户端查询：护盾类型名 -> 是否兼容（shield_types 定义） */
    public static Map<String, Boolean> clientShieldTypes() {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return new HashMap<>(snap.shieldTypes);
        }
        return new HashMap<>(SHIELD_TYPES);
    }

    /** 客户端：从服务端同步的覆盖数据更新本地覆盖层（面板显示实时生效） */
    public static void setClientOverrides(Map<String, SpecialMechanicOverride> specialMechanics, Map<String, ShieldTypeOverride> shieldTypes) {
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.clear();
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.putAll(specialMechanics);
        CLIENT_SHIELD_TYPE_OVERRIDES.clear();
        CLIENT_SHIELD_TYPE_OVERRIDES.putAll(shieldTypes);
    }

    /** 客户端：服务端同步的物品->生效机制集合（绕过客户端无数据包的限制） */
    private static final Map<String, Set<String>> CLIENT_EFFECTIVE_SETS = new ConcurrentHashMap<>();

    /** 客户端：接收服务端完整定义同步（护盾类型/特殊机制/提示规则/覆盖层），替代客户端数据包读取 */
    public static void applyClientSync(Map<String, Boolean> shieldTypes,
                                       List<String> specialMechanicItems,
                                       Map<String, List<String>> itemToSets,
                                       List<TooltipRuleDef> tooltipRules,
                                       Map<String, SpecialMechanicOverride> smOverrides,
                                       Map<String, ShieldTypeOverride> stOverrides) {
        SHIELD_TYPES.clear();
        SHIELD_TYPES.putAll(shieldTypes);
        SPECIAL_MECHANIC_ITEMS.clear();
        SPECIAL_MECHANIC_ITEMS.addAll(specialMechanicItems);
        CLIENT_EFFECTIVE_SETS.clear();
        for (var e : itemToSets.entrySet()) {
            CLIENT_EFFECTIVE_SETS.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        TOOLTIP_RULES.clear();
        TOOLTIP_RULES.addAll(tooltipRules);
        setClientOverrides(smOverrides, stOverrides);

        // 构建并发布不可变快照：此后渲染线程只读快照（不可变），彻底避免并发修改
        Map<String, Set<String>> eff = new HashMap<>();
        for (var e : itemToSets.entrySet()) {
            eff.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        Map<String, List<String>> itemShieldTypes = new HashMap<>();
        for (var e : ITEM_SHIELD_TYPES.entrySet()) {
            itemShieldTypes.put(e.getKey(), List.copyOf(e.getValue()));
        }
        Map<String, Set<String>> itemSets = new HashMap<>();
        for (var e : ITEM_SETS.entrySet()) {
            itemSets.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        CLIENT_SNAPSHOT.set(new ClientSnapshot(
                Map.copyOf(shieldTypes),
                Set.copyOf(specialMechanicItems),
                Map.copyOf(eff),
                List.copyOf(tooltipRules),
                List.copyOf(ATTRIBUTE_DEFS),
                Map.copyOf(smOverrides),
                Map.copyOf(stOverrides),
                Map.copyOf(itemShieldTypes),
                Map.copyOf(itemSets)
        ));
    }

    // ===== 服务端查询（供同步到客户端） =====

    public static Map<String, Boolean> getServerShieldTypes() {
        return SHIELD_TYPES;
    }

    public static List<String> getServerSpecialMechanicItems() {
        return new ArrayList<>(SPECIAL_MECHANIC_ITEMS);
    }

    public static Map<String, List<String>> getServerAllEffectiveSets() {
        Map<String, List<String>> map = new HashMap<>();
        for (String itemId : SPECIAL_MECHANIC_ITEMS) {
            map.put(itemId, getEffectiveSpecialMechanicSets(itemId));
        }
        return map;
    }

    public static List<TooltipRuleDef> getServerTooltipRules() {
        return TOOLTIP_RULES;
    }

    /** 服务端：获取当前覆盖数据（供同步到客户端） */
    public static Map<String, SpecialMechanicOverride> getServerSpecialMechanicOverrides() {
        return SERVER_SPECIAL_MECHANIC_OVERRIDES;
    }

    /** 服务端：获取当前覆盖数据（供同步到客户端） */
    public static Map<String, ShieldTypeOverride> getServerShieldTypeOverrides() {
        return SERVER_SHIELD_TYPE_OVERRIDES;
    }
}
