package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 *   data/&lt;命名空间&gt;/gytrinket/upgrade_paths/&lt;基础物品名&gt;.json       -- 升级路径
 * </pre>
 *
 * 服务端：注册 6 个 datapack registry（含网络编解码，登录时自动同步到客户端），
 * 在 {@link AddReloadListenerEvent} 时读取并填充 {@link Config} 的集合（/reload 生效）。
 * 客户端：Tooltip 等显示逻辑通过 {@link #itemSetContains(RegistryAccess, String, String)} 等
 * 方法从同步后的 registryAccess 实时查询。
 */
@EventBusSubscriber(modid = gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DefsManager {

    private static final String REGISTRY_NAMESPACE = gytrinket.MODID;

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

    /** 禁用/依赖条目：{ "item": "...", "disables": [...], "dependsOn": [...] } */
    public record ItemDependencyDef(String item, List<String> disables, List<String> dependsOn) {
        static final Codec<ItemDependencyDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("item").forGetter(ItemDependencyDef::item),
                Codec.STRING.listOf().optionalFieldOf("disables", List.of()).forGetter(ItemDependencyDef::disables),
                Codec.STRING.listOf().optionalFieldOf("dependsOn", List.of()).forGetter(ItemDependencyDef::dependsOn)
        ).apply(inst, ItemDependencyDef::new));
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
    private static final Map<String, Boolean> SHIELD_TYPES = new HashMap<>();
    private static final Map<String, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final List<AttributeEntry> ATTRIBUTE_DEFS = new ArrayList<>();
    private static final Map<String, Set<String>> DISABLE_TARGETS = new HashMap<>();
    private static final Map<String, Set<String>> DEPENDENCIES = new HashMap<>();
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
        event.dataPackRegistry(UPGRADE_PATHS_KEY, UpgradePathDef.CODEC, UpgradePathDef.CODEC);
        event.dataPackRegistry(TOOLTIP_RULES_KEY, TooltipRuleDef.CODEC, TooltipRuleDef.CODEC);
        gytrinket.LOGGER.info("已注册 7 个定义类 datapack registry");
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
        ITEM_SETS.clear();
        ENTITY_SETS.clear();
        SHIELD_TYPES.clear();
        ITEM_SHIELD_TYPES.clear();
        ATTRIBUTE_DEFS.clear();
        DISABLE_TARGETS.clear();
        DEPENDENCIES.clear();
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

        gytrinket.LOGGER.info("定义类数据加载完成：物品集合 {} 项，护盾类型 {} 项，物品护盾类型 {} 项，属性定义 {} 项，禁用目标 {} 项，依赖 {} 项，升级路径 {} 项，工具提示规则 {} 项",
                ITEM_SETS.size(), SHIELD_TYPES.size(), ITEM_SHIELD_TYPES.size(),
                ATTRIBUTE_DEFS.size(), DISABLE_TARGETS.size(), DEPENDENCIES.size(), UPGRADE_PATHS.size(), TOOLTIP_RULES.size());

        // 填充 Config 集合并触发依赖定义数据的子系统重载
        Config.applyDefs();
    }

    // ===== 服务端查询（供 Config.applyDefs 与各子系统使用） =====

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

    public static Map<String, List<String>> getUpgradePaths() {
        return UPGRADE_PATHS;
    }

    // ===== 客户端查询（从同步后的 registryAccess 实时读取） =====

    /** 判断指定物品是否属于某个物品集合（客户端 tooltip 使用） */
    public static boolean itemSetContains(RegistryAccess access, String setName, String itemId) {
        if (access == null) return false;
        Optional<Registry<ItemSetDef>> reg = access.registry(ITEM_SETS_KEY);
        if (reg.isEmpty()) return false;
        for (Map.Entry<ResourceKey<ItemSetDef>, ItemSetDef> e : reg.get().entrySet()) {
            if (e.getKey().location().getPath().equals(setName) && e.getValue().items().contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    /** 获取物品的护盾类型列表（客户端 tooltip 使用） */
    public static List<String> clientItemShieldTypes(RegistryAccess access, String itemId) {
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
