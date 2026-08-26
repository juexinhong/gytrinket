package com.gy_mod.gy_trinket.core.random_build;

import com.gy_mod.gy_trinket.storage.datacenter.IDataSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 随机构建随机池数据槽 - 通过PlayerDataCenter持久化
 * 玩家重进游戏后随机池保持不变，刷新点用于主动更换池
 */
public class RandomBuildDataSlot implements IDataSlot<List<String>> {

    public static final String KEY = "random_build_pool";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getPriority() {
        return 75;
    }

    @Override
    public List<String> getDefault(UUID playerUUID) {
        return new ArrayList<>();
    }

    @Override
    public List<String> loadFromNBT(CompoundTag tag) {
        List<String> list = new ArrayList<>();
        ListTag lt = tag.getList("pool", Tag.TAG_STRING);
        for (int i = 0; i < lt.size(); i++) {
            list.add(lt.getString(i));
        }
        return list;
    }

    @Override
    public void saveToNBT(CompoundTag tag, List<String> value) {
        ListTag lt = new ListTag();
        if (value != null) {
            for (String s : value) {
                lt.add(StringTag.valueOf(s));
            }
        }
        tag.put("pool", lt);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}

