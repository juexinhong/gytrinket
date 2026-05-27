package com.gy_mod.gy_trinket.event;

import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

public class PlayerAttributesUpdateEvent extends Event {
    
    private final UUID playerUUID;
    
    public PlayerAttributesUpdateEvent(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }
    
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    
    public static class Pre extends PlayerAttributesUpdateEvent {
        public Pre(UUID playerUUID) {
            super(playerUUID);
        }
    }
    
    public static class Post extends PlayerAttributesUpdateEvent {
        public Post(UUID playerUUID) {
            super(playerUUID);
        }
    }
}
