package com.gy_mod.gy_trinket.event;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
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