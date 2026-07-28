package com.gy_mod.gy_trinket.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家属性计算完毕事件
 * 当玩家的属性计算完成后触发此事件
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class PlayerAttributesCalculatedEvent extends PlayerEvent {
    private final UUID playerUUID;
    private final Map<String, Double> attributes;
    private final ServerPlayer player;
    private final Set<String> dirtyAttributes;
    private final boolean fullRecalculation;

    public PlayerAttributesCalculatedEvent(ServerPlayer player, Map<String, Double> attributes) {
        super(player);
        this.playerUUID = player.getUUID();
        this.attributes = attributes;
        this.player = player;
        this.dirtyAttributes = null;
        this.fullRecalculation = true;
    }

    public PlayerAttributesCalculatedEvent(UUID playerUUID, Map<String, Double> attributes) {
        super(null);
        this.playerUUID = playerUUID;
        this.attributes = attributes;
        this.player = null;
        this.dirtyAttributes = null;
        this.fullRecalculation = true;
    }

    /**
     * 局部重算事件构造函数
     * @param playerUUID 玩家UUID
     * @param attributes 全部属性
     * @param player 玩家实例（可为null）
     * @param dirtyAttributes 受影响的属性名集合
     */
    public PlayerAttributesCalculatedEvent(UUID playerUUID, Map<String, Double> attributes, ServerPlayer player, Set<String> dirtyAttributes) {
        super(player);
        this.playerUUID = playerUUID;
        this.attributes = attributes;
        this.player = player;
        this.dirtyAttributes = dirtyAttributes;
        this.fullRecalculation = false;
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

    /**
     * 是否为全量重算
     */
    public boolean isFullRecalculation() {
        return fullRecalculation;
    }

    /**
     * 获取受影响的属性名集合。
     * 全量重算时返回空集（所有属性都受影响）。
     */
    public Set<String> getDirtyAttributes() {
        return dirtyAttributes != null ? dirtyAttributes : Collections.emptySet();
    }
}