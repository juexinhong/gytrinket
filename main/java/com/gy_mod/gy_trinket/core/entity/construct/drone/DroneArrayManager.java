package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无人机阵列管理器
 * <p>
 * 管理玩家无人机的阵列类型切换和持久化存储。
 */
public class DroneArrayManager {
    private static final DroneArrayManager INSTANCE = new DroneArrayManager();

    private final Map<UUID, DroneArrayType> playerArrayTypes = new ConcurrentHashMap<>();
    private final Map<UUID, List<DroneConstructData>> standbyDroneDataBackup = new ConcurrentHashMap<>();

    private static final DroneArrayType[] DRONE_ARRAY_TYPES = {
        DroneArrayType.Types.ORBIT,
        DroneArrayType.Types.PURSUIT,
        DroneArrayType.Types.FORMATION,
        DroneArrayType.Types.GUARD,
        DroneArrayType.Types.STANDBY
    };

    private DroneArrayManager() {}

    public static DroneArrayManager getInstance() {
        return INSTANCE;
    }

    public DroneArrayType getPlayerArrayType(Player player) {
        return playerArrayTypes.getOrDefault(player.getUUID(), DroneArrayType.Types.ORBIT);
    }

    public void setPlayerArrayType(Player player, DroneArrayType arrayType) {
        playerArrayTypes.put(player.getUUID(), arrayType);
    }

    public void removePlayerData(Player player) {
        playerArrayTypes.remove(player.getUUID());
        standbyDroneDataBackup.remove(player.getUUID());
    }

    public void saveToNBT(Player player, net.minecraft.nbt.CompoundTag tag) {
        DroneArrayType arrayType = playerArrayTypes.get(player.getUUID());
        if (arrayType != null) {
            tag.putString("droneArrayType", arrayType.getId());
        }
    }

