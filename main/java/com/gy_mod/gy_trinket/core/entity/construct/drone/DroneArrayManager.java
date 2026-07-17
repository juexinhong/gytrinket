package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IEntityRestorer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无人机阵列管理器
 * <p>
 * 管理玩家无人机的阵列类型切换和持久化存储。
 * 统一管理无人机/僚机/蜂群的待机备份存储。
 */
public class DroneArrayManager {
    private static final DroneArrayManager INSTANCE = new DroneArrayManager();

    private final Map<UUID, DroneArrayType> playerArrayTypes = new ConcurrentHashMap<>();
    /** 统一待机备份存储：玩家UUID → (构造体类型ID → 备份数据列表) */
    private final Map<UUID, Map<String, List<ConstructData>>> standbyDataBackup = new ConcurrentHashMap<>();

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
        UUID playerUUID = player.getUUID();
        playerArrayTypes.remove(playerUUID);
        standbyDataBackup.remove(playerUUID);
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

    // ===== 统一待机备份存储 API =====

    /**
     * 获取指定玩家指定类型构造体的待机备份数据
     */
    public List<ConstructData> getStandbyBackup(UUID playerUUID, String typeId) {
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        return playerBackups != null ? playerBackups.get(typeId) : null;
    }

    /**
     * 设置指定玩家指定类型构造体的待机备份数据
     */
    public void setStandbyBackup(UUID playerUUID, String typeId, List<ConstructData> backupList) {
        standbyDataBackup.computeIfAbsent(playerUUID, k -> new HashMap<>()).put(typeId, backupList);
    }

    /**
     * 获取指定玩家所有类型构造体的待机备份数据（只读视图）
     */
    public Map<String, List<ConstructData>> getAllStandbyBackups(UUID playerUUID) {
        return standbyDataBackup.getOrDefault(playerUUID, Collections.emptyMap());
    }

    /**
     * 更新待机备份中无人机的模块状态（仅对无人机构造体数据有效）
     */
    public void updateStandbyBackupModules(UUID playerUUID, boolean hasAssaultModule, boolean hasDefenseModule) {
        List<ConstructData> backupList = getStandbyBackup(playerUUID, DroneConstructTypes.DRONE);
        if (backupList != null) {
            for (ConstructData data : backupList) {
                if (data instanceof DroneConstructData droneData) {
                    droneData.setHasAssaultModule(hasAssaultModule);
                    droneData.setHasDefenseModule(hasDefenseModule);
                }
            }
        }
    }

    // ===== 阵列切换 =====

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

    private void updatePlayerDronesArray(Player player, DroneArrayType newArray) {
        boolean isStandby = newArray.hasTag(DroneArrayType.Tags.STANDBY);

        if (isStandby) {
            enterStandby(player);
        } else {
            exitStandby(player, newArray);
        }
    }

    public boolean isInStandby(UUID playerUUID) {
        DroneArrayType arrayType = playerArrayTypes.get(playerUUID);
        return arrayType != null && arrayType.hasTag(DroneArrayType.Tags.STANDBY);
    }

    // ===== 统一备份/恢复流程 =====

    /**
     * 备份所有类型的构造体到待机存储，并移除实体。
     * <p>
     * 使用 {@link AbstractConstructEntity#snapshotToData()} 统一提取实体状态。
     */
    private void backupAllConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        ConstructManager cm = ConstructManager.getInstance();

        for (String typeId : cm.getAllConstructTypeIds()) {
            Map<UUID, Entity> entitiesMap = cm.getActiveConstructEntities(playerUUID, typeId);
            List<ConstructData> backupList = new ArrayList<>();

            for (Entity entity : entitiesMap.values()) {
                if (entity instanceof AbstractConstructEntity constructEntity && constructEntity.isAlive()) {
                    ConstructData snapshot = constructEntity.snapshotToData();
                    if (snapshot != null) {
                        backupList.add(snapshot);
                    }
                }
            }
            setStandbyBackup(playerUUID, typeId, backupList);

            // 移除实体
            for (Entity entity : entitiesMap.values()) {
                if (entity.isAlive()) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            cm.removeConstructsByType(player, typeId);
        }
    }

    /**
     * 从待机备份恢复所有类型的构造体实体。
     * <p>
     * 使用 {@link IEntityRestorer} 统一恢复实体。
     */
    private void restoreAllConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        ConstructManager cm = ConstructManager.getInstance();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);

        if (playerBackups == null || playerBackups.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        for (Map.Entry<String, List<ConstructData>> entry : playerBackups.entrySet()) {
            String typeId = entry.getKey();
            List<ConstructData> backupList = entry.getValue();

            if (backupList == null || backupList.isEmpty()) continue;

            ConstructType constructType = cm.getConstructType(typeId);
            if (constructType == null || !constructType.hasEntityRestorer()) continue;

            for (ConstructData data : backupList) {
                if (!(player instanceof ServerPlayer serverPlayer)) continue;
                Entity entity = constructType.getEntityRestorer().restore(serverPlayer, data, serverLevel);
                if (entity != null) {
                    cm.registerConstructEntity(playerUUID, typeId, entity);
                    cm.addConstruct(player, data);
                }
            }
        }

        // 清空备份数据
        playerBackups.clear();
    }

    // ===== 进入/退出待机 =====

    private void enterStandby(Player player) {
        backupAllConstructs(player);
        ConstructManager.getInstance().setBuildingDisabled(player, true);
    }

    private void exitStandby(Player player, DroneArrayType newArray) {
        ConstructManager.getInstance().setBuildingDisabled(player, false);

        // 恢复所有类型的构造体（无备份时回退到更新现有无人机阵列类型）
        if (getAllStandbyBackups(player.getUUID()).isEmpty()) {
            updateExistingDroneArrayType(player, newArray);
        } else {
            // 先更新备份数据中无人机的阵列类型和模块状态
            boolean currentHasAssault = DroneManager.getInstance().hasAssaultModule(player);
            boolean currentHasDefense = DroneManager.getInstance().hasDefenseModule(player);
            updateStandbyBackupModules(player.getUUID(), currentHasAssault, currentHasDefense);

            List<ConstructData> droneBackup = getStandbyBackup(player.getUUID(), DroneConstructTypes.DRONE);
            if (droneBackup != null) {
                for (ConstructData data : droneBackup) {
                    if (data instanceof DroneConstructData droneData) {
                        droneData.setArrayType(newArray);
                    }
                }
            }

            restoreAllConstructs(player);
        }
    }

    /**
     * 无备份时更新现有无人机构造体的阵列类型（玩家从非待机阵列切换到另一非待机阵列）
     */
    private void updateExistingDroneArrayType(Player player, DroneArrayType newArray) {
        List<ConstructData> drones = ConstructManager.getInstance()
                .getPlayerConstructsByType(player, DroneConstructTypes.DRONE);
        for (ConstructData droneData : drones) {
            if (droneData instanceof DroneConstructData extendedData) {
                extendedData.setArrayType(newArray);
            }
        }

        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance()
                .getActiveConstructEntities(player.getUUID(), DroneConstructTypes.DRONE);
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity droneEntity) {
                droneEntity.setArrayType(newArray);
            }
        }
    }
}
