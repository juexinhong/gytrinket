package com.gytrinket.gytrinket.core.upgrade;

import com.gytrinket.gytrinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class UpgradeDataSlot implements IDataSlot<UpgradeData> {

    @Override
    public String getKey() {
        return "upgrade_data";
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public UpgradeData getDefault(UUID playerUUID) {
        return new UpgradeData();
    }

    @Override
    public UpgradeData loadFromNBT(CompoundTag tag) {
        return UpgradeData.load(tag);
    }

    @Override
    public void saveToNBT(CompoundTag tag, UpgradeData value) {
        CompoundTag data = value.save();
        for (String key : data.getAllKeys()) {
            tag.put(key, data.get(key));
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
