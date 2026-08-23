package com.gytrinket.gytrinket.core.random_build;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.event.QuickEquipEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 随机构建系统
 *
 * 启用后玩家面板经验条上方会出现 3x3 随机池，
 * 玩家消耗 1 个升级点即可将随机物品装备到光点核心。
 *
 * 随机池优先级：
 * 1. 玩家核心内没有护盾类物品时 -> 只给基础护盾（不含带"+"的强化护盾）
 * 2. 没有机身类物品时 -> 只给机身
 * 3. 否则 -> 随机模块（注册了属性或特殊机制的物品，排除零件/护盾/机身）
 *
 * 模块过滤规则：
 * - 不出现玩家已拥有的物品
 * - 不出现依赖未满足的物品（如模块2依赖模块1但玩家没有模块1）
 * - 不出现被禁用/互斥的物品（如模块1禁用了模块2且玩家装备了模块1）
 */
public class RandomBuildManager {

    public static final int POOL_SIZE = 9;
    /** 兑换一件随机物品消耗的升级点 */
    public static final int EQUIP_COST = 1;

    /** 缓存每个玩家最近生成的随机池，用于装备时的合法性校验 */
    private static final Map<UUID, List<String>> CACHED_POOLS = new HashMap<>();

    private RandomBuildManager() {}

    // ==================== 持久化 ====================

    /** 保存随机池到玩家持久数据（重进游戏后保持） */
    private static void savePool(UUID playerUUID, List<String> pool) {
        PlayerDataCenter.setData(playerUUID, RandomBuildDataSlot.KEY, new ArrayList<>(pool));
    }

    /** 清除玩家持久化的随机池（装备完成后） */
    private static void clearStoredPool(UUID playerUUID) {
        PlayerDataCenter.removeData(playerUUID, RandomBuildDataSlot.KEY);
    }

    /** 是否已有可用的随机池（内存缓存或持久化数据） */
    public static boolean hasStoredPool(UUID playerUUID) {
        if (CACHED_POOLS.containsKey(playerUUID)) {
            return !CACHED_POOLS.get(playerUUID).isEmpty();
        }
        List<String> saved = PlayerDataCenter.getData(playerUUID, RandomBuildDataSlot.KEY);
        if (saved != null && !saved.isEmpty()) {
            CACHED_POOLS.put(playerUUID, saved);
            return true;
        }
        return false;
    }

    // ==================== 判定 ====================

