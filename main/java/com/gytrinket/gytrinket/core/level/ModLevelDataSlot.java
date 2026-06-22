package com.gytrinket.gytrinket.core.level;

import com.gytrinket.gytrinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 模组等级数据槽 - 通过PlayerDataCenter持久化
 */
public class ModLevelDataSlot implements IDataSlot<ModLevelData> {

    @Override
    public String getKey() {
        return "mod_level";
    }

    @Override
    public int getPriority() {
        return 80;
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
        CompoundTag dataTag = value.save();
        for (String key : dataTag.getAllKeys()) {
            tag.put(key, dataTag.get(key));
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
