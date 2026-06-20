package com.gytrinket.gytrinket.client.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientPlayerStoreManager {
    private static final Map<UUID, ClientPlayerStore> CLIENT_STORES = new HashMap<>();

    private ClientPlayerStoreManager() {}

    public static ClientPlayerStore getOrCreateClientStore(Player player) {
        return getOrCreateClientStore(player.getUUID());
    }

    public static ClientPlayerStore getOrCreateClientStore(UUID playerUUID) {
        return CLIENT_STORES.computeIfAbsent(playerUUID, ClientPlayerStore::new);
    }

    public static ClientPlayerStore getClientStore(Player player) {
        return getClientStore(player.getUUID());
    }

    public static ClientPlayerStore getClientStore(UUID playerUUID) {
        return CLIENT_STORES.get(playerUUID);
    }

    public static boolean hasClientStore(UUID playerUUID) {
        return CLIENT_STORES.containsKey(playerUUID);
    }

    public static void removeClientStore(UUID playerUUID) {
        CLIENT_STORES.remove(playerUUID);
    }

    public static void clear() {
        CLIENT_STORES.clear();
    }

    public static class ClientPlayerStore {
        private final UUID playerUUID;
        private final ItemStack[] items;

        public ClientPlayerStore(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.items = new ItemStack[27];
            for (int i = 0; i < 27; i++) {
                items[i] = ItemStack.EMPTY;
            }
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= 27) {
                return ItemStack.EMPTY;
            }
            return items[slot];
        }

        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot >= 0 && slot < 27) {
                this.items[slot] = stack;
            }
        }

        public int getSlotCount() {
            return 27;
        }

        public boolean hasItem(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            for (ItemStack item : items) {
                if (!item.isEmpty() && ItemStack.isSameItem(item, stack)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasItem(net.minecraft.world.item.Item item) {
            if (item == null) {
                return false;
            }
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && stack.getItem() == item) {
                    return true;
                }
            }
            return false;
        }

        public void saveToNBT(CompoundTag tag) {
            ListTag itemList = new ListTag();
            for (int i = 0; i < 27; i++) {
                if (!items[i].isEmpty()) {
                    CompoundTag itemTag = (CompoundTag) items[i].save(net.minecraft.client.Minecraft.getInstance().level.registryAccess());
                    itemTag.putByte("Slot", (byte) i);
                    itemList.add(itemTag);
                }
            }
            tag.put("items", itemList);
        }

        public void loadFromNBT(CompoundTag tag) {
            for (int i = 0; i < 27; i++) {
                items[i] = ItemStack.EMPTY;
            }
            ListTag itemList = tag.getList("items", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag itemTag = itemList.getCompound(i);
                int slot = itemTag.contains("Slot") ? itemTag.getByte("Slot") : i;
                if (slot >= 0 && slot < 27) {
                    items[slot] = ItemStack.parse(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                }
            }
        }
    }
}
