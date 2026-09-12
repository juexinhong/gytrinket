package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
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
 * 僚机管理器
 * <p>
 * 处理僚机的构建条件检测和管理逻辑。
 * <p>
 * 实例化机制：每个僚机实例来源物品（声明 {@link WingmanInstanceParams#MECHANIC_SET}
 * 机制集或命中 Config 僚机模块物品）各成一个实例，独立构建、独立数量上限，
 * 实例参数（构建时间/生命/伤害/数量/攻击间隔/范围）为物品级。
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class WingmanManager {
    private static final WingmanManager INSTANCE = new WingmanManager();

    /** 玩家构建条件缓存 */
    private static final Set<UUID> PLAYER_CAN_BUILD_WINGMAN = new HashSet<>();

    /** 玩家僚机实例物品缓存：玩家UUID -> 实例物品ID集合（实例键） */
    private static final Map<UUID, Set<String>> PLAYER_WINGMAN_INSTANCE_ITEMS = new ConcurrentHashMap<>();

    /** 玩家拦截机模块缓存 */
    private static final Set<UUID> PLAYER_HAS_INTERCEPTOR_MODULE = new HashSet<>();

    private WingmanManager() {
        ConstructManager.getInstance().registerBuildConditionChecker(
                WingmanConstructTypes.WINGMAN,
                player -> PLAYER_CAN_BUILD_WINGMAN.contains(player.getUUID())
        );

        // 注册实例基础数量提供器：僚机实例数量上限 = 物品级 base_count 参数（+属性修正）
        ConstructManager.getInstance().registerInstanceBaseCountProvider(
                WingmanConstructTypes.WINGMAN,
                (player, instanceKey) -> WingmanInstanceParams.getBaseCount(
                        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), instanceKey)
        );
    }

    public static WingmanManager getInstance() {
        return INSTANCE;
    }

    /**
     * 开始构建僚机（驱动各实例的构建循环）
     */
    public void startBuildingWingman(Player player) {
        tickBuilds(player);
    }

    /**
     * 每刻驱动各僚机实例的构建循环
     * <p>
     * 对玩家的每个实例物品：若该实例未在构建，则尝试启动实例构建器
     * （构建时间与产出参数为物品级；数量上限检查在 startBuildingStorage 内完成）。
     *
     * @param player 玩家
     */
    public void tickBuilds(Player player) {
        Set<String> instanceItems = PLAYER_WINGMAN_INSTANCE_ITEMS.get(player.getUUID());
        if (instanceItems == null || instanceItems.isEmpty()) {
            return;
        }
        ConstructType type = ConstructManager.getInstance().getConstructType(WingmanConstructTypes.WINGMAN);
        if (type == null) {
            return;
        }
        ConstructManager constructManager = ConstructManager.getInstance();
        for (String instanceKey : instanceItems) {
            String storageKey = ConstructManager.storageKey(WingmanConstructTypes.WINGMAN, instanceKey);
            if (constructManager.isBuildingStorage(player, storageKey)) {
                continue;
            }
            constructManager.startBuildingStorage(
                    player, new WingmanInstanceBuilder(player, type, instanceKey), storageKey);
        }
    }

    /**
     * 检查玩家是否满足僚机构建前置条件（拥有僚机模块）
     */
    public boolean canBuildWingmanInternal(Player player) {
        return PLAYER_CAN_BUILD_WINGMAN.contains(player.getUUID());
    }

    /**
     * 检查玩家是否可以构建僚机
     */
    public boolean canBuildWingman(Player player) {
        if (!canBuildWingmanInternal(player)) {
            return false;
        }
        return ConstructManager.getInstance().canCreateConstruct(player, WingmanConstructTypes.WINGMAN)
                && !ConstructManager.getInstance().isBuilding(player, WingmanConstructTypes.WINGMAN);
    }

    /**
     * 监听属性计算完毕事件，检测玩家是否拥有僚机构建物品
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        boolean hasInterceptorModule = false;
        Set<String> instanceItems = new HashSet<>();

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            var item = stack.getItem();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();

            // 实例化机制：命中 Config 僚机模块物品、或声明 wingman_module_items 机制集的
            // 物品各成一个实例（并集）；实例键为物品 ID
            if (Config.isWingmanModuleItem(item)
                    || (server != null && DefsManager.getEffectiveSpecialMechanicSets(server, itemId)
                            .contains(WingmanInstanceParams.MECHANIC_SET))) {
                instanceItems.add(itemId);
            }
            if (Config.isInterceptorModuleItem(item)) {
                hasInterceptorModule = true;
            }
        }

        boolean hasWingmanModule = !instanceItems.isEmpty();

        // 更新拦截机模块缓存
        if (hasInterceptorModule) {
            PLAYER_HAS_INTERCEPTOR_MODULE.add(playerUUID);
        } else {
            PLAYER_HAS_INTERCEPTOR_MODULE.remove(playerUUID);
        }

        // 刷新所有活跃僚机实体的拦截机模式
        ServerPlayer playerForEffects = event.getPlayer();
        if (playerForEffects != null) {
            updateExistingWingmanInterceptorMode(playerForEffects, hasInterceptorModule);
        }

        boolean canBuildBefore = PLAYER_CAN_BUILD_WINGMAN.contains(playerUUID);

        if (hasWingmanModule) {
            PLAYER_CAN_BUILD_WINGMAN.add(playerUUID);
        } else {
            PLAYER_CAN_BUILD_WINGMAN.remove(playerUUID);

            // 如果玩家之前可以构建僚机（现在不能），则销毁所有已存在的僚机
            if (canBuildBefore) {
                ServerPlayer serverPlayer = event.getPlayer();
                if (serverPlayer != null) {
                    destroyAllWingmen(serverPlayer);
                }
            }
        }

        // 实例化机制：实例物品集合 diff——被移除的实例取消构建并销毁其全部僚机
        Set<String> previousItems = PLAYER_WINGMAN_INSTANCE_ITEMS.getOrDefault(playerUUID, java.util.Collections.emptySet());
        ServerPlayer playerForDiff = event.getPlayer();
        for (String removedItem : previousItems) {
            if (instanceItems.contains(removedItem) || playerForDiff == null) continue;
            String storageKey = ConstructManager.storageKey(WingmanConstructTypes.WINGMAN, removedItem);
            ConstructManager.getInstance().cancelBuildingStorage(playerForDiff, storageKey);
            ConstructManager.getInstance().removeConstructsByInstance(playerForDiff, WingmanConstructTypes.WINGMAN, removedItem);
        }
        PLAYER_WINGMAN_INSTANCE_ITEMS.put(playerUUID, instanceItems);

        // 如果玩家现在可以构建僚机（之前不能），则驱动构建循环补满
        if (!canBuildBefore && hasWingmanModule) {
            ServerPlayer serverPlayer = event.getPlayer();
            if (serverPlayer != null) {
                WingmanManager.getInstance().tickBuilds(serverPlayer);
            }
        }
    }

    /**
     * 销毁玩家的所有僚机
     */
    private static void destroyAllWingmen(Player player) {
        ConstructManager.getInstance().cancelBuilding(player, WingmanConstructTypes.WINGMAN);

        java.util.List<com.gytrinket.gytrinket.core.entity.construct.ConstructData> constructDataList =
            ConstructManager.getInstance().getPlayerConstructsByType(player, WingmanConstructTypes.WINGMAN);

        Map<UUID, net.minecraft.world.entity.Entity> activeEntities =
            ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);

        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        if (constructDataList != null && !constructDataList.isEmpty() && server != null) {
            for (com.gytrinket.gytrinket.core.entity.construct.ConstructData data : constructDataList) {
                UUID entityUUID = data.getEntityUUID();
                if (entityUUID == null) continue;

                if (!activeEntities.containsKey(entityUUID)) {
                    for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
                        net.minecraft.world.entity.Entity entity = serverLevel.getEntity(entityUUID);
                        if (entity instanceof WingmanConstructEntity wingman && wingman.isAlive()) {
                            wingman.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    }
                }
            }
        }

        ConstructManager.getInstance().removeConstructsByType(player, WingmanConstructTypes.WINGMAN);
    }

    private static void clearPlayerCache(UUID playerUUID) {
        PLAYER_CAN_BUILD_WINGMAN.remove(playerUUID);
        PLAYER_WINGMAN_INSTANCE_ITEMS.remove(playerUUID);
        PLAYER_HAS_INTERCEPTOR_MODULE.remove(playerUUID);
    }

    /**
     * 刷新所有活跃僚机实体的拦截机数据（事件驱动，与DroneManager.updateExistingDroneEffects对齐）
     */
    private static void updateExistingWingmanInterceptorMode(ServerPlayer player, boolean hasInterceptorModule) {
        Map<UUID, net.minecraft.world.entity.Entity> wingmanEntities =
                ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);

        for (net.minecraft.world.entity.Entity entity : wingmanEntities.values()) {
            if (entity instanceof WingmanConstructEntity wingmanEntity && wingmanEntity.isAlive()) {
                wingmanEntity.refreshInterceptorData();
            }
        }
    }

    /**
     * 检查玩家是否拥有拦截机模块（缓存查询）
     */
    public boolean hasInterceptorModule(Player player) {
        return PLAYER_HAS_INTERCEPTOR_MODULE.contains(player.getUUID());
    }
}
