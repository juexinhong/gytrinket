package com.gytrinket.gytrinket.core.shield;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.BodyTypeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

@EventBusSubscriber(modid = gytrinket.MODID)
public class DisableSystem {

    private static final Map<UUID, Set<String>> PLAYER_DISABLED_ITEMS = new HashMap<>();
    private static final Map<String, Set<String>> ITEM_DISABLE_TARGETS = new HashMap<>();
    private static final Map<String, Set<String>> ITEM_DEPENDENCIES = new HashMap<>();
    private static final Map<String, Set<String>> ITEM_DISABLE_CATEGORIES = new HashMap<>();
    private static final Map<String, List<List<String>>> ITEM_DEPENDENCIES_ALL = new HashMap<>();

    private DisableSystem() {}

    public static void loadConfig() {
        ITEM_DISABLE_TARGETS.clear();
        DefsManager.getDisableTargets().forEach((itemId, targets) -> {
            if (!targets.isEmpty()) {
                ITEM_DISABLE_TARGETS.put(itemId, new HashSet<>(targets));
                gytrinket.LOGGER.info("注册禁用目标: {} -> {}", itemId, targets);
            }
        });

        ITEM_DEPENDENCIES.clear();
        DefsManager.getDependencies().forEach((itemId, deps) -> {
            if (!deps.isEmpty()) {
                ITEM_DEPENDENCIES.put(itemId, new HashSet<>(deps));
                gytrinket.LOGGER.info("注册物品依赖(OR): {} -> {}", itemId, deps);
            }
        });

        ITEM_DISABLE_CATEGORIES.clear();
        DefsManager.getDisableCategories().forEach((itemId, categories) -> {
            if (!categories.isEmpty()) {
                ITEM_DISABLE_CATEGORIES.put(itemId, new HashSet<>(categories));
                gytrinket.LOGGER.info("注册类别禁用: {} -> {}", itemId, categories);
            }
        });

        ITEM_DEPENDENCIES_ALL.clear();
        DefsManager.getDependenciesAll().forEach((itemId, groups) -> {
            if (!groups.isEmpty()) {
                ITEM_DEPENDENCIES_ALL.put(itemId, groups);
                gytrinket.LOGGER.info("注册物品依赖(AND/OR组): {} -> {}", itemId, groups);
            }
        });

        gytrinket.LOGGER.info("禁用系统配置加载完成，禁用目标: {} 项，OR依赖: {} 项，类别禁用: {} 项，AND依赖: {} 项",
                ITEM_DISABLE_TARGETS.size(), ITEM_DEPENDENCIES.size(),
                ITEM_DISABLE_CATEGORIES.size(), ITEM_DEPENDENCIES_ALL.size());
    }

    public static void updateDisabledItems(UUID playerUUID) {
        // 每次重算前先清理护盾类型内存状态，防止残留旧实体数据导致计算错误
        // 这确保了无论调用时机（重生、登录、属性变化），都从干净状态开始
        ShieldTypeManager.clearPlayerShieldTypes(playerUUID);

        Set<String> storeItemIds = new LinkedHashSet<>();
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    storeItemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }

        Set<String> disabledItems = new HashSet<>();

        applyDisableTargets(storeItemIds, disabledItems);
        propagateDependencies(storeItemIds, disabledItems);

        Set<String> shieldDisabled = ShieldTypeManager.updateShieldTypes(playerUUID, disabledItems);
        disabledItems.addAll(shieldDisabled);

        Set<String> bodyDisabled = BodyTypeManager.updateBodyTypes(playerUUID, disabledItems);
        disabledItems.addAll(bodyDisabled);

        propagateDependencies(storeItemIds, disabledItems);

        PLAYER_DISABLED_ITEMS.put(playerUUID, disabledItems);
    }

    private static void applyDisableTargets(Set<String> storeItemIds, Set<String> disabledItems) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String itemId : storeItemIds) {
                if (disabledItems.contains(itemId)) continue;
                // 互斥：禁用的具体目标（双方都装备时目标失效）
                Set<String> targets = ITEM_DISABLE_TARGETS.get(itemId);
                if (targets != null) {
                    for (String target : targets) {
                        if (storeItemIds.contains(target) && disabledItems.add(target)) {
                            changed = true;
                        }
                    }
                }
                // 类别禁用：装备 item 时整个类别（如护盾）全部失效，无需目标在库
                Set<String> categories = ITEM_DISABLE_CATEGORIES.get(itemId);
                if (categories != null) {
                    for (String category : categories) {
                        for (String target : Config.resolveDisableCategory(category)) {
                            if (disabledItems.add(target)) {
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void propagateDependencies(Set<String> storeItemIds, Set<String> disabledItems) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String itemId : storeItemIds) {
                if (disabledItems.contains(itemId)) continue;

                // OR逻辑（dependsOn）：所有依赖都未装备（不存在或被禁用）时才禁用
                Set<String> deps = ITEM_DEPENDENCIES.get(itemId);
                if (deps != null && !deps.isEmpty()) {
                    boolean anyDepAvailable = false;
                    for (String dep : deps) {
                        if (storeItemIds.contains(dep) && !disabledItems.contains(dep)) {
                            anyDepAvailable = true;
                            break;
                        }
                    }
                    if (!anyDepAvailable) {
                        disabledItems.add(itemId);
                        changed = true;
                        continue;
                    }
                }

                // AND逻辑（dependsOnAll，OR 组）：外层组全部满足（每组内任意一个依赖可用）
                List<List<String>> groups = ITEM_DEPENDENCIES_ALL.get(itemId);
                if (groups != null && !groups.isEmpty()) {
                    boolean allGroupsSatisfied = true;
                    for (List<String> group : groups) {
                        boolean anyInGroup = false;
                        for (String dep : group) {
                            if (isDependencyAvailable(storeItemIds, disabledItems, dep)) {
                                anyInGroup = true;
                                break;
                            }
                        }
                        if (!anyInGroup) {
                            allGroupsSatisfied = false;
                            break;
                        }
                    }
                    if (!allGroupsSatisfied) {
                        disabledItems.add(itemId);
                        changed = true;
                    }
                }
            }
        }
    }

    /** 判断单个依赖是否可用：支持普通物品 id 与 "category:xxx" 类别引用 */
    private static boolean isDependencyAvailable(Set<String> storeItemIds, Set<String> disabledItems, String dep) {
        if (dep.startsWith("category:")) {
            Set<String> items = Config.resolveDependencyCategory(dep.substring("category:".length()));
            for (String id : items) {
                if (storeItemIds.contains(id) && !disabledItems.contains(id)) {
                    return true;
                }
            }
            return false;
        }
        return storeItemIds.contains(dep) && !disabledItems.contains(dep);
    }

    public static boolean isItemDisabled(UUID playerUUID, String itemId) {
        return PLAYER_DISABLED_ITEMS.getOrDefault(playerUUID, Collections.emptySet()).contains(itemId);
    }

    public static boolean isItemDisabled(UUID playerUUID, ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return isItemDisabled(playerUUID, itemId);
    }

    public static Set<String> getDisabledItems(UUID playerUUID) {
        return Collections.unmodifiableSet(PLAYER_DISABLED_ITEMS.getOrDefault(playerUUID, Collections.emptySet()));
    }

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_DISABLED_ITEMS.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_DISABLED_ITEMS.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerData(player.getUUID());
        }
    }
}
