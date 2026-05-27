package com.gy_mod.gy_trinket.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * 护盾破裂事件
 * <p>
 * 当玩家的护盾值从大于0降低至0时触发此事件。
 * <p>
 * 可用于触发粒子效果、音效或其他护盾破裂相关的逻辑。
 */
public class ShieldBreakEvent extends Event {

    private final UUID playerUUID;
    private final Player player;
    private final double previousShield;

    /**
     * 构造护盾破裂事件
     *
     * @param playerUUID    玩家UUID
     * @param player        玩家对象
     * @param previousShield 破裂前的护盾值
     */
    public ShieldBreakEvent(UUID playerUUID, Player player, double previousShield) {
        this.playerUUID = playerUUID;
        this.player = player;
        this.previousShield = previousShield;
    }

    /**
     * 获取玩家UUID
     *
     * @return 玩家UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取玩家对象
     *
     * @return 玩家对象
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取破裂前的护盾值
     *
     * @return 破裂前的护盾值
     */
    public double getPreviousShield() {
        return previousShield;
    }
}