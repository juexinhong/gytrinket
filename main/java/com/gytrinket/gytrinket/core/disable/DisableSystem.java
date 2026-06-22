package com.gytrinket.gytrinket.core.disable;

import com.gytrinket.gytrinket.Config;
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

    private DisableSystem() {}

    public static void loadConfig() {
        ITEM_DISABLE_TARGETS.clear();
        for (String entry : Config.ITEM_DISABLE_TARGETS_CONFIG.get()) {
            if (entry.trim().isEmpty()) continue;
            String[] parts = entry.trim().split("\\|");
            if (parts.length < 2) continue;
            String itemId = parts[0].trim();
            Set<String> targets = new HashSet<>();
            for (String target : parts[1].trim().split(",")) {
                String t = target.trim();
                if (!t.isEmpty()) targets.add(t);
            }
            if (!targets.isEmpty()) {
                ITEM_DISABLE_TARGETS.put(itemId, targets);
                gytrinket.LOGGER.info("注册禁用目标: {} -> {}", itemId, targets);
            }
        }

        ITEM_DEPENDENCIES.clear();
        for (String entry : Config.ITEM_DEPENDENCIES_CONFIG.get()) {
            if (entry.trim().isEmpty()) continue;
            String[] parts = entry.trim().split("\\|");
            if (parts.length < 2) continue;
            String itemId = parts[0].trim();
            Set<String> deps = new HashSet<>();
            for (String dep : parts[1].trim().split(",")) {
                String d = dep.trim();
                if (!d.isEmpty()) deps.add(d);
            }
            if (!deps.isEmpty()) {
                ITEM_DEPENDENCIES.put(itemId, deps);
                gytrinket.LOGGER.info("注册物品依赖: {} -> {}", itemId, deps);
            }
        }

        gytrinket.LOGGER.info("禁用系统配置加载完成，禁用目标: {} 项，依赖关系: {} 项",
                ITEM_DISABLE_TARGETS.size(), ITEM_DEPENDENCIES.size());
    }

    public static void updateDisabledItems(UUID playerUUID) {
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

        propagateDependencies(storeItemIds, disabledItems);

        PLAYER_DISABLED_ITEMS.put(playerUUID, disabledItems);
    }

    private static void applyDisableTargets(Set<String> storeItemIds, Set<String> disabledItems) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String itemId : storeItemIds) {
                if (disabledItems.contains(itemId)) continue;
                Set<String> targets = ITEM_DISABLE_TARGETS.get(itemId);
                if (targets == null) continue;
                for (String target : targets) {
                    if (storeItemIds.contains(target) && disabledItems.add(target)) {
                        changed = true;
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
                Set<String> deps = ITEM_DEPENDENCIES.get(itemId);
                if (deps == null) continue;
                // OR逻辑：只有当所有依赖都未装备（不存在或被禁用）时才禁用
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
                }
            }
        }
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
