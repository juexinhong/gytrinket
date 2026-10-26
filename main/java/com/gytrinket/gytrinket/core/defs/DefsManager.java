package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.gytrinket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.gytrinket.gytrinket.core.defs.DefsTypes.AttributeEntry;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ModuleTreeDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.TooltipRuleDef;

/**
 * 定义类数据管理器（datapack 数据驱动）—— <b>facade + 唯一状态持有者</b>（P5-5c）。
 *
 * <p>数据包布局（原写在 TOML 的"定义类"配置已全部迁移到 datapack）：
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
 * <p>职责拆分（其余类都在本包内，外部只认这个 facade）：
 * <ul>
 *   <li>{@link DefsTypes}：JSON 数据模型（record + Codec）；</li>
 *   <li>{@link DefsLoader}：ResourceManager 扫描 → 解析 → 全成功才原子发布 {@link DefsSnapshot}；</li>
 *   <li>{@link DefsOverrideIO}：运行时覆盖层（config/gytrinket/*.json）读写；</li>
 *   <li>{@link DefsItemOverrides}：UI 编辑写回（统一 items 覆写 + 立即重载）；</li>
 *   <li>{@link DefsQueries}：服务端机制/护盾/属性参数解析与同步视图；</li>
 *   <li>{@link DefsClientView}：客户端不可变快照与只读查询；</li>
 *   <li>{@link DefsRuntimeIndex}：物品级派生索引（模块集合/护盾类型/特殊机制），供 Config 热路径判定。</li>
 * </ul>
 *
 * <p>Forge 1.20.1 没有 NeoForge 的 DataPackRegistry / registry 网络同步机制：服务端在
 * {@link net.minecraftforge.event.AddReloadListenerEvent} 上注册监听（见 {@link DefsLoader}），
 * 客户端在首次查询时惰性加载本地数据包、联机时等服务端同步。
 */
public class DefsManager {

    // ===== 运行时覆盖层（绕过数据包验证：编辑写入独立 JSON，玩家手动「应用」生效） =====

    /** 覆盖文件（config/gytrinket/gytrinket_ui_overrides.json，位于数据包目录之外，不触发数据包校验/安全模式） */
    static final String OVERRIDES_FILE_NAME = "gytrinket_ui_overrides.json";

