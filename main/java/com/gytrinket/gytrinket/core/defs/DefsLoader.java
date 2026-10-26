package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemSetDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemShieldTypeDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.AttributeEntry;
import com.gytrinket.gytrinket.core.defs.DefsTypes.AttributeDefs;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDependencyDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ModuleTreeDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.UpgradePathDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.TooltipRuleDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinitionsFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 定义类数据加载器（P5-5c 从 DefsManager 拆出）。
 * <p>
 * 读取 datapack JSON → 逐 kind 解析 → 全部成功才发布新快照（失败保留旧缓存）。
 * 覆盖层合并顺序在这里固化：数据文件 → 文件持久化覆盖 → items 统一覆写派生。
 * 加载完成后调用 {@link Config#applyDefs()} 触发 Config 与子系统的派生重建。
 */
final class DefsLoader {

    private DefsLoader() {}

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
    static void loadFrom(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return;
        }
        int loadErrorCountBefore = DefsManager.loadErrorCount;
        DefsManager.loadErrorCount = 0;
        Map<String, Set<String>> itemSets = new ConcurrentHashMap<>();
        Map<String, Set<String>> entitySets = new ConcurrentHashMap<>();
        Set<String> specialMechanicItems = ConcurrentHashMap.newKeySet();
        Map<String, List<String>> specialMechanicSets = new ConcurrentHashMap<>();
        Map<String, Boolean> shieldTypes = new ConcurrentHashMap<>();
        Map<String, Map<String, ParamValue>> shieldTypeParamDefaults = new ConcurrentHashMap<>();
        Map<String, List<String>> itemShieldTypes = new ConcurrentHashMap<>();
        List<AttributeEntry> attributeDefs = new ArrayList<>();
        Map<String, Set<String>> disableTargets = new HashMap<>();
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, Set<String>> disableCategories = new HashMap<>();
        Map<String, List<List<String>>> dependenciesAll = new HashMap<>();
        Map<String, ModuleTreeDef> moduleTrees = new LinkedHashMap<>();
        Map<String, List<String>> upgradePaths = new HashMap<>();
        List<TooltipRuleDef> tooltipRules = new CopyOnWriteArrayList<>();
        Map<String, ItemDefinition> itemDefinitions = new LinkedHashMap<>();
        Set<String> itemDataPackIds = new HashSet<>();
        // 每次定义加载（世界启动/重载/手动应用）先读入运行时覆盖文件，保证持久化与优先级
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            DefsOverrideIO.loadOverridesFromFile(server);
        }
        // 资源管理器可能处于数据包尚未就绪的瞬时状态（例如客户端刚进入世界时）。
        // 此时找不到定义类数据；若已有缓存数据则保留，避免空结果清空后导致
        // 工具提示/禁用判定等机制全部失效。
        boolean hasItemSets = !resourceManager.listResources("gytrinket/item_sets", p -> p.getPath().endsWith(".json")).isEmpty();
        if (!hasItemSets && (!DefsManager.ITEM_SETS.isEmpty() || !DefsManager.TOOLTIP_RULES.isEmpty() || !DefsManager.SHIELD_TYPES.isEmpty())) {
            // 资源管理器还没就绪：不要用空局部状态覆盖上一份缓存
            DefsManager.loadErrorCount = loadErrorCountBefore;
            return;
        }
        itemSets.clear();
        entitySets.clear();
        specialMechanicItems.clear();
        specialMechanicSets.clear();
        shieldTypes.clear();
        shieldTypeParamDefaults.clear();
        itemShieldTypes.clear();
        attributeDefs.clear();
        disableTargets.clear();
        dependencies.clear();
        disableCategories.clear();
        dependenciesAll.clear();
        moduleTrees.clear();
        upgradePaths.clear();
        tooltipRules.clear();
        itemDefinitions.clear();

        // 物品集合
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_sets", p -> p.getPath().endsWith(".json")).entrySet()) {
            String id = fileId(e.getKey());
            ItemSetDef def = parseResource(e.getValue(), ItemSetDef.CODEC);
            if (def == null) continue;
            if (!def.items().isEmpty()) {
                itemSets.put(id, new HashSet<>(def.items()));
            }
            if (!def.entities().isEmpty()) {
                entitySets.put(id, new HashSet<>(def.entities()));
            }
        }

        // 护盾类型
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/shield_types", p -> p.getPath().endsWith(".json")).entrySet()) {
            ShieldTypeDef def = parseResource(e.getValue(), ShieldTypeDef.CODEC);
            if (def != null) {
                shieldTypes.put(fileId(e.getKey()), def.compatible());
                if (!def.shieldValues().isEmpty()) {
                    shieldTypeParamDefaults.put(fileId(e.getKey()), def.shieldValues());
                }
            }
        }

        // 物品->护盾类型（统一 items 覆写显式接管护盾类型时跳过数据文件）
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_shield_types", p -> p.getPath().endsWith(".json")).entrySet()) {
            ItemShieldTypeDef def = parseResource(e.getValue(), ItemShieldTypeDef.CODEC);
            if (def != null && !itemOverrideOwnsShieldTypes(def.item())) {
                itemShieldTypes.put(def.item(), new ArrayList<>(def.types()));
            }
        }

        // 运行时覆盖：护盾类型以覆盖为准。
        // 统一 items 覆写显式接管护盾类型时（removed / shieldTypes 非 null）跳过旧段覆盖
        for (var e : DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            if (itemOverrideOwnsShieldTypes(e.getKey())) {
                continue;
            }
            itemShieldTypes.put(e.getKey(), new ArrayList<>(e.getValue().types()));
        }

        // 特殊机制（路径定义 + 分类声明）：文件名 = 物品 id（完整注册名，带命名空间；快速装备/判定用）
        // 运行时覆盖（DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES）优先：removed 撤销声明，sets 覆盖分类
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/special_mechanics", p -> p.getPath().endsWith(".json")).entrySet()) {
            SpecialMechanicDef def = parseResource(e.getValue(), SpecialMechanicDef.CODEC);
            if (def == null) continue;
            // 必须带命名空间前缀（如 gytrinket:journey_module），否则与物品注册名/覆盖 key 不匹配
            String itemId = e.getKey().getNamespace() + ":" + fileId(e.getKey());
            // 统一 items 覆写显式接管机制声明时（removed / mechanic 非 null）跳过数据文件声明
            if (itemOverrideOwnsMechanic(itemId)) {
                continue;
            }
            SpecialMechanicOverride ov = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
            if (ov != null) {
                if (ov.removed()) {
                    continue;
                }
                specialMechanicItems.add(itemId);
                specialMechanicSets.put(itemId, new ArrayList<>(ov.sets()));
                for (String setName : ov.sets()) {
                    if (setName == null || setName.isEmpty()) continue;
                    itemSets.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                }
                continue;
            }
            if (def.removed()) {
                continue;
            }
            specialMechanicItems.add(itemId);
            specialMechanicSets.put(itemId, new ArrayList<>(def.sets()));
            for (String setName : def.sets()) {
                if (setName == null || setName.isEmpty()) continue;
                itemSets.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
            }
        }

        // 运行时覆盖：新增 JAR/资源中不存在的条目（机制声明已被 items 覆写显式接管的除外）
        for (var e : DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
            if (specialMechanicItems.contains(e.getKey()) || e.getValue().removed() || itemOverrideOwnsMechanic(e.getKey())) {
                continue;
            }
            specialMechanicItems.add(e.getKey());
            specialMechanicSets.put(e.getKey(), new ArrayList<>(e.getValue().sets()));
            for (String setName : e.getValue().sets()) {
                if (setName == null || setName.isEmpty()) continue;
                itemSets.computeIfAbsent(setName, k -> new HashSet<>()).add(e.getKey());
            }
        }

        // 属性定义（多文件合并）
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/attribute_definitions", p -> p.getPath().endsWith(".json")).entrySet()) {
            AttributeDefs defs = parseResource(e.getValue(), AttributeDefs.CODEC);
            if (defs != null) {
                attributeDefs.addAll(defs.attributes());
            }
        }

        // 禁用/依赖
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/item_dependencies", p -> p.getPath().endsWith(".json")).entrySet()) {
            ItemDependencyDef def = parseResource(e.getValue(), ItemDependencyDef.CODEC);
            if (def == null) continue;
            if (!def.disables().isEmpty()) {
                disableTargets.put(def.item(), new HashSet<>(def.disables()));
            }
            if (!def.dependsOn().isEmpty()) {
                dependencies.put(def.item(), new HashSet<>(def.dependsOn()));
            }
            if (!def.disablesCategories().isEmpty()) {
                disableCategories.put(def.item(), new HashSet<>(def.disablesCategories()));
            }
            if (!def.dependsOnAll().isEmpty()) {
                dependenciesAll.put(def.item(), def.dependsOnAll());
            }
        }

        // 模块树
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/module_trees", p -> p.getPath().endsWith(".json")).entrySet()) {
            ModuleTreeDef def = parseResource(e.getValue(), ModuleTreeDef.CODEC);
            if (def != null) {
                moduleTrees.put(fileId(e.getKey()), def);
            }
        }

        // 升级路径
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/upgrade_paths", p -> p.getPath().endsWith(".json")).entrySet()) {
            UpgradePathDef def = parseResource(e.getValue(), UpgradePathDef.CODEC);
            if (def != null) {
                upgradePaths.put(def.base(), new ArrayList<>(def.upgrades()));
            }
        }

        // 工具提示规则
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager.listResources("gytrinket/tooltip_rules", p -> p.getPath().endsWith(".json")).entrySet()) {
            TooltipRuleDef def = parseResource(e.getValue(), TooltipRuleDef.CODEC);
            if (def != null) {
                tooltipRules.add(def);
            }
        }

        // 统一物品定义合并：item_definitions 数据文件（按 key 排序防多文件同物品顺序不定）+ DefsManager.SERVER_ITEM_OVERRIDES
        itemDataPackIds.clear();
        List<Map.Entry<ResourceLocation, Resource>> itemDefEntries = new ArrayList<>(
                resourceManager.listResources("gytrinket/item_definitions", p -> p.getPath().endsWith(".json")).entrySet());
        itemDefEntries.sort(Map.Entry.comparingByKey());
        for (var e : itemDefEntries) {
            ItemDefinitionsFile file = parseResource(e.getValue(), ItemDefinitionsFile.CODEC);
            if (file == null) continue;
            for (var item : file.items().entrySet()) {
                String id = normalizeItemId(item.getKey());
                itemDataPackIds.add(id);
                itemDefinitions.merge(id, item.getValue(), ItemDefinition::merge);
            }
        }
        for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
            itemDefinitions.merge(e.getKey(), e.getValue(), ItemDefinition::merge);
        }

        // items 统一定义派生旧缓存：遍历合并后全量条目（JAR 数据 + 覆写），
        // 显式接管的字段以合并后定义为准（合并链保留下层 sets/shieldTypes）
        for (var e : itemDefinitions.entrySet()) {
            String itemId = e.getKey();
            ItemDefinition def = e.getValue();
            if (def != null && def.removed()) {
                // removed：整体撤销机制声明与护盾类型
                specialMechanicItems.remove(itemId);
                specialMechanicSets.remove(itemId);
                itemShieldTypes.remove(itemId);
                continue;
            }
            if (def != null && def.mechanic() != null) {
                if (def.mechanic()) {
                    specialMechanicItems.add(itemId);
                    specialMechanicSets.put(itemId, def.sets() != null ? new ArrayList<>(def.sets()) : new ArrayList<>());
                    if (def.sets() != null) {
                        for (String setName : def.sets()) {
                            if (setName == null || setName.isEmpty()) continue;
                            itemSets.computeIfAbsent(setName, k -> new HashSet<>()).add(itemId);
                        }
                    }
                } else {
                    specialMechanicItems.remove(itemId);
                    specialMechanicSets.remove(itemId);
                }
            }
            if (def != null && def.shieldTypes() != null) {
                if (def.shieldTypes().isEmpty()) {
                    itemShieldTypes.remove(itemId);
                } else {
                    itemShieldTypes.put(itemId, new ArrayList<>(def.shieldTypes()));
                }
            }
        }

        // 仅当加载到实际数据时才视为完成；否则保持可重试（客户端资源管理器可能尚未就绪）
        if (DefsManager.loadErrorCount > 0) {
            gytrinket.LOGGER.error("定义类数据加载失败：{} 个文件解析错误，保留上一份缓存（本次不发布）", DefsManager.loadErrorCount);
            DefsManager.loadErrorCount = loadErrorCountBefore;
            return;
        }

        // 解析全部成功后才一次性替换静态缓存：失败时旧缓存保持不变
        DefsManager.ITEM_SETS = itemSets;
        DefsManager.ENTITY_SETS = entitySets;
        DefsManager.SPECIAL_MECHANIC_ITEMS = specialMechanicItems;
        DefsManager.SPECIAL_MECHANIC_SETS = specialMechanicSets;
        DefsManager.SHIELD_TYPES = shieldTypes;
        DefsManager.SHIELD_TYPE_PARAM_DEFAULTS = shieldTypeParamDefaults;
        DefsManager.ITEM_SHIELD_TYPES = itemShieldTypes;
        DefsManager.ATTRIBUTE_DEFS = attributeDefs;
        DefsManager.DISABLE_TARGETS = disableTargets;
        DefsManager.DEPENDENCIES = dependencies;
        DefsManager.DISABLE_CATEGORIES = disableCategories;
        DefsManager.DEPENDENCIES_ALL = dependenciesAll;
        DefsManager.MODULE_TREES = moduleTrees;
        DefsManager.UPGRADE_PATHS = upgradePaths;
        DefsManager.TOOLTIP_RULES = tooltipRules;
        DefsManager.ITEM_DEFINITIONS = itemDefinitions;
        DefsManager.ITEM_DATA_PACK_IDS = itemDataPackIds;
        DefsManager.loadErrorCount = 0;
        DefsManager.loadSequence++;
        DefsManager.currentSnapshot = DefsSnapshot.capture(DefsManager.loadSequence, itemSets, entitySets, specialMechanicItems,
                specialMechanicSets, shieldTypes, shieldTypeParamDefaults, itemShieldTypes, attributeDefs,
                disableTargets, dependencies, disableCategories, dependenciesAll, moduleTrees, upgradePaths,
                tooltipRules, itemDefinitions, itemDataPackIds);
        DefsManager.clientLoaded = !DefsManager.ITEM_SETS.isEmpty() || !DefsManager.SHIELD_TYPES.isEmpty() || !DefsManager.TOOLTIP_RULES.isEmpty() || !DefsManager.ATTRIBUTE_DEFS.isEmpty();

        // 填充 Config 集合并触发依赖定义数据的子系统重载
        Config.applyDefs();
    }

    /** items 覆写条目是否显式接管该物品的机制声明（removed 撤销，或 mechanic 非 null） */
    static boolean itemOverrideOwnsMechanic(String itemId) {
        ItemDefinition def = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        return def != null && (def.removed() || def.mechanic() != null);
    }

    /** items 覆写条目是否显式接管该物品的护盾类型（removed 撤销，或 shieldTypes 非 null） */
    static boolean itemOverrideOwnsShieldTypes(String itemId) {
        ItemDefinition def = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        return def != null && (def.removed() || def.shieldTypes() != null);
    }

    /** 物品 id 规范化：无命名空间时补 gytrinket: */
    static String normalizeItemId(String id) {
        return id != null && !id.isEmpty() && !id.contains(":") ? gytrinket.MODID + ":" + id : id;
    }

    /** 从资源 key 提取文件名（去掉目录与 .json 后缀） */
    static String fileId(ResourceLocation key) {
        String path = key.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    /** 用 Codec 解析单个 JSON 资源，失败返回 null */
    static <T> T parseResource(Resource resource, Codec<T> codec) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            var result = codec.parse(JsonOps.INSTANCE, json);
            result.error().ifPresent(err -> {
                DefsManager.loadErrorCount++;
                gytrinket.LOGGER.error("定义类数据解析失败 [{}]: {}", resource, err);
            });
            return result.result().orElse(null);
        } catch (Exception e) {
            DefsManager.loadErrorCount++;
            gytrinket.LOGGER.error("读取定义类数据失败 [{}]", resource, e);
            return null;
        }
    }

    /** 客户端惰性加载：首次查询时从客户端资源管理器读取 */
    static void ensureClientLoaded() {
        if (DefsManager.clientLoaded) {
            return;
        }
        DefsManager.clientLoaded = true;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                loadFrom(mc.getResourceManager());
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("客户端定义类数据加载失败", e);
        }
    }
}
