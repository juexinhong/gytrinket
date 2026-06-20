package com.gytrinket.gytrinket.core.shield_transfer.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

import java.util.UUID;

public class PlayerConstructListChangedEvent extends Event {

    private final UUID playerUUID;
    private final Player player;
    private final Entity construct;
    private final ChangeType changeType;

    public enum ChangeType {
        ADDED,
        REMOVED,
        CLEARED
    }

    public PlayerConstructListChangedEvent(Player player, Entity construct, ChangeType changeType) {
        this.playerUUID = player.getUUID();
        this.player = player;
        this.construct = construct;
        this.changeType = changeType;
    }

    public PlayerConstructListChangedEvent(UUID playerUUID, Entity construct, ChangeType changeType) {
        this.playerUUID = playerUUID;
        this.player = null;
        this.construct = construct;
        this.changeType = changeType;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getConstruct() {
        return construct;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}