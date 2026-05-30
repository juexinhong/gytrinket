package com.gy_mod.gy_trinket.storage.datacenter.slot;

import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

public class LightPointStoreSlot implements IDataSlot<PlayerStore> {

    @Override
    public String getKey() {
        return "light_point_store";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public PlayerStore getDefault(UUID playerUUID) {
        return new PlayerStore(playerUUID);
    }

    @Override
    public PlayerStore loadFromNBT(CompoundTag tag) {
        UUID playerUUID = tag.contains("playerUUID") ? tag.getUUID("playerUUID") : null;
        if (playerUUID == null) {
            return null;
        }
        PlayerStore store = new PlayerStore(playerUUID);
        if (tag.contains("Items")) {
            ItemStackHandler handler = store.getItemHandler();
            ListTag itemsTag = tag.getList("Items", 10);
            for (int i = 0; i < handler.getSlots() && i < itemsTag.size(); i++) {
                CompoundTag itemTag = itemsTag.getCompound(i);
                if (!itemTag.isEmpty()) {
                    ItemStack stack = ItemStack.of(itemTag);
                    handler.setStackInSlot(i, stack);
                }
            }
        }
        return store;
    }

    @Override
    public void saveToNBT(CompoundTag tag, PlayerStore value) {
        tag.putUUID("playerUUID", value.getPlayerUUID());
        ItemStackHandler handler = value.getItemHandler();
        ListTag itemsTag = new ListTag();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            CompoundTag itemTag = new CompoundTag();
            if (!stack.isEmpty()) {
                stack.save(itemTag);
            }
            itemsTag.add(itemTag);
        }
        tag.put("Items", itemsTag);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
