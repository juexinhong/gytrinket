package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无人机管理器
 * <p>
 * 处理无人机的构建、管理逻辑和构建条件检测。
 * <p>
 * 实例化机制：每个无人机实例来源物品（声明 {@link DroneInstanceParams#MECHANIC_SET}
 * 机制集或命中 Config 无人机模块物品）各成一个实例，独立构建、独立数量上限，
 * 实例参数（构建时间/生命/伤害/数量/攻击间隔/移速）为物品级。
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class DroneManager {
    private static final DroneManager INSTANCE = new DroneManager();

    /** 玩家构建条件缓存：玩家UUID -> 是否可以构建无人机 */
    private static final Set<UUID> PLAYER_CAN_BUILD_DRONE = new HashSet<>();

    /** 玩家无人机实例物品缓存：玩家UUID -> 实例物品ID集合（实例键） */
    private static final Map<UUID, Set<String>> PLAYER_DRONE_INSTANCE_ITEMS = new ConcurrentHashMap<>();

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

        // 注册实例基础数量提供器：无人机实例数量上限 = 物品级 base_count 参数（+属性修正）
        ConstructManager.getInstance().registerInstanceBaseCountProvider(
                DroneConstructTypes.DRONE,
                (player, instanceKey) -> DroneInstanceParams.getBaseCount(
                        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), instanceKey)
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
        
        ConstructBuilder builder = new ConstructBuilder(player, type);
        ConstructManager.getInstance().startBuilding(player, builder);
    }

    /**
     * 每刻驱动各无人机实例的构建循环
     * <p>
     * 对玩家的每个实例物品：若该实例未在构建，则尝试启动实例构建器
     * （构建时间与产出参数为物品级；数量上限检查在 startBuildingStorage 内完成）。
     *
     * @param player 玩家
     */
    public void tickBuilds(Player player) {
        Set<String> instanceItems = PLAYER_DRONE_INSTANCE_ITEMS.get(player.getUUID());
        if (instanceItems == null || instanceItems.isEmpty()) {
            return;
        }
        ConstructType type = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
        if (type == null) {
            return;
        }
        ConstructManager constructManager = ConstructManager.getInstance();
        for (String instanceKey : instanceItems) {
            String storageKey = ConstructManager.storageKey(DroneConstructTypes.DRONE, instanceKey);
            if (constructManager.isBuildingStorage(player, storageKey)) {
                continue;
            }
            constructManager.startBuildingStorage(
                    player, new DroneInstanceBuilder(player, type, instanceKey), storageKey);
        }
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
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        boolean hasDroneModule = false;
        boolean hasAssaultModule = false;
        boolean hasDefenseModule = false;
        boolean hasCommanderModule = false;

        Set<String> instanceItems = new HashSet<>();

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            var item = stack.getItem();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();

            // 实例化机制：命中 Config 无人机模块物品、或声明 drone_module_items 机制集的
            // 物品各成一个实例（并集）；实例键为物品 ID
            if (Config.isDroneModuleItem(item)
                    || (server != null && DefsManager.getEffectiveSpecialMechanicSets(server, itemId)
                            .contains(DroneInstanceParams.MECHANIC_SET))) {
                hasDroneModule = true;
                instanceItems.add(itemId);
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

        // 实例化机制：实例物品集合 diff——被移除的实例取消构建并销毁其全部无人机
        Set<String> previousItems = PLAYER_DRONE_INSTANCE_ITEMS.getOrDefault(playerUUID, java.util.Collections.emptySet());
        ServerPlayer playerForDiff = event.getPlayer();
        for (String removedItem : previousItems) {
            if (instanceItems.contains(removedItem) || playerForDiff == null) continue;
            String storageKey = ConstructManager.storageKey(DroneConstructTypes.DRONE, removedItem);
            ConstructManager.getInstance().cancelBuildingStorage(playerForDiff, storageKey);
            ConstructManager.getInstance().removeConstructsByInstance(playerForDiff, DroneConstructTypes.DRONE, removedItem);
        }
        PLAYER_DRONE_INSTANCE_ITEMS.put(playerUUID, instanceItems);

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

            // 驱动各实例的构建循环（新增实例立即开始构建；每刻循环由 TickScheduler 兜底）
            DroneManager.getInstance().tickBuilds(playerForEffects);
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
        // 阵列按对应特殊机制检查：玩家不再装备声明该阵列特殊机制（覆盖层优先）的物品时切回环绕
        DroneArrayManager arrayManager = DroneArrayManager.getInstance();
        DroneArrayType currentArray = arrayManager.getPlayerArrayType(player);
        if (currentArray != null && !arrayManager.canUseArray(player, currentArray)) {
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