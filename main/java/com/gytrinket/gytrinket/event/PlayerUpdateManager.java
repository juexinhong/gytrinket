package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerUpdateManager {

    private PlayerUpdateManager() {}

    public static void triggerPlayerUpdate(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        NeoForge.EVENT_BUS.post(new PlayerAttributesUpdateEvent.Pre(playerUUID));

        AttributeManager.recalculateAndCachePlayerAttributes(player);

        NeoForge.EVENT_BUS.post(new PlayerAttributesUpdateEvent.Post(playerUUID));
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
