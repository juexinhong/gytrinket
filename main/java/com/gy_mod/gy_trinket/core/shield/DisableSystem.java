package com.gy_mod.gy_trinket.core.shield;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.BodyTypeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        Set<String> storeItemIds = PlayerStoreUtils.getAllEquippedItemIds(playerUUID);

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
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return isItemDisabled(playerUUID, itemId);
    }

    /**
     * 获取物品被禁用的原因（未禁用返回 null）
     * 用于光点核心界面显示禁用提示
     */
    public static String getDisabledReason(UUID playerUUID, String itemId) {
        if (!isItemDisabled(playerUUID, itemId)) return null;

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        Set<String> storeItemIds = PlayerStoreUtils.getAllEquippedItemIds(playerUUID);

        // 互斥：某已装备物品禁用了它
        for (Map.Entry<String, Set<String>> e : ITEM_DISABLE_TARGETS.entrySet()) {
            if (storeItemIds.contains(e.getKey()) && e.getValue().contains(itemId)) {
                return "与 " + itemDisplayName(e.getKey()) + " 互斥";
            }
        }
        // 类别禁用：某已装备物品禁用了它所属的整个类别
        for (Map.Entry<String, Set<String>> e : ITEM_DISABLE_CATEGORIES.entrySet()) {
            if (!storeItemIds.contains(e.getKey())) continue;
            for (String category : e.getValue()) {
                if (Config.resolveDisableCategory(category).contains(itemId)) {
                    return "被 " + itemDisplayName(e.getKey()) + " 禁用的类别";
                }
            }
        }
        // 依赖未满足（dependsOn，OR 依赖）
        Set<String> deps = ITEM_DEPENDENCIES.get(itemId);
        if (deps != null && !deps.isEmpty()) {
            boolean anyAvailable = false;
            for (String dep : deps) {
                if (storeItemIds.contains(dep) && !isItemDisabled(playerUUID, dep)) {
                    anyAvailable = true;
                    break;
                }
            }
            if (!anyAvailable) {
                return "依赖未满足，需要 " + joinDisplayNames(deps);
            }
        }
        // 依赖未满足（dependsOnAll，AND/OR 组）
        List<List<String>> groups = ITEM_DEPENDENCIES_ALL.get(itemId);
        if (groups != null && !groups.isEmpty()) {
            for (List<String> group : groups) {
                boolean anyInGroup = false;
                for (String dep : group) {
                    Set<String> depIds = dep.startsWith("category:")
                            ? Config.resolveDependencyCategory(dep.substring("category:".length()))
                            : Set.of(dep);
                    for (String id : depIds) {
                        if (storeItemIds.contains(id) && !isItemDisabled(playerUUID, id)) {
                            anyInGroup = true;
                            break;
                        }
                    }
                    if (anyInGroup) break;
                }
                if (!anyInGroup) {
                    return "依赖未满足，需要 " + joinDisplayNames(group);
                }
            }
        }
        return "已禁用";
    }

    private static String itemDisplayName(String itemId) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(itemId));
        return item != null && item != net.minecraft.world.item.Items.AIR
                ? item.getName(net.minecraft.world.item.ItemStack.EMPTY).getString() : itemId;
    }

    private static String joinDisplayNames(java.util.Collection<String> ids) {
        return ids.stream().map(DisableSystem::itemDisplayName).collect(java.util.stream.Collectors.joining(" 或 "));
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
