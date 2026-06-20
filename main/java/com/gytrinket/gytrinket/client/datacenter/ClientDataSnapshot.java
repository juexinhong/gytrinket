package com.gytrinket.gytrinket.client.datacenter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientDataSnapshot {

    private ItemStack[] items = new ItemStack[0];
    private double currentShield = 0;
    private double maxShield = 0;
    private String activeShieldType = "none";
    private Map<String, Double> keyAttributes = new HashMap<>();

    public void loadFromNBT(CompoundTag tag) {
        if (tag.contains("items")) {
            ListTag itemList = tag.getList("items", Tag.TAG_COMPOUND);
            items = new ItemStack[27];
            for (int i = 0; i < 27; i++) {
                items[i] = ItemStack.EMPTY;
            }
            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag itemTag = itemList.getCompound(i);
                int slot = itemTag.contains("Slot") ? itemTag.getByte("Slot") : i;
                if (slot >= 0 && slot < 27) {
                    items[slot] = ItemStack.parse(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                }
            }
        }

        if (tag.contains("currentShield")) {
            currentShield = tag.getDouble("currentShield");
        }
        if (tag.contains("maxShield")) {
            maxShield = tag.getDouble("maxShield");
        }
        if (tag.contains("activeShieldType")) {
            activeShieldType = tag.getString("activeShieldType");
        }

        if (tag.contains("attributes")) {
            CompoundTag attrTag = tag.getCompound("attributes");
            Map<String, Double> attrs = new HashMap<>();
            for (String key : attrTag.getAllKeys()) {
                attrs.put(key, attrTag.getDouble(key));
            }
            keyAttributes = attrs;
        }
    }

    public boolean hasItem(Item item) {
        if (item == null || items == null) {
            return false;
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public boolean hasItem(ItemStack targetStack) {
        if (targetStack == null || targetStack.isEmpty() || items == null) {
            return false;
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, targetStack)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getItemInSlot(int slot) {
        if (items == null || slot < 0 || slot >= items.length) {
            return ItemStack.EMPTY;
        }
        return items[slot];
    }

    public int getSlotCount() {
        return items != null ? items.length : 0;
    }

    public double getCurrentShield() {
        return currentShield;
    }

    public double getMaxShield() {
        return maxShield;
    }

    public String getActiveShieldType() {
        return activeShieldType;
    }

    public Double getAttribute(String name) {
        return keyAttributes.get(name);
    }

    public Map<String, Double> getAttributes() {
        return Collections.unmodifiableMap(keyAttributes);
    }

    public void reset() {
        items = new ItemStack[0];
        currentShield = 0;
        maxShield = 0;
        activeShieldType = "none";
        keyAttributes.clear();
    }
}
