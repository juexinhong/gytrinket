package com.gytrinket.gytrinket.core.body;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * 机身冲突管理器
 * <p>
 * 配置中定义的机身物品，玩家光点核心中只生效第一个，后续的机身物品被禁用。
 */
public class BodyTypeManager {

    private BodyTypeManager() {}

    /**
     * 更新玩家机身类型，返回因冲突而被禁用的物品ID集合
     */
    public static Set<String> updateBodyTypes(UUID playerUUID, Set<String> preDisabledItems) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Collections.emptySet();
        }

        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUUID);
        if (serverPlayer == null) {
            return Collections.emptySet();
        }

        return collectAndResolveConflicts(serverPlayer, preDisabledItems);
    }

    private static Set<String> collectAndResolveConflicts(ServerPlayer player, Set<String> preDisabledItems) {
        Set<String> disabledItemIds = new HashSet<>();
        boolean foundFirst = false;

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store == null) {
            return disabledItemIds;
        }

        ItemStackHandler handler = store.getItemHandler();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) continue;

            if (preDisabledItems.contains(itemId.toString())) continue;

            if (Config.isBodyItem(item)) {
                if (foundFirst) {
                    disabledItemIds.add(itemId.toString());
                } else {
                    foundFirst = true;
                }
            }
        }

        return disabledItemIds;
    }
}
