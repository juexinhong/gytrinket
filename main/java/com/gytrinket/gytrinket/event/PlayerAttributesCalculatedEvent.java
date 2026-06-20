package com.gytrinket.gytrinket.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家属性计算完毕事件
 * 当玩家的属性计算完成后触发此事件
 */
public class PlayerAttributesCalculatedEvent extends PlayerEvent {
    private final UUID playerUUID;
    private final Map<String, Double> attributes;
    private final ServerPlayer player;

    public PlayerAttributesCalculatedEvent(ServerPlayer player, Map<String, Double> attributes) {
        super(player);
        this.playerUUID = player.getUUID();
        this.attributes = attributes;
        this.player = player;
    }

    public PlayerAttributesCalculatedEvent(UUID playerUUID, Map<String, Double> attributes) {
        super(null);
        this.playerUUID = playerUUID;
        this.attributes = attributes;
        this.player = null;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Map<String, Double> getAttributes() {
        return attributes;
    }

    public double getAttributeValue(String attributeName) {
        return attributes.getOrDefault(attributeName, 0.0);
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
