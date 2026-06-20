package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class CommanderManager {

    private static final CommanderManager INSTANCE = new CommanderManager();

    private final Map<UUID, Integer> playerAppointTimers = new HashMap<>();

    private CommanderManager() {}

    public static CommanderManager getInstance() {
        return INSTANCE;
    }

    public void tick(Player player) {
        if (player.level().isClientSide) return;

        UUID playerUUID = player.getUUID();

        if (!hasRequiredItems(playerUUID)) {
            removeAllCommanders(playerUUID);
            playerAppointTimers.remove(playerUUID);
            return;
        }

        Map<UUID, Entity> droneEntities =
                ConstructManager.getInstance().getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

        int commanderCount = countCommanders(droneEntities);
        int maxCount = Config.COMMANDER_MAX_COUNT.get();

        if (commanderCount >= maxCount) {
            playerAppointTimers.remove(playerUUID);
            return;
        }

        int timer = playerAppointTimers.getOrDefault(playerUUID, 0);
        timer++;
        int appointDelay = Config.COMMANDER_APPOINT_DELAY.get();

        if (timer >= appointDelay) {
            appointCommander(droneEntities, maxCount);
            playerAppointTimers.remove(playerUUID);
        } else {
            playerAppointTimers.put(playerUUID, timer);
        }
    }

    private int countCommanders(Map<UUID, Entity> droneEntities) {
        int count = 0;
        for (Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity drone && drone.isAlive() && drone.isCommanderDrone()) {
                count++;
            }
        }
        return count;
    }

    private void appointCommander(Map<UUID, Entity> droneEntities, int maxCount) {
        if (countCommanders(droneEntities) >= maxCount) return;

        List<DroneConstructEntity> candidates = new ArrayList<>();

        for (Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity drone && drone.isAlive() && !drone.isCommanderDrone() && !drone.isExploding()) {
                candidates.add(drone);
            }
        }

        if (candidates.isEmpty()) return;

        float maxHealth = -1;
        List<DroneConstructEntity> bestCandidates = new ArrayList<>();

        for (DroneConstructEntity drone : candidates) {
            float health = drone.getHealth();
            if (health > maxHealth) {
                maxHealth = health;
                bestCandidates.clear();
                bestCandidates.add(drone);
            } else if (health == maxHealth) {
                bestCandidates.add(drone);
            }
        }

        DroneConstructEntity chosen = bestCandidates.get(
                bestCandidates.size() == 1 ? 0 : new Random().nextInt(bestCandidates.size()));

        chosen.addEffectTag(DroneConstructEntity.DroneEffectTag.COMMANDER);
        if (chosen.getDroneConstruct() != null) {
            chosen.getDroneConstruct().setCommander(true);
        }
    }

    public void removeAllCommanders(UUID playerUUID) {
        Map<UUID, Entity> droneEntities =
                ConstructManager.getInstance().getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

        for (Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity drone && drone.isAlive() && drone.isCommanderDrone()) {
                drone.removeEffectTag(DroneConstructEntity.DroneEffectTag.COMMANDER);
                if (drone.getDroneConstruct() != null) {
                    drone.getDroneConstruct().setCommander(false);
                }
            }
        }
    }

    public void onDroneRemoved(DroneConstructEntity drone) {
        if (!drone.isCommanderDrone()) return;

        Entity owner = drone.getOwner();
        if (owner instanceof Player player) {
            playerAppointTimers.remove(player.getUUID());
        }
    }

    private boolean hasRequiredItems(UUID playerUUID) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) return false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack) && Config.isCommanderItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
