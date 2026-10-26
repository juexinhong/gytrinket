package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.engine.bus.GyEvent;

import java.util.UUID;

public class ShieldCooldownCompleteEvent implements GyEvent {
    private final UUID playerUUID;

    public ShieldCooldownCompleteEvent(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }
}
