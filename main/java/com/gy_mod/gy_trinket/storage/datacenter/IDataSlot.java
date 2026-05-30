package com.gy_mod.gy_trinket.storage.datacenter;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public interface IDataSlot<T> {

    String getKey();

    T getDefault(UUID playerUUID);

    T loadFromNBT(CompoundTag tag);

    void saveToNBT(CompoundTag tag, T value);

    boolean isPersistent();

    default int getPriority() {
        return 0;
    }

    default void onCleanup(UUID playerUUID, T value) {}

    default void onLogin(UUID playerUUID, T value) {}

    default void onLogout(UUID playerUUID, T value) {}

    default void onRespawn(UUID playerUUID, T value) {}

    default void onClone(UUID oldUUID, UUID newUUID, T oldValue, T newValue) {}
}
