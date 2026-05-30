package com.gy_mod.gy_trinket.storage.datacenter;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class PlayerDataCenter {

    private static final ConcurrentHashMap<UUID, PlayerDataEntry> PLAYER_DATA = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ReentrantReadWriteLock> PLAYER_LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, IDataSlot<?>> SLOT_REGISTRY = new LinkedHashMap<>();

    private static volatile List<IDataSlot<?>> sortedSlots = Collections.emptyList();

    private PlayerDataCenter() {}

    public static void registerSlot(IDataSlot<?> slot) {
        if (SLOT_REGISTRY.containsKey(slot.getKey())) {
            gytrinket.LOGGER.warn("数据槽 {} 已注册，跳过重复注册", slot.getKey());
            return;
        }
        SLOT_REGISTRY.put(slot.getKey(), slot);
        rebuildSortedSlots();
        gytrinket.LOGGER.debug("注册数据槽: {} (持久化: {}, 优先级: {})", slot.getKey(), slot.isPersistent(), slot.getPriority());
    }

    private static void rebuildSortedSlots() {
        sortedSlots = SLOT_REGISTRY.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
    }

    private static List<IDataSlot<?>> getSortedSlots() {
        return sortedSlots;
    }

    public static <T> T getData(UUID playerUUID, String key) {
        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(playerUUID);
            if (entry == null) {
                return null;
            }
            return entry.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T> T getData(Player player, String key) {
        return getData(player.getUUID(), key);
    }

    public static <T> T getDataOrDefault(UUID playerUUID, String key, T defaultValue) {
        T value = getData(playerUUID, key);
        return value != null ? value : defaultValue;
    }

    public static <T> void setData(UUID playerUUID, String key, T value) {
        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            PLAYER_DATA.computeIfAbsent(playerUUID, k -> new PlayerDataEntry()).set(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T> void setData(Player player, String key, T value) {
        setData(player.getUUID(), key, value);
    }

    public static <T> T getOrCreateData(UUID playerUUID, String key, Class<T> type) {
        T value = getData(playerUUID, key);
        if (value != null) {
            return value;
        }
        IDataSlot<?> slot = SLOT_REGISTRY.get(key);
        if (slot == null) {
            return null;
        }
        T defaultValue = (T) slot.getDefault(playerUUID);
        if (defaultValue != null) {
            setData(playerUUID, key, defaultValue);
        }
        return defaultValue;
    }

    public static boolean hasData(UUID playerUUID, String key) {
        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(playerUUID);
            return entry != null && entry.has(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void removeData(UUID playerUUID, String key) {
        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(playerUUID);
            if (entry != null) {
                entry.remove(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void onLogin(Player player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        CompoundTag savedData = storage.getPlayerData(uuid);

        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(uuid, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.computeIfAbsent(uuid, k -> new PlayerDataEntry());

            for (IDataSlot<?> slot : getSortedSlots()) {
                if (slot.isPersistent() && savedData != null && savedData.contains(slot.getKey())) {
                    CompoundTag slotTag = savedData.getCompound(slot.getKey());
                    Object value = slot.loadFromNBT(slotTag);
                    if (value != null) {
                        entry.set(slot.getKey(), value);
                    }
                } else {
                    Object defaultValue = slot.getDefault(uuid);
                    if (defaultValue != null) {
                        entry.set(slot.getKey(), defaultValue);
                    }
                }
                Object current = entry.get(slot.getKey());
                if (current != null) {
                    try {
                        ((IDataSlot) slot).onLogin(uuid, current);
                    } catch (Exception e) {
                        gytrinket.LOGGER.error("数据槽 {} onLogin 回调异常", slot.getKey(), e);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        gytrinket.LOGGER.debug("玩家 {} 登录，数据从SavedData恢复完成", uuid);
    }

    public static void onLogout(Player player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);

        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(uuid, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(uuid);
            if (entry == null) {
                return;
            }

            for (IDataSlot<?> slot : getSortedSlots()) {
                Object value = entry.get(slot.getKey());
                if (value == null) {
                    continue;
                }

                try {
                    ((IDataSlot) slot).onLogout(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onLogout 回调异常", slot.getKey(), e);
                }
            }

            CompoundTag savedData = storage.hasPlayerData(uuid)
                ? storage.getPlayerData(uuid).copy()
                : new CompoundTag();
            for (IDataSlot<?> slot : getSortedSlots()) {
                if (!slot.isPersistent()) {
                    continue;
                }
                Object value = entry.get(slot.getKey());
                if (value == null) {
                    savedData.remove(slot.getKey());
                    continue;
                }
                CompoundTag slotTag = new CompoundTag();
                try {
                    ((IDataSlot) slot).saveToNBT(slotTag, value);
                    if (!slotTag.isEmpty()) {
                        savedData.put(slot.getKey(), slotTag);
                    } else {
                        savedData.remove(slot.getKey());
                    }
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} 保存NBT异常", slot.getKey(), e);
                }
            }

            if (!savedData.isEmpty()) {
                storage.putPlayerData(uuid, savedData);
            } else {
                storage.removePlayerData(uuid);
            }

            for (IDataSlot<?> slot : getSortedSlots()) {
                Object value = entry.get(slot.getKey());
                if (value == null) {
                    continue;
                }
                try {
                    ((IDataSlot) slot).onCleanup(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onCleanup 回调异常", slot.getKey(), e);
                }
            }

            entry.clear();
            PLAYER_DATA.remove(uuid);
        } finally {
            lock.writeLock().unlock();
            PLAYER_LOCKS.remove(uuid);
        }

        gytrinket.LOGGER.debug("玩家 {} 退出，数据保存到SavedData并清理完成", uuid);
    }

    public static void onRespawn(Player player) {
        UUID uuid = player.getUUID();
        ReentrantReadWriteLock lock = PLAYER_LOCKS.computeIfAbsent(uuid, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(uuid);
            if (entry == null) {
                return;
            }

            for (IDataSlot<?> slot : getSortedSlots()) {
                Object value = entry.get(slot.getKey());
                if (value != null) {
                    try {
                        ((IDataSlot) slot).onRespawn(uuid, value);
                    } catch (Exception e) {
                        gytrinket.LOGGER.error("数据槽 {} onRespawn 回调异常", slot.getKey(), e);
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void onClone(Player originalPlayer, Player newPlayer) {
        UUID oldUUID = originalPlayer.getUUID();
        UUID newUUID = newPlayer.getUUID();

        ReentrantReadWriteLock oldLock = PLAYER_LOCKS.get(oldUUID);
        if (oldLock == null) {
            return;
        }

        oldLock.writeLock().lock();
        try {
            PlayerDataEntry oldEntry = PLAYER_DATA.get(oldUUID);
            if (oldEntry == null) {
                return;
            }

            PlayerDataEntry newEntry = new PlayerDataEntry();

            for (IDataSlot<?> slot : getSortedSlots()) {
                Object oldValue = oldEntry.get(slot.getKey());
                if (oldValue == null) {
                    continue;
                }

                Object newValue = slot.getDefault(newUUID);
                if (slot.isPersistent()) {
                    CompoundTag tempTag = new CompoundTag();
                    try {
                        ((IDataSlot) slot).saveToNBT(tempTag, oldValue);
                        newValue = slot.loadFromNBT(tempTag);
                    } catch (Exception e) {
                        gytrinket.LOGGER.error("数据槽 {} Clone序列化异常", slot.getKey(), e);
                    }
                }

                if (newValue != null) {
                    newEntry.set(slot.getKey(), newValue);
                }

                try {
                    ((IDataSlot) slot).onClone(oldUUID, newUUID, oldValue, newValue);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onClone 回调异常", slot.getKey(), e);
                }
            }

            PLAYER_DATA.put(newUUID, newEntry);
            PLAYER_LOCKS.computeIfAbsent(newUUID, k -> new ReentrantReadWriteLock());
        } finally {
            oldLock.writeLock().unlock();
        }

        gytrinket.LOGGER.debug("玩家数据克隆: {} -> {}", oldUUID, newUUID);
    }

    public static CompoundTag buildSnapshot(UUID playerUUID) {
        CompoundTag snapshot = new CompoundTag();

        ReentrantReadWriteLock lock = PLAYER_LOCKS.get(playerUUID);
        if (lock == null) {
            return snapshot;
        }

        lock.readLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(playerUUID);
            if (entry == null) {
                return snapshot;
            }

            for (IDataSlot<?> slot : getSortedSlots()) {
                Object value = entry.get(slot.getKey());
                if (value == null) {
                    continue;
                }
                CompoundTag slotTag = new CompoundTag();
                try {
                    ((IDataSlot) slot).saveToNBT(slotTag, value);
                    if (!slotTag.isEmpty()) {
                        snapshot.put(slot.getKey(), slotTag);
                    }
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} 构建快照异常", slot.getKey(), e);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return snapshot;
    }

    public static void clearPlayerData(UUID playerUUID) {
        ReentrantReadWriteLock lock = PLAYER_LOCKS.get(playerUUID);
        if (lock == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            PlayerDataEntry entry = PLAYER_DATA.get(playerUUID);
            if (entry != null) {
                entry.clear();
            }
            PLAYER_DATA.remove(playerUUID);
        } finally {
            lock.writeLock().unlock();
            PLAYER_LOCKS.remove(playerUUID);
        }
    }

    public static void clearAll() {
        PLAYER_DATA.clear();
        PLAYER_LOCKS.clear();
    }

    public static Set<String> getRegisteredSlotKeys() {
        return Collections.unmodifiableSet(SLOT_REGISTRY.keySet());
    }

    public static boolean isSlotRegistered(String key) {
        return SLOT_REGISTRY.containsKey(key);
    }
}
