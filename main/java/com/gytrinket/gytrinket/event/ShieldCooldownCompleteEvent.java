package com.gytrinket.gytrinket.event;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

public class ShieldCooldownCompleteEvent extends PlayerEvent {
    private final UUID playerUUID;

    public ShieldCooldownCompleteEvent(UUID playerUUID) {
        super(null);
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }
}
