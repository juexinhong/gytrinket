package com.gytrinket.gytrinket.core.shield_transfer.event;

import net.minecraft.server.level.ServerPlayer;
import com.gytrinket.gytrinket.engine.bus.GyEvent;

import java.util.UUID;

public class ShieldTransferRebuiltEvent implements GyEvent {

    private final UUID playerUUID;
    private final ServerPlayer player;

    public ShieldTransferRebuiltEvent(ServerPlayer player) {
        this.playerUUID = player.getUUID();
        this.player = player;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
