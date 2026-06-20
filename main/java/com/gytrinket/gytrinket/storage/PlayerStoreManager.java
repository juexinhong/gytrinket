package com.gytrinket.gytrinket.storage;

import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PlayerStoreManager {

    private static final String SLOT_KEY = "light_point_store";

    private PlayerStoreManager() {}

    public static PlayerStore getOrCreatePlayerStore(Player player) {
        return getOrCreatePlayerStore(player.getUUID());
    }

    public static PlayerStore getOrCreatePlayerStore(UUID playerUUID) {
        PlayerStore store = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (store == null) {
            store = new PlayerStore(playerUUID);
            PlayerDataCenter.setData(playerUUID, SLOT_KEY, store);
        }
        return store;
    }

    public static PlayerStore getPlayerStore(Player player) {
        return getPlayerStore(player.getUUID());
    }

    public static PlayerStore getPlayerStore(UUID playerUUID) {
        return PlayerDataCenter.getData(playerUUID, SLOT_KEY);
    }

    public static boolean hasPlayerStore(Player player) {
        return hasPlayerStore(player.getUUID());
    }

    public static boolean hasPlayerStore(UUID playerUUID) {
        return PlayerDataCenter.hasData(playerUUID, SLOT_KEY);
    }

    public static void removePlayerStore(Player player) {
        removePlayerStore(player.getUUID());
    }

    public static void removePlayerStore(UUID playerUUID) {
        PlayerDataCenter.removeData(playerUUID, SLOT_KEY);
    }

    public static net.minecraft.nbt.CompoundTag saveToNBT(Player player) {
        PlayerStore store = getPlayerStore(player);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (store == null) {
            return tag;
        }

        net.minecraft.nbt.ListTag itemsTag = new net.minecraft.nbt.ListTag();
        var handler = store.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            net.minecraft.world.item.ItemStack stack = handler.getStackInSlot(i);
            net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
            if (!stack.isEmpty()) {
                stack.save(player.registryAccess(), itemTag);
            }
            itemsTag.add(itemTag);
        }
        tag.put("Items", itemsTag);

        return tag;
    }

    public static void loadFromNBT(Player player, net.minecraft.nbt.CompoundTag tag) {
        if (!tag.contains("Items")) {
            getOrCreatePlayerStore(player);
            return;
        }

        PlayerStore store = getOrCreatePlayerStore(player);
        var handler = store.getItemHandler();
        net.minecraft.nbt.ListTag itemsTag = tag.getList("Items", 10);

        for (int i = 0; i < handler.getSlots() && i < itemsTag.size(); i++) {
            net.minecraft.nbt.CompoundTag itemTag = itemsTag.getCompound(i);
            if (!itemTag.isEmpty()) {
                net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.parse(player.registryAccess(), itemTag).orElse(net.minecraft.world.item.ItemStack.EMPTY);
                handler.setStackInSlot(i, stack);
            }
        }
    }
}
