package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.TickScheduler;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerUpdateManager {

    /** 同一 tick 内多次核心内容变化合并为一次全量重算。 */
    private static final Set<UUID> PENDING_UPDATES = ConcurrentHashMap.newKeySet();
    private static boolean debounceSchedulerRegistered = false;

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
            PENDING_UPDATES.add(player.getUUID());
            ensureDebounceScheduler();
        }
    }

    private static void ensureDebounceScheduler() {
        if (debounceSchedulerRegistered) {
            return;
        }
        debounceSchedulerRegistered = true;
        TickScheduler.register("player_update_debounce", 1, PlayerUpdateManager::processPendingUpdates);
    }

    private static void processPendingUpdates(long currentTick) {
        if (PENDING_UPDATES.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<UUID> players = new ArrayList<>(PENDING_UPDATES);
        PENDING_UPDATES.removeAll(players);
        for (UUID playerUUID : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                triggerPlayerUpdate(player);
            }
        }
    }
}
