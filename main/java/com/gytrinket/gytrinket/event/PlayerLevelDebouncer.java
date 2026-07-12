package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.TickScheduler;
import com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier;
import com.gytrinket.gytrinket.core.entity.construct.swarm.MothershipManager;
import com.gytrinket.gytrinket.core.level.ModLevelChangeEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerLevelDebouncer {

    private static final int DEBOUNCE_TICKS = 5;
    private static final Map<UUID, Long> PENDING_CHANGES = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
    private static boolean registered = false;

    private PlayerLevelDebouncer() {}

    @SubscribeEvent
    public static void onModLevelChange(ModLevelChangeEvent event) {
        UUID uuid = event.getPlayerUUID();
        PENDING_CHANGES.put(uuid, TickScheduler.getCurrentTick() + DEBOUNCE_TICKS);
        DIRTY_PLAYERS.add(uuid);

        if (!registered) {
            registered = true;
            TickScheduler.register("level_debounce_check", 1, PlayerLevelDebouncer::tick);
        }
    }

    private static void tick(long currentTick) {
        if (DIRTY_PLAYERS.isEmpty()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        Set<UUID> toProcess = new java.util.HashSet<>();
        for (Map.Entry<UUID, Long> entry : PENDING_CHANGES.entrySet()) {
            if (currentTick >= entry.getValue()) {
                toProcess.add(entry.getKey());
            }
        }

        for (UUID uuid : toProcess) {
            PENDING_CHANGES.remove(uuid);
            DIRTY_PLAYERS.remove(uuid);

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                continue;
            }

            AdvancedEngineeringEventHandler.applyEngineeringBonus(player);
            PrecisionConstructEventHandler.applyPrecisionConstructBonus(player);
            MothershipManager.applyMothershipBonus(player);
            ConstructAttributeApplier.refreshForPlayer(uuid, player);
        }
    }
}