    public void loadFromNBT(Player player, net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("droneArrayType")) {
            String arrayTypeId = tag.getString("droneArrayType");
            DroneArrayType arrayType = DroneArrayType.Types.fromId(arrayTypeId);
            if (arrayType != null) {
                playerArrayTypes.put(player.getUUID(), arrayType);
            }
        }
    }

    public void switchToNextArray(Player player) {
        DroneArrayType currentArray = getPlayerArrayType(player);

        int currentIndex = 0;
        for (int i = 0; i < DRONE_ARRAY_TYPES.length; i++) {
            if (DRONE_ARRAY_TYPES[i].equals(currentArray)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % DRONE_ARRAY_TYPES.length;
        int startIndex = nextIndex;
        DroneArrayType newArray = null;

        do {
            DroneArrayType candidate = DRONE_ARRAY_TYPES[nextIndex];
            if (candidate.hasRequiredItems(player.getUUID())) {
                newArray = candidate;
                break;
            }
            nextIndex = (nextIndex + 1) % DRONE_ARRAY_TYPES.length;
        } while (nextIndex != startIndex);

        if (newArray == null) {
            return;
        }

        setPlayerArrayType(player, newArray);
        updatePlayerDronesArray(player, newArray);
    }

    private void updatePlayerDronesArray(Player player, DroneArrayType newArray) {
        boolean isStandby = newArray.hasTag(DroneArrayType.Tags.STANDBY);

        if (isStandby) {
            enterStandby(player);
        } else {
            exitStandby(player, newArray);
        }
    }

    private void enterStandby(Player player) {
        UUID playerUUID = player.getUUID();

        Map<UUID, net.minecraft.world.entity.Entity> entitiesMap =
                ConstructManager.getInstance().getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

        List<DroneConstructData> backupList = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity droneEntity && droneEntity.isAlive()) {
                DroneArrayType currentArrayType = getPlayerArrayType(player);
                DroneConstructData copy = new DroneConstructData(
                        DroneConstructTypes.DRONE,
                        droneEntity.getUUID(),
                        droneEntity.getMaxHealth(),
                        currentArrayType
                );
                copy.setHealth(droneEntity.getHealth());
                copy.setActive(true);
                copy.setHasAssaultModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT));
                copy.setHasDefenseModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE));
                backupList.add(copy);
            }
        }
        standbyDroneDataBackup.put(playerUUID, backupList);

        for (net.minecraft.world.entity.Entity entity : entitiesMap.values()) {
            if (entity.isAlive()) {
                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        }
        ConstructManager.getInstance().removeConstructsByType(player, DroneConstructTypes.DRONE);
        ConstructManager.getInstance().setBuildingDisabled(player, true);
    }

    private void exitStandby(Player player, DroneArrayType newArray) {
        UUID playerUUID = player.getUUID();

        ConstructManager.getInstance().setBuildingDisabled(player, false);

        List<DroneConstructData> backupList = standbyDroneDataBackup.remove(playerUUID);

        boolean currentHasAssault = DroneManager.getInstance().hasAssaultModule(player);
        boolean currentHasDefense = DroneManager.getInstance().hasDefenseModule(player);

        if (backupList != null && !backupList.isEmpty()) {
            for (DroneConstructData restoredData : backupList) {
                restoredData.setArrayType(newArray);
                restoredData.setHasAssaultModule(currentHasAssault);
                restoredData.setHasDefenseModule(currentHasDefense);

                if (player.level() instanceof ServerLevel serverLevel) {
                    DroneConstructEntity droneEntity = new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), serverLevel);
                    droneEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
                    droneEntity.setOwnerUUID(playerUUID);
                    droneEntity.setArrayType(newArray);

                    if (currentHasAssault) {
                        droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
                    }
                    if (currentHasDefense) {
                        droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
                    }

                    droneEntity.setHealth((float) restoredData.getHealth());
                    serverLevel.addFreshEntity(droneEntity);

                    ConstructManager.getInstance().registerConstructEntity(playerUUID, DroneConstructTypes.DRONE, droneEntity);

                    restoredData.setEntityUUID(droneEntity.getUUID());
                    ConstructManager.getInstance().addConstruct(player, restoredData);
                }
            }
        } else {
            List<ConstructData> drones = ConstructManager.getInstance()
                    .getPlayerConstructsByType(player, DroneConstructTypes.DRONE);

            for (ConstructData droneData : drones) {
                if (droneData instanceof DroneConstructData extendedData) {
                    extendedData.setArrayType(newArray);
                }
            }

            Map<UUID, net.minecraft.world.entity.Entity> entitiesMap =
                    ConstructManager.getInstance().getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

            for (net.minecraft.world.entity.Entity entity : entitiesMap.values()) {
                if (entity instanceof DroneConstructEntity droneEntity) {
                    droneEntity.setArrayType(newArray);
                }
            }
        }
    }

    public void updateStandbyBackupModules(UUID playerUUID, boolean hasAssaultModule, boolean hasDefenseModule) {
        List<DroneConstructData> backupList = standbyDroneDataBackup.get(playerUUID);
        if (backupList != null) {
            for (DroneConstructData data : backupList) {
                data.setHasAssaultModule(hasAssaultModule);
                data.setHasDefenseModule(hasDefenseModule);
            }
        }
    }

    public List<DroneConstructData> getStandbyBackup(UUID playerUUID) {
        return standbyDroneDataBackup.get(playerUUID);
    }

    public void setStandbyBackup(UUID playerUUID, List<DroneConstructData> backupList) {
        standbyDroneDataBackup.put(playerUUID, backupList);
    }

    public boolean isInStandby(UUID playerUUID) {
        DroneArrayType arrayType = playerArrayTypes.get(playerUUID);
        return arrayType != null && arrayType.hasTag(DroneArrayType.Tags.STANDBY);
    }

    public void switchToArray(Player player, DroneArrayType newArray) {
        setPlayerArrayType(player, newArray);
        updatePlayerDronesArray(player, newArray);
    }

    public void syncToArrayEntities(Player player) {
        DroneArrayType arrayType = getPlayerArrayType(player);
        if (arrayType != null) {
            if (arrayType.hasTag(DroneArrayType.Tags.STANDBY)) {
                switchToArray(player, DroneArrayType.Types.ORBIT);
            } else {
                updatePlayerDronesArray(player, arrayType);
            }
        }
    }
}