    /** 是否为护盾类物品（注册了护盾类型） */
    public static boolean isShieldItem(String itemId, Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl == null) return false;
        return !Config.getItemShieldTypes(rl).isEmpty();
    }

    /** 是否为基础护盾（不含带"+"的强化护盾，如 shield_gy2/shield_aura_ring3 等） */
    public static boolean isBaseShieldItem(String itemId, Item item) {
        return isShieldItem(itemId, item) && !itemId.matches(".*\\d$");
    }

    /** 是否为机身类物品 */
    public static boolean isBodyItem(Item item) {
        return Config.isBodyItem(item);
    }

    /** 是否为零件类物品（id 以 _part 结尾，如 drone_part；纯名零件本身不注册属性/机制，已被 isQuickEquipItem 排除） */
    public static boolean isPartItem(String itemId) {
        return itemId.endsWith("_part");
    }

    /** 是否为模块类物品：注册了属性或特殊机制，且不是护盾/机身/零件 */
    public static boolean isModuleItem(String itemId, Item item) {
        if (!QuickEquipEvent.isQuickEquipItem(itemId, item)) return false;
        if (isShieldItem(itemId, item)) return false;
        if (isBodyItem(item)) return false;
        if (isPartItem(itemId)) return false;
        return true;
    }

    /** 是否被禁用：玩家装备的物品禁用了该物品（互斥/类别），或该物品依赖未满足 */
    private static boolean isDisabledByEquipped(UUID playerUUID, String itemId, Set<String> coreIds) {
        // 互斥：玩家装备了 disabler，其 disables 列表含 itemId
        Map<String, Set<String>> disableTargets = DefsManager.getDisableTargets();
        for (Map.Entry<String, Set<String>> entry : disableTargets.entrySet()) {
            if (coreIds.contains(entry.getKey()) && entry.getValue().contains(itemId)) {
                return true;
            }
        }
        // 类别禁用：玩家装备了 disabler，其 disablesCategories 对应类别含 itemId（如快速重构禁用护盾类）
        Map<String, Set<String>> disableCategories = DefsManager.getDisableCategories();
        for (Map.Entry<String, Set<String>> entry : disableCategories.entrySet()) {
            if (!coreIds.contains(entry.getKey())) continue;
            for (String category : entry.getValue()) {
                if (Config.resolveDisableCategory(category).contains(itemId)) {
                    return true;
                }
            }
        }
        // OR 依赖（dependsOn）：所有依赖都未装备时不可用
        Set<String> deps = DefsManager.getDependencies().get(itemId);
        if (deps != null && !deps.isEmpty()) {
            boolean anyDepAvailable = false;
            for (String dep : deps) {
                if (coreIds.contains(dep)) {
                    anyDepAvailable = true;
                    break;
                }
            }
            if (!anyDepAvailable) return true;
        }
        // AND 依赖（dependsOnAll，OR 组）：组间 AND、组内 OR，支持 category:xxx 类别引用
        List<List<String>> groups = DefsManager.getDependenciesAll().get(itemId);
        if (groups != null && !groups.isEmpty()) {
            for (List<String> group : groups) {
                boolean anyInGroup = false;
                for (String dep : group) {
                    if (dependencyAvailable(coreIds, dep)) {
                        anyInGroup = true;
                        break;
                    }
                }
                if (!anyInGroup) return true;
            }
        }
        return false;
    }

    /** 判断单个依赖在玩家核心中是否可用：支持普通物品 id 与 "category:xxx" 类别引用 */
    private static boolean dependencyAvailable(Set<String> coreIds, String dep) {
        if (dep.startsWith("category:")) {
            Set<String> items = Config.resolveDependencyCategory(dep.substring("category:".length()));
            for (String id : items) {
                if (coreIds.contains(id)) return true;
            }
            return false;
        }
        return coreIds.contains(dep);
    }

    /** 护盾类别是否被禁用（如装备了快速重构模块 -> 禁用了整个护盾类） */
    private static boolean isShieldCategoryDisabled(Set<String> coreIds) {
        Map<String, Set<String>> disableCategories = DefsManager.getDisableCategories();
        for (Map.Entry<String, Set<String>> entry : disableCategories.entrySet()) {
            if (coreIds.contains(entry.getKey()) && entry.getValue().contains("shields")) {
                return true;
            }
        }
        return false;
    }

    // ==================== 候选物品收集 ====================

    private static List<String> collectItems(ItemFilter filter) {
        List<String> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null) continue;
            if (!gytrinket.MODID.equals(rl.getNamespace())) continue;
            String itemId = rl.toString();
            if (filter.accept(itemId, item)) {
                result.add(itemId);
            }
        }
        return result;
    }

    private interface ItemFilter {
        boolean accept(String itemId, Item item);
    }

    // ==================== 随机池生成 ====================

    public static List<String> generatePool(ServerPlayer player) {
        return generatePool(player, null);
    }

    /**
     * 生成随机池
     * @param player 玩家
     * @param avoid 需要尽量避免的物品 id 集合（如上一轮随机池），可为 null
     */
    public static List<String> generatePool(ServerPlayer player, Set<String> avoid) {
        if (!Config.isRandomBuildEnabled()) return List.of();

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        Set<String> coreIds = new HashSet<>();
        if (store != null) {
            ItemStackHandler handler = store.getItemHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (rl != null) coreIds.add(rl.toString());
                }
            }
        }

        List<String> pool = new ArrayList<>();
        boolean hasShield = false;
        boolean hasBody = false;
        for (String id : coreIds) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) continue;
            if (isShieldItem(id, item)) hasShield = true;
            if (isBodyItem(item)) hasBody = true;
        }

        if (!hasShield && !isShieldCategoryDisabled(coreIds)) {
            // 护盾池：仅基础护盾，过滤已拥有/被禁用（护盾类别被禁用时直接跳过，不进入护盾池）
            pool.addAll(collectItems((id, item) -> isBaseShieldItem(id, item)
                    && !coreIds.contains(id)
                    && !isDisabledByEquipped(player.getUUID(), id, coreIds)));
        } else if (!hasBody) {
            // 机身池
            pool.addAll(collectItems((id, item) -> isBodyItem(item)
                    && !coreIds.contains(id)
                    && !isDisabledByEquipped(player.getUUID(), id, coreIds)));
        } else {
            // 模块池：排除已有、依赖未满足、被禁用的
            pool.addAll(collectItems((id, item) -> isModuleItem(id, item)
                    && !coreIds.contains(id)
                    && !isDisabledByEquipped(player.getUUID(), id, coreIds)));
        }

        // 尽量不与上一轮重复：优先从排除 avoid 的候选中取，不足时再补足
        if (avoid != null && !avoid.isEmpty()) {
            List<String> avoided = new ArrayList<>();
            for (String id : pool) {
                if (!avoid.contains(id)) avoided.add(id);
            }
            if (avoided.size() < pool.size() && avoided.size() < POOL_SIZE) {
                List<String> rest = new ArrayList<>();
                for (String id : pool) {
                    if (!avoided.contains(id)) rest.add(id);
                }
                Collections.shuffle(rest);
                int need = Math.min(POOL_SIZE - avoided.size(), rest.size());
                for (int i = 0; i < need; i++) {
                    avoided.add(rest.get(i));
                }
            }
            pool = avoided;
        }

        Collections.shuffle(pool);
        if (pool.size() > POOL_SIZE) {
            pool = new ArrayList<>(pool.subList(0, POOL_SIZE));
        }
        CACHED_POOLS.put(player.getUUID(), pool);
        savePool(player.getUUID(), pool);
        return pool;
    }

    public static List<String> getCurrentPool(UUID playerUUID) {
        List<String> pool = CACHED_POOLS.get(playerUUID);
        if (pool != null) return pool;
        List<String> saved = PlayerDataCenter.getData(playerUUID, RandomBuildDataSlot.KEY);
        if (saved != null) {
            CACHED_POOLS.put(playerUUID, saved);
            return saved;
        }
        return List.of();
    }

    /** 玩家登出时清理内存缓存（持久化数据保留，重进后恢复） */
    public static void clearPlayerData(UUID playerUUID) {
        CACHED_POOLS.remove(playerUUID);
    }

    // ==================== 装备 ====================

    /**
     * 将随机池中的物品装备到光点核心并消耗 1 个升级点
     * @return 成功返回 true；失败返回 false（调用方负责发送提示消息）
     */
    public static boolean equipItem(ServerPlayer player, String itemId) {
        if (!Config.isRandomBuildEnabled()) return false;
        UUID uuid = player.getUUID();

        if (ModLevelManager.getUpgradePoints(uuid) < EQUIP_COST) return false;

        List<String> pool = CACHED_POOLS.get(uuid);
        if (pool == null || !pool.contains(itemId)) return false;

        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return false;

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store == null) return false;
        ItemStackHandler handler = store.getItemHandler();

        // 再次校验依赖/禁用/重复
        Set<String> coreIds = new HashSet<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ResourceLocation sr = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (sr != null) coreIds.add(sr.toString());
            }
        }
        if (coreIds.contains(itemId)) return false;
        if (isDisabledByEquipped(uuid, itemId, coreIds)) return false;

        int emptySlot = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }
        if (emptySlot < 0) return false;

        handler.setStackInSlot(emptySlot, new ItemStack(item, 1));
        if (!ModLevelManager.consumeUpgradePoints(uuid, EQUIP_COST)) {
            handler.setStackInSlot(emptySlot, ItemStack.EMPTY);
            return false;
        }

        CACHED_POOLS.remove(uuid);
        clearStoredPool(uuid);
        return true;
    }
}
