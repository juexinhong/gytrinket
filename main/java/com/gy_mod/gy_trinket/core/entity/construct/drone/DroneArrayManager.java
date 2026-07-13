package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.IConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    // ===== 通用备份流程 =====

    /**
     * 备份指定类型的所有构造体到待机存储，并移除实体。
     */
    private void backupDroneConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, DroneConstructTypes.DRONE);

        List<ConstructData> backupList = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity droneEntity && droneEntity.isAlive()) {
                DroneArrayType currentArrayType = getPlayerArrayType(player);
                DroneConstructData copy = new DroneConstructData(
                        DroneConstructTypes.DRONE, droneEntity.getUUID(), droneEntity.getBaseMaxHealth(), currentArrayType);
                copy.setHasAssaultModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT));
                copy.setHasDefenseModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE));
                double currentMaxHealth = droneEntity.getMaxHealth();
                float currentHealth = droneEntity.getHealth();
                copy.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                copy.setActive(true);
                backupList.add(copy);
            }
        }
        setStandbyBackup(playerUUID, DroneConstructTypes.DRONE, backupList);

        for (Entity entity : entitiesMap.values()) {
            if (entity.isAlive()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        ConstructManager.getInstance().removeConstructsByType(player, DroneConstructTypes.DRONE);
    }

    private void backupWingmanConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, WingmanConstructTypes.WINGMAN);

        List<ConstructData> backupList = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof WingmanConstructEntity wingmanEntity && wingmanEntity.isAlive()) {
                WingmanConstructData copy = new WingmanConstructData(
                        WingmanConstructTypes.WINGMAN, wingmanEntity.getUUID(), wingmanEntity.getBaseMaxHealth());
                double currentMaxHealth = wingmanEntity.getMaxHealth();
                float currentHealth = wingmanEntity.getHealth();
                copy.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                copy.setActive(true);
                backupList.add(copy);
            }
        }
        setStandbyBackup(playerUUID, WingmanConstructTypes.WINGMAN, backupList);

        for (Entity entity : entitiesMap.values()) {
            if (entity.isAlive()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        ConstructManager.getInstance().removeConstructsByType(player, WingmanConstructTypes.WINGMAN);
    }

    private void backupSwarmConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, SwarmConstructTypes.SWARM);

        List<ConstructData> backupList = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof SwarmConstructEntity swarmEntity && swarmEntity.isAlive()) {
                SwarmConstructData copy = new SwarmConstructData(
                        SwarmConstructTypes.SWARM, swarmEntity.getUUID(), swarmEntity.getBaseMaxHealth());
                copy.setTier(swarmEntity.getTier());
                double currentMaxHealth = swarmEntity.getMaxHealth();
                float currentHealth = swarmEntity.getHealth();
                copy.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                copy.setActive(true);
                backupList.add(copy);
            }
        }
        setStandbyBackup(playerUUID, SwarmConstructTypes.SWARM, backupList);

        for (Entity entity : entitiesMap.values()) {
            if (entity.isAlive()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        ConstructManager.getInstance().removeConstructsByType(player, SwarmConstructTypes.SWARM);
    }

    // ===== 通用恢复流程 =====

    /**
     * 从待机备份恢复无人机构造体实体。
     * @return true 如果存在备份并已恢复；false 如果无备份
     */
    private boolean restoreDroneConstructs(Player player, DroneArrayType newArray, boolean hasAssault, boolean hasDefense) {
        UUID playerUUID = player.getUUID();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        List<ConstructData> backupList = playerBackups != null ? playerBackups.remove(DroneConstructTypes.DRONE) : null;

        if (backupList == null || backupList.isEmpty()) {
            return false;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        for (ConstructData restoredData : backupList) {
            if (!(restoredData instanceof DroneConstructData droneData)) continue;

            DroneConstructEntity droneEntity = new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), serverLevel);
            droneEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            droneEntity.setOwnerUUID(playerUUID);

            droneData.setArrayType(newArray);
            droneData.setHasAssaultModule(hasAssault);
            droneData.setHasDefenseModule(hasDefense);
            droneEntity.setArrayType(newArray);
            if (hasAssault) droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
            if (hasDefense) droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
            if (!hasAssault && !hasDefense) droneEntity.refreshConstructAttributes();

            // 属性修饰器应用完毕后，用保存的生命值比例恢复当前生命值
            float healthRatio = (float) restoredData.getHealthRatio();
            float newMaxHealth = droneEntity.getMaxHealth();
            droneEntity.setHealth(newMaxHealth * healthRatio);

            serverLevel.addFreshEntity(droneEntity);
            ConstructManager.getInstance().registerConstructEntity(playerUUID, DroneConstructTypes.DRONE, droneEntity);
            restoredData.setEntityUUID(droneEntity.getUUID());
            ConstructManager.getInstance().addConstruct(player, restoredData);
        }
        return true;
    }

    /**
     * 从待机备份恢复僚机构造体实体。
     */
    private void restoreWingmanConstructs(Player player, DroneArrayType newArray) {
        UUID playerUUID = player.getUUID();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        List<ConstructData> backupList = playerBackups != null ? playerBackups.remove(WingmanConstructTypes.WINGMAN) : null;

        if (backupList == null || backupList.isEmpty()) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ConstructData restoredData : backupList) {
            WingmanConstructEntity wingmanEntity = new WingmanConstructEntity(ModEntities.WINGMAN_CONSTRUCT.get(), serverLevel);
            wingmanEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            wingmanEntity.setOwnerUUID(playerUUID);
            wingmanEntity.refreshConstructAttributes();

            float healthRatio = (float) restoredData.getHealthRatio();
            float newMaxHealth = wingmanEntity.getMaxHealth();
            wingmanEntity.setHealth(newMaxHealth * healthRatio);

            serverLevel.addFreshEntity(wingmanEntity);
            ConstructManager.getInstance().registerConstructEntity(playerUUID, WingmanConstructTypes.WINGMAN, wingmanEntity);
            restoredData.setEntityUUID(wingmanEntity.getUUID());
            ConstructManager.getInstance().addConstruct(player, restoredData);
        }
    }

    /**
     * 从待机备份恢复蜂群构造体实体。
     */
    private void restoreSwarmConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        List<ConstructData> backupList = playerBackups != null ? playerBackups.remove(SwarmConstructTypes.SWARM) : null;

        if (backupList == null || backupList.isEmpty()) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ConstructData restoredData : backupList) {
            if (!(restoredData instanceof SwarmConstructData swarmData)) continue;

            SwarmConstructEntity swarmEntity = new SwarmConstructEntity(ModEntities.SWARM_CONSTRUCT.get(), serverLevel);
            swarmEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            swarmEntity.setOwnerUUID(playerUUID);
            swarmEntity.setTier(swarmData.getTier());

            float healthRatio = (float) restoredData.getHealthRatio();
            float newMaxHealth = swarmEntity.getMaxHealth();
            swarmEntity.setHealth(newMaxHealth * healthRatio);

            serverLevel.addFreshEntity(swarmEntity);
            ConstructManager.getInstance().registerConstructEntity(playerUUID, SwarmConstructTypes.SWARM, swarmEntity);
            restoredData.setEntityUUID(swarmEntity.getUUID());
            ConstructManager.getInstance().addConstruct(player, restoredData);
        }
    }

    // ===== 进入/退出待机 =====

    private void enterStandby(Player player) {
        // 备份所有类型的构造体
        backupDroneConstructs(player);
        backupWingmanConstructs(player);
        backupSwarmConstructs(player);

        // 禁用构建
        ConstructManager.getInstance().setBuildingDisabled(player, true);
    }

    private void exitStandby(Player player, DroneArrayType newArray) {
        ConstructManager.getInstance().setBuildingDisabled(player, false);

        boolean currentHasAssault = DroneManager.getInstance().hasAssaultModule(player);
        boolean currentHasDefense = DroneManager.getInstance().hasDefenseModule(player);

        // 恢复无人机（无备份时回退到更新现有实体阵列类型）
        if (!restoreDroneConstructs(player, newArray, currentHasAssault, currentHasDefense)) {
            updateExistingDroneArrayType(player, newArray);
        }

        // 恢复僚机
        restoreWingmanConstructs(player, newArray);

        // 恢复蜂群
        restoreSwarmConstructs(player);
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
