package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.IEntityRestorer;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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
        if (player == null) return DroneArrayType.Types.ORBIT;
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
            if (canUseArray(player, candidate)) {
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

        player.displayClientMessage(
                Component.literal("阵列: " + newArray.getName()).withStyle(ChatFormatting.GRAY),
                true
        );
    }

    /**
     * 检查玩家是否可启用该阵列（数据驱动/覆盖层优先）。
     * 追击/列队/守卫阵列要求玩家装备的物品声明了对应阵列特殊机制集合；环绕/待机始终可用。
     */
    boolean canUseArray(Player player, DroneArrayType arrayType) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return true;
        UUID uuid = player.getUUID();
        return switch (arrayType.getId()) {
            case "pursuit" -> DefsManager.playerHasEquippedMechanic(server, uuid, "pursuit_array_required_items");
            case "formation" -> DefsManager.playerHasEquippedMechanic(server, uuid, "formation_array_required_items");
            case "guard" -> DefsManager.playerHasEquippedMechanic(server, uuid, "guard_array_required_items");
            default -> true;
        };
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



    // ===== 通用备份/恢复流程 =====

    /**
     * 备份指定类型的所有构造体到待机存储，并移除实体。
     * 只处理玩家当前维度的构造体，其他维度遗留的构造体不受影响。
     * @param player 玩家
     * @param typeId 构造体类型ID
     */
    private void backupConstructs(Player player, String typeId) {
        UUID playerUUID = player.getUUID();
        ResourceKey<Level> playerDim = player.level().dimension();
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance().getActiveConstructEntities(playerUUID, typeId);

        List<ConstructData> backupList = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (!(entity instanceof AbstractConstructEntity constructEntity) || !constructEntity.isAlive()) continue;
            // 只备份玩家当前维度的构造体
            if (entity.level() == null || !entity.level().dimension().equals(playerDim)) continue;

            ConstructData copy = constructEntity.snapshotToData();
            double currentMaxHealth = constructEntity.getMaxHealth();
            float currentHealth = constructEntity.getHealth();
            copy.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
            copy.setActive(true);
            copy.setSavedPos(entity.getX(), entity.getY(), entity.getZ());
            copy.setDimension(entity.level().dimension().location().toString());
            backupList.add(copy);
        }
        setStandbyBackup(playerUUID, typeId, backupList);

        for (Entity entity : entitiesMap.values()) {
            if (!entity.isAlive()) continue;
            if (entity.level() == null || !entity.level().dimension().equals(playerDim)) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
            UUID entityUUID = entity.getUUID();
            ConstructManager.getInstance().unregisterConstructEntity(playerUUID, typeId, entityUUID);
            ConstructManager.getInstance().removeConstruct(playerUUID, entityUUID);
        }
    }

    /**
     * 从待机备份恢复指定类型的构造体实体。
     * @return true 如果存在备份并已恢复；false 如果无备份
     */
    private boolean restoreConstructs(Player player, String typeId, DroneArrayType newArray,
                                       boolean hasAssault, boolean hasDefense) {
        UUID playerUUID = player.getUUID();
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(playerUUID);
        List<ConstructData> backupList = playerBackups != null ? playerBackups.remove(typeId) : null;

        if (backupList == null || backupList.isEmpty()) {
            return false;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        ConstructType constructType = ConstructManager.getInstance().getConstructType(typeId);
        if (constructType == null || !constructType.hasEntityRestorer()) {
            return false;
        }

        IEntityRestorer restorer = constructType.getEntityRestorer();

        for (ConstructData restoredData : backupList) {
            Entity restoredEntity = restorer.restore((ServerPlayer) player, restoredData, serverLevel);
            if (restoredEntity == null) continue;

            if (restoredEntity instanceof AbstractConstructEntity constructEntity) {
                // 无人机特有：设置阵列类型和模块
                if (constructEntity instanceof DroneConstructEntity droneEntity && restoredData instanceof DroneConstructData droneData) {
                    droneData.setArrayType(newArray);
                    droneData.setHasAssaultModule(hasAssault);
                    droneData.setHasDefenseModule(hasDefense);
                    droneEntity.setArrayType(newArray);
                    if (hasAssault) droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
                    if (hasDefense) droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
                    if (!hasAssault && !hasDefense) droneEntity.refreshConstructAttributes();
                }

                // 用保存的生命值比例恢复当前生命值
                float healthRatio = (float) restoredData.getHealthRatio();
                float newMaxHealth = constructEntity.getMaxHealth();
                constructEntity.setHealth(newMaxHealth * healthRatio);

                restoredData.setEntityUUID(constructEntity.getUUID());
                ConstructManager.getInstance().registerConstructEntity(playerUUID, typeId, constructEntity);
                ConstructManager.getInstance().addConstruct(player, restoredData);
            }
        }
        return true;
    }

    // ===== 进入/退出待机 =====

    private void enterStandby(Player player) {
        // 备份所有类型的构造体
        backupConstructs(player, DroneConstructTypes.DRONE);
        backupConstructs(player, WingmanConstructTypes.WINGMAN);
        backupConstructs(player, SwarmConstructTypes.SWARM);

        // 禁用构建
        ConstructManager.getInstance().setBuildingDisabled(player, true);
    }

    private void exitStandby(Player player, DroneArrayType newArray) {
        ConstructManager.getInstance().setBuildingDisabled(player, false);

        boolean currentHasAssault = DroneManager.getInstance().hasAssaultModule(player);
        boolean currentHasDefense = DroneManager.getInstance().hasDefenseModule(player);

        // 恢复无人机（无备份时回退到更新现有实体阵列类型）。
        // 仅当玩家仍具备对应构建能力（仍装备模块）时才恢复，否则丢弃备份，防止卸载模块后无人机重新生成
        if (DroneManager.getInstance().canBuildDroneInternal(player)) {
            if (!restoreConstructs(player, DroneConstructTypes.DRONE, newArray, currentHasAssault, currentHasDefense)) {
                updateExistingDroneArrayType(player, newArray);
            }
        } else {
            discardStandbyBackup(player, DroneConstructTypes.DRONE);
        }

        // 恢复僚机（仅当玩家仍具备构建能力时）
        if (WingmanManager.getInstance().canBuildWingmanInternal(player)) {
            restoreConstructs(player, WingmanConstructTypes.WINGMAN, newArray, currentHasAssault, currentHasDefense);
        } else {
            discardStandbyBackup(player, WingmanConstructTypes.WINGMAN);
        }

        // 恢复蜂群（仅当玩家仍具备构建能力时）
        if (SwarmManager.getInstance().canBuildSwarmInternal(player)) {
            restoreConstructs(player, SwarmConstructTypes.SWARM, newArray, currentHasAssault, currentHasDefense);
        } else {
            discardStandbyBackup(player, SwarmConstructTypes.SWARM);
        }
    }

    /**
     * 丢弃指定类型的待机备份（玩家已失去对应构建能力时不再恢复）
     */
    private void discardStandbyBackup(Player player, String typeId) {
        Map<String, List<ConstructData>> playerBackups = standbyDataBackup.get(player.getUUID());
        if (playerBackups != null) {
            playerBackups.remove(typeId);
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
