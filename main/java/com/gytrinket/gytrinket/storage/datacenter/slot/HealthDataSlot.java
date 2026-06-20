package com.gytrinket.gytrinket.storage.datacenter.slot;

import com.gytrinket.gytrinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class HealthDataSlot implements IDataSlot<Double> {

    @Override
    public String getKey() {
        return "health";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public Double getDefault(UUID playerUUID) {
        return -1.0;
    }

    @Override
    public Double loadFromNBT(CompoundTag tag) {
        return tag.contains("currentHealth") ? tag.getDouble("currentHealth") : -1.0;
    }

    @Override
    public void saveToNBT(CompoundTag tag, Double value) {
        if (value != null && value > 0) {
            tag.putDouble("currentHealth", value);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
