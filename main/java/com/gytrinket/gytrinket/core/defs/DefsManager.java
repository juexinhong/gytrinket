package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import com.mojang.serialization.Codec;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    /** 覆盖文件（<世界>/gytrinket_ui_overrides.json，位于数据包目录之外，不触发数据包校验/安全模式） */
    private static final String OVERRIDES_FILE_NAME = "gytrinket_ui_overrides.json";

    /** 服务端：特殊机制覆盖（itemId -> 声明），removed=true 表示撤销声明 */
    public record SpecialMechanicOverride(List<String> sets, boolean removed) {
        public static SpecialMechanicOverride removedState() { return new SpecialMechanicOverride(List.of(), true); }
        public static SpecialMechanicOverride declared(List<String> sets) { return new SpecialMechanicOverride(sets, false); }
    }

    private static final Map<String, SpecialMechanicOverride> SERVER_SPECIAL_MECHANIC_OVERRIDES = new HashMap<>();
    private static final Map<String, List<String>> SERVER_SHIELD_TYPE_OVERRIDES = new HashMap<>();
    /** 客户端：从服务端同步的覆盖数据（面板显示用） */
    private static final Map<String, SpecialMechanicOverride> CLIENT_SPECIAL_MECHANIC_OVERRIDES = new HashMap<>();
    private static final Map<String, List<String>> CLIENT_SHIELD_TYPE_OVERRIDES = new HashMap<>();

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

    // ===== 服务端加载缓存 =====
    private static final Map<String, Set<String>> ITEM_SETS = new HashMap<>();
    private static final Map<String, Set<String>> ENTITY_SETS = new HashMap<>();
    /** 声明为"特殊机制"的物品集合（specialMechanic=true 的物品集合条目并集），供快速装备等统一判定 */
    private static final Set<String> SPECIAL_MECHANIC_ITEMS = new HashSet<>();
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
        gytrinket.LOGGER.info("已注册 9 个定义类 datapack registry");
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
        ITEM_SHIELD_TYPES.clear();
        ATTRIBUTE_DEFS.clear();
        DISABLE_TARGETS.clear();
        DEPENDENCIES.clear();
        DISABLE_CATEGORIES.clear();
        DEPENDENCIES_ALL.clear();
        MODULE_TREES.clear();
        UPGRADE_PATHS.clear();

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
        access.registry(SPECIAL_MECHANICS_KEY).ifPresent(reg -> {
            Set<String> seen = new HashSet<>();
            for (Map.Entry<ResourceKey<SpecialMechanicDef>, SpecialMechanicDef> e : reg.entrySet()) {
                String itemId = e.getKey().location().toString();
                seen.add(itemId);
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
            // 覆盖：新增 JAR 中不存在的条目
            for (var e : SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
                if (seen.contains(e.getKey()) || e.getValue().removed()) {
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
            }
        });

        access.registry(ITEM_SHIELD_TYPES_KEY).ifPresent(reg -> {
            for (ItemShieldTypeDef def : reg) {
                ITEM_SHIELD_TYPES.put(def.item(), new ArrayList<>(def.types()));
            }
        });

        // 运行时覆盖：护盾类型以覆盖为准（空列表 = 移除全部类型）
        for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            ITEM_SHIELD_TYPES.put(e.getKey(), new ArrayList<>(e.getValue()));
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

        gytrinket.LOGGER.info("定义类数据加载完成：物品集合 {} 项，护盾类型 {} 项，物品护盾类型 {} 项，属性定义 {} 项，禁用目标 {} 项，依赖 {} 项，类别禁用 {} 项，AND依赖 {} 项，模块树 {} 棵，升级路径 {} 项，工具提示规则 {} 项",
                ITEM_SETS.size(), SHIELD_TYPES.size(), ITEM_SHIELD_TYPES.size(),
                ATTRIBUTE_DEFS.size(), DISABLE_TARGETS.size(), DEPENDENCIES.size(),
                DISABLE_CATEGORIES.size(), DEPENDENCIES_ALL.size(), MODULE_TREES.size(), UPGRADE_PATHS.size(), TOOLTIP_RULES.size());

        // 填充 Config 集合并触发依赖定义数据的子系统重载
        Config.applyDefs();
    }

    // ===== 服务端查询（供 Config.applyDefs 与各子系统使用） =====

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
                    SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId,
                            removed ? SpecialMechanicOverride.removedState() : SpecialMechanicOverride.declared(sets));
                }
            }
            if (root.has("shieldTypes")) {
                for (var e : root.getAsJsonObject("shieldTypes").entrySet()) {
                    List<String> types = new ArrayList<>();
                    e.getValue().getAsJsonArray().forEach(el -> types.add(el.getAsString()));
                    SERVER_SHIELD_TYPE_OVERRIDES.put(e.getKey(), types);
                }
            }
            gytrinket.LOGGER.info("已读取定义覆盖文件：特殊机制 {} 项，护盾类型 {} 项",
                    SERVER_SPECIAL_MECHANIC_OVERRIDES.size(), SERVER_SHIELD_TYPE_OVERRIDES.size());
        } catch (Exception e) {
            gytrinket.LOGGER.error("读取定义覆盖文件失败: {}", file, e);
        }
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
                sm.add(e.getKey(), def);
            }
            root.add("specialMechanics", sm);
            com.google.gson.JsonObject st = new com.google.gson.JsonObject();
            for (var e : SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
                com.google.gson.JsonArray types = new com.google.gson.JsonArray();
                e.getValue().forEach(types::add);
                st.add(e.getKey(), types);
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

    /** 服务端：物品当前生效的特殊机制集合（覆盖优先，其次数据驱动声明） */
    public static List<String> getEffectiveSpecialMechanicSets(MinecraftServer server, String itemId) {
        SpecialMechanicOverride ov = SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return new ArrayList<>(ov.removed() ? List.of() : ov.sets());
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
     * 编辑操作：为物品添加/移除指定特殊机制（set）。
     * 添加 = 当前集合 ∪ {set}；移除 = 当前集合 − {set}，移除后为空则整体撤销声明。
     * 写入内存 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateSpecialMechanicSet(MinecraftServer server, String itemId, String mechanicSet, boolean remove) {
        List<String> current = getEffectiveSpecialMechanicSets(server, itemId);
        List<String> updated = new ArrayList<>();
        if (remove) {
            for (String s : current) {
                if (!s.equals(mechanicSet)) {
                    updated.add(s);
                }
            }
            if (updated.isEmpty()) {
                SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.removedState());
            } else {
                SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(updated));
            }
        } else {
            updated.addAll(current);
            if (!updated.contains(mechanicSet)) {
                updated.add(mechanicSet);
            }
            SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId, SpecialMechanicOverride.declared(updated));
        }
        saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 编辑操作：更新物品护盾类型（写入内存 + 覆盖文件，立即重新加载生效，不重载数据包） */
    public static void updateShieldTypeOverride(MinecraftServer server, String itemId, List<String> types) {
        SERVER_SHIELD_TYPE_OVERRIDES.put(itemId, new ArrayList<>(types));
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

    /** 客户端查询：物品是否声明为特殊机制（special_mechanics 路径定义 + 客户端覆盖层） */
    public static boolean clientIsSpecialMechanic(RegistryAccess access, String itemId) {
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return !ov.removed();
        }
        if (access == null) return false;
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
        // 1. 取该物品声明的 sets（客户端覆盖层优先）
        List<String> sets = new ArrayList<>();
        SpecialMechanicOverride ov = CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            if (ov.removed()) {
                return List.of();
            }
            sets.addAll(ov.sets());
        } else if (access != null) {
            Optional<Registry<SpecialMechanicDef>> smReg = access.registry(SPECIAL_MECHANICS_KEY);
            if (smReg.isPresent()) {
                ResourceLocation loc = ResourceLocation.tryParse(itemId);
                if (loc != null) {
                    SpecialMechanicDef def = smReg.get().get(loc);
                    if (def != null && !def.removed()) {
                        sets.addAll(def.sets());
                    }
                }
            }
        }
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
        if (CLIENT_SHIELD_TYPE_OVERRIDES.containsKey(itemId)) {
            return List.copyOf(CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId));
        }
        if (access == null) return List.of();
        Optional<Registry<ItemShieldTypeDef>> reg = access.registry(ITEM_SHIELD_TYPES_KEY);
        if (reg.isEmpty()) return List.of();
        for (ItemShieldTypeDef def : reg.get()) {
            if (def.item().equals(itemId)) {
                return List.copyOf(def.types());
            }
        }
        return List.of();
    }

    /** 客户端：从服务端同步的覆盖数据更新本地覆盖层（面板显示实时生效） */
    public static void setClientOverrides(Map<String, SpecialMechanicOverride> specialMechanics, Map<String, List<String>> shieldTypes) {
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.clear();
        CLIENT_SPECIAL_MECHANIC_OVERRIDES.putAll(specialMechanics);
        CLIENT_SHIELD_TYPE_OVERRIDES.clear();
        CLIENT_SHIELD_TYPE_OVERRIDES.putAll(shieldTypes);
    }

    /** 服务端：获取当前覆盖数据（供同步到客户端） */
    public static Map<String, SpecialMechanicOverride> getServerSpecialMechanicOverrides() {
        return SERVER_SPECIAL_MECHANIC_OVERRIDES;
    }

    /** 服务端：获取当前覆盖数据（供同步到客户端） */
    public static Map<String, List<String>> getServerShieldTypeOverrides() {
        return SERVER_SHIELD_TYPE_OVERRIDES;
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
