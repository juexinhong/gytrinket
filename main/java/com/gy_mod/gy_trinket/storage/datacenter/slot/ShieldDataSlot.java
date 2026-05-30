package com.gy_mod.gy_trinket.storage.datacenter.slot;

import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class ShieldDataSlot implements IDataSlot<ShieldData> {

    @Override
    public String getKey() {
        return "shield";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public ShieldData getDefault(UUID playerUUID) {
        return new ShieldData(0);
    }

    @Override
    public ShieldData loadFromNBT(CompoundTag tag) {
        double currentShield = tag.contains("currentShield") ? tag.getDouble("currentShield") : 0;
        double maxShield = tag.contains("maxShield") ? tag.getDouble("maxShield") : 0;
        return new ShieldData(currentShield, maxShield);
    }

    @Override
    public void saveToNBT(CompoundTag tag, ShieldData value) {
        tag.putDouble("currentShield", value.getCurrentShield());
        tag.putDouble("maxShield", value.getMaxShield());
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
