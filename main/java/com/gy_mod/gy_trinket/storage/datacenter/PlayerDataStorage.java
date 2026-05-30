package com.gy_mod.gy_trinket.storage.datacenter;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataStorage extends SavedData {

    private static final String DATA_NAME = "gy_trinket_player_data";

    private final Map<UUID, CompoundTag> playerDataMap = new HashMap<>();

    public PlayerDataStorage() {}

    public static PlayerDataStorage load(CompoundTag rootTag) {
        PlayerDataStorage storage = new PlayerDataStorage();
        CompoundTag playersTag = rootTag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                storage.playerDataMap.put(uuid, playersTag.getCompound(key));
            } catch (IllegalArgumentException e) {
                gytrinket.LOGGER.warn("无法解析玩家UUID: {}", key);
            }
        }
        gytrinket.LOGGER.debug("从SavedData加载了 {} 个玩家的持久化数据", storage.playerDataMap.size());
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag rootTag) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> entry : playerDataMap.entrySet()) {
            playersTag.put(entry.getKey().toString(), entry.getValue());
        }
        rootTag.put("players", playersTag);
        return rootTag;
    }

    public void putPlayerData(UUID playerUUID, CompoundTag data) {
        playerDataMap.put(playerUUID, data);
        setDirty();
    }

    @Nullable
    public CompoundTag getPlayerData(UUID playerUUID) {
        return playerDataMap.get(playerUUID);
    }

    public void removePlayerData(UUID playerUUID) {
        playerDataMap.remove(playerUUID);
        setDirty();
    }

    public boolean hasPlayerData(UUID playerUUID) {
        return playerDataMap.containsKey(playerUUID);
    }

    public static PlayerDataStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(PlayerDataStorage::load, PlayerDataStorage::new, DATA_NAME);
    }
}
