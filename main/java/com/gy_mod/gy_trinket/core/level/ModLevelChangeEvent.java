package com.gy_mod.gy_trinket.core.level;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * 光点等级变动事件
 * 当玩家的光点等级发生变化时触发（升级或降级）
 */
public class ModLevelChangeEvent extends Event {

    private final UUID playerUUID;
    private final int oldLevel;
    private final int newLevel;

    public ModLevelChangeEvent(UUID playerUUID, int oldLevel, int newLevel) {
        this.playerUUID = playerUUID;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public int getLevelDiff() {
        return newLevel - oldLevel;
    }
}
