package com.gytrinket.gytrinket.storage;

import com.gytrinket.gytrinket.event.PlayerLightPointStoreChangedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * 单个玩家的光点核心存储类
 * 负责管理该玩家的 ItemStackHandler
 */
public class PlayerStore {
    // 玩家的 UUID
    private final UUID playerUUID;
    // 物品存储处理器
    private final ItemStackHandler itemHandler;

    /**
     * 构造函数
     * @param playerUUID 玩家的 UUID
     */
    public PlayerStore(UUID playerUUID) {
        this.playerUUID = playerUUID;
        // 创建一个大小为 27 的物品存储
        this.itemHandler = createItemHandler();
    }

    /**
     * 创建带事件监听的 ItemStackHandler
     * @return 配置好的 ItemStackHandler
     */
    private ItemStackHandler createItemHandler() {
        return new ItemStackHandler(27) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                // 当内容变化时触发事件
                handleContentChange();
            }
        };
    }

    /**
     * 处理物品内容变化
     * 触发内容变化事件
     */
    private void handleContentChange() {
        // 触发内容变化事件
        NeoForge.EVENT_BUS.post(new PlayerLightPointStoreChangedEvent(playerUUID));
    }

    /**
     * 获取玩家 UUID
     * @return 玩家 UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取物品存储处理器
     * @return ItemStackHandler 实例
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }
}
