package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 无人机管理器
 * <p>
 * 处理无人机的构建、管理逻辑和构建条件检测。
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class DroneManager {
    private static final DroneManager INSTANCE = new DroneManager();

    /** 玩家构建条件缓存：玩家UUID -> 是否可以构建无人机 */
    private static final Set<UUID> PLAYER_CAN_BUILD_DRONE = new HashSet<>();

    /** 玩家拥有的模块缓存：玩家UUID -> 是否拥有突击模块 */
    private static final Set<UUID> PLAYER_HAS_ASSAULT_MODULE = new HashSet<>();

    /** 玩家拥有的模块缓存：玩家UUID -> 是否拥有防御模块 */
    private static final Set<UUID> PLAYER_HAS_DEFENSE_MODULE = new HashSet<>();

    /** 玩家拥有的模块缓存：玩家UUID -> 是否拥有指挥官模块 */
    private static final Set<UUID> PLAYER_HAS_COMMANDER_MODULE = new HashSet<>();

    private DroneManager() {
        // 注册无人机构建条件检查器
        ConstructManager.getInstance().registerBuildConditionChecker(
                DroneConstructTypes.DRONE,
                player -> PLAYER_CAN_BUILD_DRONE.contains(player.getUUID())
        );
    }

    public static DroneManager getInstance() {
        return INSTANCE;
    }

    /**
     * 通过实体UUID获取无人机构造体
     *
     * @param entityUUID 无人机实体UUID
     * @return 对应的 DroneConstruct，如果没有找到返回 null
     */
    public DroneConstruct getConstructByEntityUUID(UUID entityUUID) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(entityUUID);
            if (entity instanceof DroneConstructEntity droneEntity) {
                return droneEntity.getDroneConstruct();
            }
        }
        return null;
    }

    /**
     * 开始构建无人机（使用玩家当前的阵列类型）
     *
     * @param player 玩家
     */
    public void startBuildingDrone(Player player) {
        ConstructType type = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
        if (type == null) {
            return;
        }
        
        DroneArrayType arrayType = DroneArrayManager.getInstance().getPlayerArrayType(player);
        DroneBuilder builder = new DroneBuilder(player, type, arrayType);
        ConstructManager.getInstance().startBuilding(player, builder);
    }

    /**
     * 检查玩家是否可以构建无人机
     *
     * @param player 玩家
     * @return 如果可以构建返回true
     */
    public boolean canBuildDrone(Player player) {
        if (!canBuildDroneInternal(player)) {
            return false;
        }
        return ConstructManager.getInstance().canCreateConstruct(player, DroneConstructTypes.DRONE)
                && !ConstructManager.getInstance().isBuilding(player, DroneConstructTypes.DRONE);
    }

    /**
     * 检查玩家是否可以构建无人机（需要拥有基础无人机构建物品）
     *
     * @param player 玩家
     * @return 是否可以构建
     */
    public boolean canBuildDroneInternal(Player player) {
        return PLAYER_CAN_BUILD_DRONE.contains(player.getUUID());
    }

    /**
     * 检查玩家是否拥有突击无人机构建物品
     *
     * @param player 玩家
     * @return 是否拥有
     */
    public boolean hasAssaultModule(Player player) {
        return PLAYER_HAS_ASSAULT_MODULE.contains(player.getUUID());
    }

    /**
     * 检查玩家是否拥有防御无人机构建物品
     *
     * @param player 玩家
     * @return 是否拥有
     */
    public boolean hasDefenseModule(Player player) {
        return PLAYER_HAS_DEFENSE_MODULE.contains(player.getUUID());
    }

    /**
     * 监听属性计算完毕事件
     * 仅在此事件触发时，检测一次玩家是否拥有无人机构建物品
     *
     * @param event 属性计算完毕事件
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            clearPlayerCache(playerUUID);
            return;
        }

        boolean hasDroneModule = false;
        boolean hasAssaultModule = false;
        boolean hasDefenseModule = false;
        boolean hasCommanderModule = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                var item = stack.getItem();

                if (Config.isDroneModuleItem(item)) {
                    hasDroneModule = true;
                }
                if (Config.isAssaultDroneModuleItem(item)) {
                    hasAssaultModule = true;
                }
                if (Config.isDefenseDroneModuleItem(item)) {
                    hasDefenseModule = true;
                }
                if (Config.isCommanderItem(item)) {
                    hasCommanderModule = true;
                }
            }
        }

        boolean canBuildBefore = PLAYER_CAN_BUILD_DRONE.contains(playerUUID);

        if (hasDroneModule) {
            PLAYER_CAN_BUILD_DRONE.add(playerUUID);
        } else {
            PLAYER_CAN_BUILD_DRONE.remove(playerUUID);
            
            // 如果玩家之前可以构建无人机（现在不能），则销毁所有已存在的无人机
            if (canBuildBefore) {
                ServerPlayer serverPlayer = event.getPlayer();
                if (serverPlayer != null) {
                    destroyAllDrones(serverPlayer);
                }
            }
        }

        if (hasAssaultModule) {
            PLAYER_HAS_ASSAULT_MODULE.add(playerUUID);
        } else {
            PLAYER_HAS_ASSAULT_MODULE.remove(playerUUID);
        }

        if (hasDefenseModule) {
            PLAYER_HAS_DEFENSE_MODULE.add(playerUUID);
        } else {
            PLAYER_HAS_DEFENSE_MODULE.remove(playerUUID);
        }

        if (hasCommanderModule) {
            PLAYER_HAS_COMMANDER_MODULE.add(playerUUID);
        } else {
            PLAYER_HAS_COMMANDER_MODULE.remove(playerUUID);
        }

        ServerPlayer playerForEffects = event.getPlayer();
        if (playerForEffects != null) {
            updateExistingDroneEffects(playerForEffects, hasAssaultModule, hasDefenseModule);
            validateCurrentArray(playerForEffects);
        }
        
        // 如果玩家现在可以构建无人机（之前不能，现在可以），则开始构建
        if (!canBuildBefore && hasDroneModule) {
            ServerPlayer serverPlayer = event.getPlayer();
            if (serverPlayer != null && DroneManager.getInstance().canBuildDrone(serverPlayer)) {
                DroneManager.getInstance().startBuildingDrone(serverPlayer);
            }
        }
    }

    /**
     * 销毁玩家的所有无人机
     *
     * @param player 玩家
     */
    private static void destroyAllDrones(Player player) {
        ConstructManager.getInstance().cancelBuilding(player, DroneConstructTypes.DRONE);

        java.util.List<com.gytrinket.gytrinket.core.entity.construct.ConstructData> constructDataList =
            ConstructManager.getInstance().getPlayerConstructsByType(player, DroneConstructTypes.DRONE);

        Map<UUID, net.minecraft.world.entity.Entity> activeEntities =
            ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), DroneConstructTypes.DRONE);

        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        if (constructDataList != null && !constructDataList.isEmpty() && server != null) {
            for (com.gytrinket.gytrinket.core.entity.construct.ConstructData data : constructDataList) {
                UUID entityUUID = data.getEntityUUID();
                if (entityUUID == null) continue;

                if (!activeEntities.containsKey(entityUUID)) {
                    for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
                        net.minecraft.world.entity.Entity entity = serverLevel.getEntity(entityUUID);
                        if (entity instanceof DroneConstructEntity drone && drone.isAlive()) {
                            drone.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    }
                }
            }
        }

        ConstructManager.getInstance().removeConstructsByType(player, DroneConstructTypes.DRONE);
    }

    /**
     * 清除玩家的缓存数据
     *
     * @param playerUUID 玩家UUID
     */
    private static void clearPlayerCache(UUID playerUUID) {
        PLAYER_CAN_BUILD_DRONE.remove(playerUUID);
        PLAYER_HAS_ASSAULT_MODULE.remove(playerUUID);
        PLAYER_HAS_DEFENSE_MODULE.remove(playerUUID);
        PLAYER_HAS_COMMANDER_MODULE.remove(playerUUID);
    }

    private static void validateCurrentArray(ServerPlayer player) {
        DroneArrayManager arrayManager = DroneArrayManager.getInstance();
        DroneArrayType currentArray = arrayManager.getPlayerArrayType(player);

        if (currentArray != null && !currentArray.hasRequiredItems(player.getUUID())) {
            arrayManager.switchToArray(player, DroneArrayType.Types.ORBIT);
        }
    }

    private static void updateExistingDroneEffects(ServerPlayer player, boolean hasAssaultModule, boolean hasDefenseModule) {
        Map<UUID, net.minecraft.world.entity.Entity> droneEntities =
                ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), DroneConstructTypes.DRONE);

        for (net.minecraft.world.entity.Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity droneEntity && droneEntity.isAlive()) {
                boolean needRefresh = false;

                if (hasAssaultModule && !droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT)) {
                    droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
                    if (droneEntity.getDroneConstruct() != null) {
                        droneEntity.getDroneConstruct().addEffect(new com.gytrinket.gytrinket.core.entity.construct.drone.effect.AssaultEffect());
                    }
                    needRefresh = true;
                }

                if (hasDefenseModule && !droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE)) {
                    droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
                    if (droneEntity.getDroneConstruct() != null) {
                        droneEntity.getDroneConstruct().addEffect(new com.gytrinket.gytrinket.core.entity.construct.drone.effect.DefenseEffect());
                    }
                    needRefresh = true;
                }

                if (!hasAssaultModule && droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT)) {
                    droneEntity.removeEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
                    if (droneEntity.getDroneConstruct() != null) {
                        droneEntity.getDroneConstruct().getEffects().removeIf(e -> e instanceof com.gytrinket.gytrinket.core.entity.construct.drone.effect.AssaultEffect);
                    }
                    needRefresh = true;
                }

                if (!hasDefenseModule && droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE)) {
                    droneEntity.removeEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
                    if (droneEntity.getDroneConstruct() != null) {
                        droneEntity.getDroneConstruct().getEffects().removeIf(e -> e instanceof com.gytrinket.gytrinket.core.entity.construct.drone.effect.DefenseEffect);
                    }
                    needRefresh = true;
                }

                if (needRefresh) {
                    droneEntity.refreshConstructAttributes();
                }
            }
        }

        DroneArrayManager.getInstance().updateStandbyBackupModules(player.getUUID(), hasAssaultModule, hasDefenseModule);
    }
}