    /** 分层覆盖文件匹配：override_layer_数字.json（数字越大覆盖优先级越高） */
    static final java.util.regex.Pattern OVERRIDE_LAYER_FILE_PATTERN =
            java.util.regex.Pattern.compile("override_layer_(\\d+)\\.json", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** UI 覆盖文件拥有的条目（specialMechanics 的 itemId）：保存时仅写这些条目，避免分层文件条目被复制进 UI 文件后遮蔽外部分层文件的后续修改 */
    static final Set<String> SPECIAL_MECHANIC_UI_OWNED = new HashSet<>();

    /** UI 覆盖文件拥有的条目（shieldTypes 的 itemId），作用同上 */
    static final Set<String> SHIELD_TYPE_UI_OWNED = new HashSet<>();

    /** UI 覆盖文件拥有的条目（items 段的物品 id）：统一物品级覆盖的保存范围（旧段条目加载时升级为本集合） */
    static final Set<String> ITEM_UI_OWNED = new HashSet<>();

    /** 数据包 item_definitions 声明的物品 id（纯数据文件来源，不含 UI 覆写；删除功能判定用） */
    static volatile Set<String> ITEM_DATA_PACK_IDS = new HashSet<>();

    static final Map<String, SpecialMechanicOverride> SERVER_SPECIAL_MECHANIC_OVERRIDES = new ConcurrentHashMap<>();
    static final Map<String, ShieldTypeOverride> SERVER_SHIELD_TYPE_OVERRIDES = new ConcurrentHashMap<>();
    /** 统一物品级覆盖（items 段）：物品 id -> ItemDefinition，对覆盖的物品完全接管（旧段/数据文件不再为该物品生效） */
    static final Map<String, ItemDefinition> SERVER_ITEM_OVERRIDES = new HashMap<>();
    /** 客户端：从服务端同步的覆盖数据（面板显示用） */
    static final Map<String, SpecialMechanicOverride> CLIENT_SPECIAL_MECHANIC_OVERRIDES = new ConcurrentHashMap<>();
    static final Map<String, ShieldTypeOverride> CLIENT_SHIELD_TYPE_OVERRIDES = new ConcurrentHashMap<>();

    /** 声明为"特殊机制"的物品集合（special_mechanics 文件夹声明并集），供快速装备等统一判定 */
    static volatile Set<String> SPECIAL_MECHANIC_ITEMS = ConcurrentHashMap.newKeySet();

    /** 物品声明的特殊机制集合（itemId -> 机制集合名列表；special_mechanics 定义，覆盖优先）。
     *  与 1.21.1 的 SPECIAL_MECHANICS registry 语义对齐：只含机制声明，不含 item_sets 普通集合 */
    static volatile Map<String, List<String>> SPECIAL_MECHANIC_SETS = new ConcurrentHashMap<>();

    // ===== 加载缓存（服务端与客户端共用；由 DefsLoader 全量替换） =====
    static volatile Map<String, Set<String>> ITEM_SETS = new ConcurrentHashMap<>();
    static volatile Map<String, Set<String>> ENTITY_SETS = new ConcurrentHashMap<>();
    static volatile Map<String, Boolean> SHIELD_TYPES = new ConcurrentHashMap<>();
    /** 护盾类型基础参数默认值缓存（shield_types/*.json 的 shieldValues 段）：四项基础属性的类型级回退层 */
    static volatile Map<String, Map<String, ParamValue>> SHIELD_TYPE_PARAM_DEFAULTS = new ConcurrentHashMap<>();
    static volatile Map<String, List<String>> ITEM_SHIELD_TYPES = new ConcurrentHashMap<>();
    static volatile List<AttributeEntry> ATTRIBUTE_DEFS = new ArrayList<>();
    static volatile Map<String, Set<String>> DISABLE_TARGETS = new HashMap<>();
    static volatile Map<String, Set<String>> DEPENDENCIES = new HashMap<>();
    static volatile Map<String, Set<String>> DISABLE_CATEGORIES = new HashMap<>();
    static volatile Map<String, List<List<String>>> DEPENDENCIES_ALL = new HashMap<>();
    static volatile Map<String, ModuleTreeDef> MODULE_TREES = new LinkedHashMap<>();
    static volatile Map<String, List<String>> UPGRADE_PATHS = new HashMap<>();
    static volatile List<TooltipRuleDef> TOOLTIP_RULES = new CopyOnWriteArrayList<>();
    /** 统一物品定义（item_definitions 数据文件按 key 排序合并后的结果），供有效查询入口取基线 */
    static volatile Map<String, ItemDefinition> ITEM_DEFINITIONS = new LinkedHashMap<>();

    /** 客户端惰性加载标记 */
    static boolean clientLoaded = false;

    /** 本次定义加载的解析错误计数（0 = 全量成功，可发布） */
    static int loadErrorCount = 0;

    /** 成功发布的最新定义快照（解析失败不发布）。 */
    static volatile DefsSnapshot currentSnapshot;

    /** 成功加载序号（诊断用）。 */
    static long loadSequence = 0;

    /** 客户端最近一次同步收到的 revision（-1 = 尚未同步）。 */
    static volatile long clientRevision = -1L;

    private DefsManager() {}

    // ===== 定义查询（服务端与客户端共用，读静态缓存） =====

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

    /** 获取所有声明为特殊机制的物品 id（special_mechanics 文件夹声明并集） */
    public static Set<String> getSpecialMechanicItems() {
        return SPECIAL_MECHANIC_ITEMS;
    }

    /** 统一物品级覆盖的只读视图（AttributeManager / 护盾多实例接线使用） */
    public static Map<String, ItemDefinition> getItemDefinitions() {
        return SERVER_ITEM_OVERRIDES;
    }

    // ===== 快照状态（诊断/同步） =====

    /** 最近一次成功发布的定义快照；null = 尚未成功加载过。 */
    public static DefsSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    public static long snapshotRevision() {
        DefsSnapshot snapshot = currentSnapshot;
        return snapshot == null ? 0L : snapshot.revision;
    }

    public static long snapshotLoadId() {
        DefsSnapshot snapshot = currentSnapshot;
        return snapshot == null ? 0L : snapshot.loadId;
    }

    /** 客户端最近一次同步收到的 revision（-1 = 尚未同步）。 */
    public static long clientRevision() {
        return clientRevision;
    }
}
