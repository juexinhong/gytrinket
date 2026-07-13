package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.MothershipManager;
import com.gy_mod.gy_trinket.core.level.ModLevelChangeEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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
