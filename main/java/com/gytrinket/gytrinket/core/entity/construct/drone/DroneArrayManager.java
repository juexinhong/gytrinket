package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructData;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructData;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import net.minecraft.server.level.ServerLevel;
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

    // ===== 备份/恢复函数式接口 =====

    @FunctionalInterface
    private interface DataBackupper {
        ConstructData createBackup(AbstractConstructEntity entity, Player player);
    }

    @FunctionalInterface
    private interface EntityRestorer {
        void restore(AbstractConstructEntity entity, ConstructData data, DroneArrayType newArray, boolean hasAssault, boolean hasDefense);
    }

    // 各类型构造体的备份/恢复策略
    private static final DataBackupper DRONE_BACKUPPER = (entity, player) -> {
        DroneConstructEntity drone = (DroneConstructEntity) entity;
        DroneArrayType currentArrayType = getInstance().getPlayerArrayType(player);
        DroneConstructData copy = new DroneConstructData(
                DroneConstructTypes.DRONE, drone.getUUID(), drone.getBaseMaxHealth(), currentArrayType);
        copy.setHasAssaultModule(drone.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT));
        copy.setHasDefenseModule(drone.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE));
        return copy;
    };

    private static final DataBackupper WINGMAN_BACKUPPER = (entity, player) -> {
        WingmanConstructEntity wingman = (WingmanConstructEntity) entity;
        return new WingmanConstructData(WingmanConstructTypes.WINGMAN, wingman.getUUID(), wingman.getBaseMaxHealth());
    };

    private static final DataBackupper SWARM_BACKUPPER = (entity, player) -> {
        SwarmConstructEntity swarm = (SwarmConstructEntity) entity;
        SwarmConstructData copy = new SwarmConstructData(SwarmConstructTypes.SWARM, swarm.getUUID(), swarm.getBaseMaxHealth());
        copy.setTier(swarm.getTier());
        return copy;
    };

    private static final EntityRestorer DRONE_RESTORER = (entity, data, newArray, hasAssault, hasDefense) -> {
        DroneConstructEntity drone = (DroneConstructEntity) entity;
        DroneConstructData droneData = (DroneConstructData) data;
        droneData.setArrayType(newArray);
        droneData.setHasAssaultModule(hasAssault);
        droneData.setHasDefenseModule(hasDefense);
        drone.setArrayType(newArray);
        if (hasAssault) drone.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
        if (hasDefense) drone.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
        if (!hasAssault && !hasDefense) drone.refreshConstructAttributes();
    };

    private static final EntityRestorer WINGMAN_RESTORER = (entity, data, newArray, hasAssault, hasDefense) -> {
        ((WingmanConstructEntity) entity).refreshConstructAttributes();
    };

    private static final EntityRestorer SWARM_RESTORER = (entity, data, newArray, hasAssault, hasDefense) -> {
        SwarmConstructEntity swarm = (SwarmConstructEntity) entity;
        SwarmConstructData swarmData = (SwarmConstructData) data;
        swarm.setTier(swarmData.getTier());
    };

    // ===== 通用备份/恢复流程 =====

    /**
     * 备份指定类型的所有构造体到待机存储，并移除实体。
     * @param player 玩家
     * @param typeId 构造体类型ID
     * @param backupper 备份数据创建函数
     */
    private void backupConstructs(Player player, String typeId, DataBackupper backupper) {
        UUID playerUUID = player.getUUID();
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, typeId);

        List<ConstructData> backupList = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof AbstractConstructEntity constructEntity && constructEntity.isAlive()) {
                ConstructData copy = backupper.createBackup(constructEntity, player);
                double currentMaxHealth = constructEntity.getMaxHealth();
                float currentHealth = constructEntity.getHealth();
                copy.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                copy.setActive(true);
                backupList.add(copy);
            }
        }
        setStandbyBackup(playerUUID, typeId, backupList);

        for (Entity entity : entitiesMap.values()) {
            if (entity.isAlive()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        ConstructManager.getInstance().removeConstructsByType(player, typeId);
    }

    /**
     * 从待机备份恢复指定类型的构造体实体。
     * @return true 如果存在备份并已恢复；false 如果无备份
     */
    private boolean restoreConstructs(Player player, String typeId, DroneArrayType newArray,
                                       boolean hasAssault, boolean hasDefense, EntityRestorer restorer) {
        UUID playerUUID = player.getUUID();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        List<ConstructData> backupList = playerBackups != null ? playerBackups.remove(typeId) : null;

        if (backupList == null || backupList.isEmpty()) {
            return false;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        for (ConstructData restoredData : backupList) {
            AbstractConstructEntity entity = createConstructEntity(serverLevel, typeId);
            if (entity == null) continue;

            entity.setPos(player.getX(), player.getY() + 1, player.getZ());
            entity.setOwnerUUID(playerUUID);
            restorer.restore(entity, restoredData, newArray, hasAssault, hasDefense);

            // 属性修饰器应用完毕后，用保存的生命值比例恢复当前生命值
            float healthRatio = (float) restoredData.getHealthRatio();
            float newMaxHealth = entity.getMaxHealth();
            entity.setHealth(newMaxHealth * healthRatio);

            serverLevel.addFreshEntity(entity);
            ConstructManager.getInstance().registerConstructEntity(playerUUID, typeId, entity);
            restoredData.setEntityUUID(entity.getUUID());
            ConstructManager.getInstance().addConstruct(player, restoredData);
        }
        return true;
    }

    /**
     * 创建指定类型的构造体实体
     */
    private AbstractConstructEntity createConstructEntity(ServerLevel serverLevel, String typeId) {
        return switch (typeId) {
            case DroneConstructTypes.DRONE -> new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), serverLevel);
            case WingmanConstructTypes.WINGMAN -> new WingmanConstructEntity(ModEntities.WINGMAN_CONSTRUCT.get(), serverLevel);
            case SwarmConstructTypes.SWARM -> new SwarmConstructEntity(ModEntities.SWARM_CONSTRUCT.get(), serverLevel);
            default -> null;
        };
    }

    // ===== 进入/退出待机 =====

    private void enterStandby(Player player) {
        // 备份所有类型的构造体
        backupConstructs(player, DroneConstructTypes.DRONE, DRONE_BACKUPPER);
        backupConstructs(player, WingmanConstructTypes.WINGMAN, WINGMAN_BACKUPPER);
        backupConstructs(player, SwarmConstructTypes.SWARM, SWARM_BACKUPPER);

        // 禁用构建
        ConstructManager.getInstance().setBuildingDisabled(player, true);
    }

    private void exitStandby(Player player, DroneArrayType newArray) {
        ConstructManager.getInstance().setBuildingDisabled(player, false);

        boolean currentHasAssault = DroneManager.getInstance().hasAssaultModule(player);
        boolean currentHasDefense = DroneManager.getInstance().hasDefenseModule(player);

        // 恢复无人机（无备份时回退到更新现有实体阵列类型）
        if (!restoreConstructs(player, DroneConstructTypes.DRONE, newArray, currentHasAssault, currentHasDefense, DRONE_RESTORER)) {
            updateExistingDroneArrayType(player, newArray);
        }

        // 恢复僚机
        restoreConstructs(player, WingmanConstructTypes.WINGMAN, newArray, currentHasAssault, currentHasDefense, WINGMAN_RESTORER);

        // 恢复蜂群
        restoreConstructs(player, SwarmConstructTypes.SWARM, newArray, currentHasAssault, currentHasDefense, SWARM_RESTORER);
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
