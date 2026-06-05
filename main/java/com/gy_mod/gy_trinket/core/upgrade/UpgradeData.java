package com.gy_mod.gy_trinket.core.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class UpgradeData {

    private final Map<String, List<ItemStack>> collectedMaterials = new HashMap<>();

    public void addMaterial(String baseItemKey, ItemStack material) {
        ItemStack single = material.copy();
        single.setCount(1);

        List<ItemStack> list = collectedMaterials.computeIfAbsent(baseItemKey, k -> new ArrayList<>());
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameTags(existing, single)) {
                existing.grow(1);
                return;
            }
        }
        list.add(single);
    }

    public List<ItemStack> getMaterials(String baseItemKey) {
        return collectedMaterials.getOrDefault(baseItemKey, Collections.emptyList());
    }

    public void clearMaterials(String baseItemKey) {
        collectedMaterials.remove(baseItemKey);
    }

    public boolean hasMaterials(String baseItemKey) {
        return collectedMaterials.containsKey(baseItemKey) && !collectedMaterials.get(baseItemKey).isEmpty();
    }

    public Set<String> getBaseItemKeys() {
        return collectedMaterials.keySet();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, List<ItemStack>> entry : collectedMaterials.entrySet()) {
            ListTag listTag = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    stack.save(itemTag);
                    listTag.add(itemTag);
                }
            }
            if (!listTag.isEmpty()) {
                tag.put(entry.getKey(), listTag);
            }
        }
        return tag;
    }

    public static UpgradeData load(CompoundTag tag) {
        UpgradeData data = new UpgradeData();
        for (String key : tag.getAllKeys()) {
            ListTag listTag = tag.getList(key, 10);
            List<ItemStack> materials = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag itemTag = listTag.getCompound(i);
                ItemStack stack = ItemStack.of(itemTag);
                if (!stack.isEmpty()) {
                    materials.add(stack);
                }
            }
            if (!materials.isEmpty()) {
                data.collectedMaterials.put(key, materials);
            }
        }
        return data;
    }
}
