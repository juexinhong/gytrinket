package com.gy_mod.gy_trinket.core.defs;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeType;
import com.gy_mod.gy_trinket.gytrinket;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // ===== 加载缓存（服务端与客户端共用） =====
    private static final Map<String, Set<String>> ITEM_SETS = new HashMap<>();
    private static final Map<String, Set<String>> ENTITY_SETS = new HashMap<>();
    private static final Map<String, Boolean> SHIELD_TYPES = new HashMap<>();
    private static final Map<String, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final List<AttributeEntry> ATTRIBUTE_DEFS = new ArrayList<>();
    private static final Map<String, Set<String>> DISABLE_TARGETS = new HashMap<>();
    private static final Map<String, Set<String>> DEPENDENCIES = new HashMap<>();
    private static final Map<String, Set<String>> DISABLE_CATEGORIES = new HashMap<>();
    private static final Map<String, List<List<String>>> DEPENDENCIES_ALL = new HashMap<>();
    private static final Map<String, ModuleTreeDef> MODULE_TREES = new LinkedHashMap<>();
    private static final Map<String, List<String>> UPGRADE_PATHS = new HashMap<>();
    private static final List<TooltipRuleDef> TOOLTIP_RULES = new ArrayList<>();

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
        SHIELD_TYPES.clear();
        ITEM_SHIELD_TYPES.clear();
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

    // ===== 客户端查询（读静态缓存，首次访问时惰性加载） =====

    /** 判断指定物品是否属于某个物品集合（客户端 tooltip 使用） */
    public static boolean itemSetContains(String setName, String itemId) {
        ensureClientLoaded();
        return ITEM_SETS.getOrDefault(setName, Set.of()).contains(itemId);
    }

    /** 获取物品的护盾类型列表（客户端 tooltip 使用） */
    public static List<String> clientItemShieldTypes(String itemId) {
        ensureClientLoaded();
        return ITEM_SHIELD_TYPES.getOrDefault(itemId, List.of());
    }

    /** 查询属性的组合方式（客户端 tooltip 格式化使用），未找到返回 null */
    public static AttributeType clientAttributeType(String attrName) {
        ensureClientLoaded();
        for (AttributeEntry e : ATTRIBUTE_DEFS) {
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
        return TOOLTIP_RULES;
    }
}
