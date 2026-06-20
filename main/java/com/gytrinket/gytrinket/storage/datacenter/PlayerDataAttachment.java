package com.gytrinket.gytrinket.storage.datacenter;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据附件 - 绑定到玩家实体，随玩家自动保存/加载/死亡复制
 * 替代旧的 SavedData + ConcurrentHashMap 方案
 *
 * 存储内容：
 * - IDataSlot 注册的持久化数据（light_point_store, upgrade_data, shield, health）
 * - 构造体/无人机数据（construct_manager, drone_array）
 * - 护盾移植数据（shield_transfer）
 */
public class PlayerDataAttachment implements INBTSerializable<CompoundTag> {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
    /** 额外的NBT数据（构造体、无人机、护盾移植等） */
    private final CompoundTag extraNbt = new CompoundTag();
    private UUID playerUUID;

    public PlayerDataAttachment() {
    }

    public void setPlayerUUID(UUID uuid) {
        this.playerUUID = uuid;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getDataOrDefault(String key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    public <T> void setData(String key, T value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
    }

    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    public void removeData(String key) {
        data.remove(key);
    }

    public void clear() {
        data.clear();
        extraNbt.getAllKeys().clear();
    }

    // ===== 额外NBT数据访问（构造体/无人机/护盾移植） =====

    public CompoundTag getExtraNbt() {
        return extraNbt;
    }

    public boolean hasExtraData(String key) {
        return extraNbt.contains(key);
    }

    public CompoundTag getExtraData(String key) {
        return extraNbt.getCompound(key);
    }

    public void setExtraData(String key, CompoundTag tag) {
        extraNbt.put(key, tag);
    }

    public void removeExtraData(String key) {
        extraNbt.remove(key);
    }

    // ===== 初始化 =====

    /**
     * 初始化默认数据（首次登录时调用）
     */
    public void initializeDefaults(UUID uuid) {
        this.playerUUID = uuid;
        for (IDataSlot<?> slot : PlayerDataCenter.getSortedSlots()) {
            if (!data.containsKey(slot.getKey())) {
                Object defaultValue = slot.getDefault(uuid);
                if (defaultValue != null) {
                    data.put(slot.getKey(), defaultValue);
                }
            }
        }
    }

    // ===== 序列化 =====

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag rootTag = new CompoundTag();
        if (playerUUID != null) {
            rootTag.putUUID("playerUUID", playerUUID);
        }

        // 序列化 IDataSlot 注册的持久化数据
        for (IDataSlot<?> slot : PlayerDataCenter.getSortedSlots()) {
            if (!slot.isPersistent()) {
                continue;
            }
            Object value = data.get(slot.getKey());
            if (value == null) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            try {
                ((IDataSlot) slot).saveToNBT(slotTag, value);
                if (!slotTag.isEmpty()) {
                    rootTag.put(slot.getKey(), slotTag);
                }
            } catch (Exception e) {
                gytrinket.LOGGER.error("数据槽 {} 序列化异常", slot.getKey(), e);
            }
        }

        // 序列化额外NBT数据
        for (String key : extraNbt.getAllKeys()) {
            rootTag.put(key, extraNbt.get(key));
        }

        return rootTag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag rootTag) {
        if (rootTag.contains("playerUUID")) {
            this.playerUUID = rootTag.getUUID("playerUUID");
        }

        // 已知的 IDataSlot 键
        java.util.Set<String> slotKeys = PlayerDataCenter.getRegisteredSlotKeys();

        for (String key : rootTag.getAllKeys()) {
            if ("playerUUID".equals(key)) continue;

            if (slotKeys.contains(key)) {
                // 反序列化 IDataSlot 数据
                IDataSlot<?> slot = PlayerDataCenter.getSlot(key);
                if (slot != null && slot.isPersistent()) {
                    try {
                        Object value = slot.loadFromNBT(rootTag.getCompound(key));
                        if (value != null) {
                            data.put(key, value);
                        }
                    } catch (Exception e) {
                        gytrinket.LOGGER.error("数据槽 {} 反序列化异常", key, e);
                    }
                }
            } else {
                // 额外NBT数据（construct_manager, drone_array, shield_transfer 等）
                extraNbt.put(key, rootTag.get(key));
            }
        }
    }
}
