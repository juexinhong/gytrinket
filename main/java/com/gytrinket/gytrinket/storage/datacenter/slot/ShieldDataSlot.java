package com.gytrinket.gytrinket.storage.datacenter.slot;

import com.gytrinket.gytrinket.core.shield.ShieldData;
import com.gytrinket.gytrinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
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
        List<ShieldData.InstanceSnapshot> instances = null;
        if (tag.contains("instances", Tag.TAG_LIST)) {
            ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
            instances = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                instances.add(new ShieldData.InstanceSnapshot(
                        t.getString("itemId"),
                        t.contains("type") ? t.getString("type") : null,
                        t.getBoolean("global"),
                        t.getDouble("current"),
                        t.getBoolean("broken"),
                        t.getDouble("progress"),
                        t.getInt("maxCooldown")));
            }
        }
        return instances != null ? new ShieldData(currentShield, maxShield, instances)
                : new ShieldData(currentShield, maxShield);
    }

    @Override
    public void saveToNBT(CompoundTag tag, ShieldData value) {
        tag.putDouble("currentShield", value.getCurrentShield());
        tag.putDouble("maxShield", value.getMaxShield());
        // 实例级快照：登录重建实例时按 identityKey 迁移池量/破盾/冷却状态（多实例持久化）
        List<ShieldData.InstanceSnapshot> instances = value.getInstances();
        if (instances != null) {
            ListTag list = new ListTag();
            for (ShieldData.InstanceSnapshot snapshot : instances) {
                CompoundTag t = new CompoundTag();
                t.putString("itemId", snapshot.itemId());
                if (snapshot.shieldTypeName() != null) {
                    t.putString("type", snapshot.shieldTypeName());
                }
                t.putBoolean("global", snapshot.globalPool());
                t.putDouble("current", snapshot.current());
                t.putBoolean("broken", snapshot.broken());
                t.putDouble("progress", snapshot.cooldownProgress());
                t.putInt("maxCooldown", snapshot.maxCooldown());
                list.add(t);
            }
            tag.put("instances", list);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
