package com.gy_mod.gy_trinket.storage.datacenter.slot;

import com.gy_mod.gy_trinket.core.level.ModLevelData;
import com.gy_mod.gy_trinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class ModLevelDataSlot implements IDataSlot<ModLevelData> {

    @Override
    public String getKey() {
        return "mod_level";
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public ModLevelData getDefault(UUID playerUUID) {
        return new ModLevelData();
    }

    @Override
    public ModLevelData loadFromNBT(CompoundTag tag) {
        return ModLevelData.load(tag);
    }

    @Override
    public void saveToNBT(CompoundTag tag, ModLevelData value) {
        CompoundTag data = value.save();
        tag.merge(data);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
