package com.gy_mod.gy_trinket.core.level;

import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.datacenter.PlayerDataCenter;
import net.minecraftforge.common.MinecraftForge;

import java.util.UUID;

/**
 * 光点等级管理器 - 提供等级数据的读写接口
 */
public class ModLevelManager {

    private static final String SLOT_KEY = "mod_level";

    private ModLevelManager() {}

    public static int addUpgradeExp(UUID playerUUID, int amount) {
        if (amount <= 0) return 0;

        ModLevelData data = getOrCreateData(playerUUID);
        if (data == null) return 0;

        int oldLevel = data.getModLevel();
        data.addUpgradeExp(amount);
        PlayerDataCenter.setData(playerUUID, SLOT_KEY, data);

        int newLevel = data.getModLevel();
        if (newLevel != oldLevel) {
            gytrinket.LOGGER.debug("玩家 {} 光点经验+{}，光点等级 {} -> {}",
                    playerUUID, amount, oldLevel, newLevel);
            MinecraftForge.EVENT_BUS.post(new ModLevelChangeEvent(playerUUID, oldLevel, newLevel));
        }

        return newLevel - oldLevel;
    }

    public static boolean consumeUpgradePoints(UUID playerUUID, int amount) {
        ModLevelData data = getData(playerUUID);
        if (data == null) return false;

        boolean success = data.consumeUpgradePoints(amount);
        if (success) {
            PlayerDataCenter.setData(playerUUID, SLOT_KEY, data);
        }
        return success;
    }

    public static int getModLevel(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        return data != null ? data.getModLevel() : 0;
    }

    public static int getUpgradeExp(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        return data != null ? data.getUpgradeExp() : 0;
    }

    public static int getUpgradePoints(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        return data != null ? data.getUpgradePoints() : 0;
    }

    public static float getExpProgress(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        return data != null ? data.getExpProgress() : 0.0f;
    }

    public static int getXpNeededForNextLevel(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        int level = data != null ? data.getModLevel() : 0;
        return ModLevelData.getXpNeededForNextLevel(level);
    }

    public static void resetData(UUID playerUUID) {
        ModLevelData data = getData(playerUUID);
        if (data == null) return;

        int oldLevel = data.getModLevel();
        data.reset();
        PlayerDataCenter.setData(playerUUID, SLOT_KEY, data);

        if (oldLevel != 0) {
            MinecraftForge.EVENT_BUS.post(new ModLevelChangeEvent(playerUUID, oldLevel, 0));
        }
    }

    private static ModLevelData getData(UUID playerUUID) {
        return PlayerDataCenter.getData(playerUUID, SLOT_KEY);
    }

    private static ModLevelData getOrCreateData(UUID playerUUID) {
        return PlayerDataCenter.getOrCreateData(playerUUID, SLOT_KEY, ModLevelData.class);
    }
}
