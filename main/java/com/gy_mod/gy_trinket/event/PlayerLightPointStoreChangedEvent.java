package com.gy_mod.gy_trinket.event;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 光点核心存储内容变化事件
 * 当玩家的光点核心存储内容发生变化时触发此事件
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class PlayerLightPointStoreChangedEvent extends PlayerEvent {
    // 发生变化的玩家 UUID
    private final UUID playerUUID;

    /**
     * 构造函数
     * @param playerUUID 玩家 UUID
     */
    public PlayerLightPointStoreChangedEvent(UUID playerUUID) {
        super(null);
        this.playerUUID = playerUUID;
    }

    /**
     * 获取玩家 UUID
     * @return 玩家 UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }
}