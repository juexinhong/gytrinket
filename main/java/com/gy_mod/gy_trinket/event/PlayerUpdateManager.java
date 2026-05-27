package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerUpdateManager {

    private PlayerUpdateManager() {}

    public static void triggerPlayerUpdate(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        
        MinecraftForge.EVENT_BUS.post(new PlayerAttributesUpdateEvent.Pre(playerUUID));
        
        AttributeManager.recalculateAndCachePlayerAttributes(player);
        
        MinecraftForge.EVENT_BUS.post(new PlayerAttributesUpdateEvent.Post(playerUUID));
    }
    
    @SubscribeEvent
    public static void onLightPointStoreChanged(PlayerLightPointStoreChangedEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerUUID());
        if (player != null) {
            triggerPlayerUpdate(player);
        }
    }
}
