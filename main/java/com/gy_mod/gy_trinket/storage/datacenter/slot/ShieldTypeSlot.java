package com.gy_mod.gy_trinket.storage.datacenter.slot;

import com.gy_mod.gy_trinket.core.shield.type.IShieldType;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class ShieldTypeSlot implements IDataSlot<String> {

    @Override
    public String getKey() {
        return "active_shield_type";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDefault(UUID playerUUID) {
        return "none";
    }

    @Override
    public String loadFromNBT(CompoundTag tag) {
        return tag.contains("shieldType") ? tag.getString("shieldType") : "none";
    }

    @Override
    public void saveToNBT(CompoundTag tag, String value) {
        tag.putString("shieldType", value != null ? value : "none");
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    public static String determineActiveType(UUID playerUUID) {
        var types = ShieldTypeManager.getPlayerShieldTypes(playerUUID);
        for (IShieldType.ShieldTypeData data : types) {
            if (data.active()) {
                return data.type().getName();
            }
        }
        return "none";
    }
}
