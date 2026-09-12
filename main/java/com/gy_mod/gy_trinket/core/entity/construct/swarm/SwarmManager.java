package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蜂群管理器
 * <p>
 * 处理蜂群的构建条件检测和管理逻辑。
 * 玩家光点核心中需包含指定蜂群模块物品（母舰机身）才能构建。
 * 护盾相关行为（修复模式/破裂增益）由实体每刻轮询护盾状态实现，无需在此挂钩事件。
 * <p>
 * 实例化机制：每个蜂群实例来源物品（声明 {@link SwarmInstanceParams#MECHANIC_SET}
 * 机制集或命中 Config 蜂群模块物品）各成一个实例，独立构建、独立数量上限，
 * 实例参数（构建时间/生命/伤害/数量/攻击间隔/范围/移速）为物品级。
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class SwarmManager {
    private static final SwarmManager INSTANCE = new SwarmManager();

    /** 玩家构建条件缓存 */
    private static final Set<UUID> PLAYER_CAN_BUILD_SWARM = new HashSet<>();

    /** 玩家蜂群实例物品缓存：玩家UUID -> 实例物品ID集合（实例键） */
    private static final Map<UUID, Set<String>> PLAYER_SWARM_INSTANCE_ITEMS = new ConcurrentHashMap<>();

    private SwarmManager() {
        ConstructManager.getInstance().registerBuildConditionChecker(
                SwarmConstructTypes.SWARM,
                player -> PLAYER_CAN_BUILD_SWARM.contains(player.getUUID())
        );

        // 注册实例基础数量提供器：蜂群实例数量上限 = 物品级 base_count 参数（+属性修正）
        ConstructManager.getInstance().registerInstanceBaseCountProvider(
                SwarmConstructTypes.SWARM,
                (player, instanceKey) -> SwarmInstanceParams.getBaseCount(
                        ServerLifecycleHooks.getCurrentServer(), instanceKey)
        );
    }

    public static SwarmManager getInstance() {
        return INSTANCE;
    }

    /**
     * 开始构建蜂群（驱动各实例的构建循环）
     */
    public void startBuildingSwarm(Player player) {
        tickBuilds(player);
    }

    /**
     * 每刻驱动各蜂群实例的构建循环
     * <p>
     * 对玩家的每个实例物品：若该实例未在构建，则尝试启动实例构建器
     * （构建时间与产出参数为物品级；数量上限检查在 startBuildingStorage 内完成）。
     *
     * @param player 玩家
     */
    public void tickBuilds(Player player) {
        Set<String> instanceItems = PLAYER_SWARM_INSTANCE_ITEMS.get(player.getUUID());
        if (instanceItems == null || instanceItems.isEmpty()) {
            return;
        }
        ConstructType type = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
        if (type == null) {
            return;
        }
        ConstructManager constructManager = ConstructManager.getInstance();
        for (String instanceKey : instanceItems) {
            String storageKey = ConstructManager.storageKey(SwarmConstructTypes.SWARM, instanceKey);
            if (constructManager.isBuildingStorage(player, storageKey)) {
                continue;
            }
            constructManager.startBuildingStorage(
                    player, new SwarmInstanceBuilder(player, type, instanceKey), storageKey);
        }
    }

    /**
     * 检查玩家是否满足蜂群构建前置条件（拥有蜂群模块）
     */
    public boolean canBuildSwarmInternal(Player player) {
        return PLAYER_CAN_BUILD_SWARM.contains(player.getUUID());
    }

    /**
     * 检查玩家是否满足蜂群构建前置条件（拥有母舰机身物品）
     * <p>
     * UUID 重载，供仅有 UUID 的逻辑使用（如数量上限计算）。
     */
    public boolean canBuildSwarmInternal(UUID playerUUID) {
        return PLAYER_CAN_BUILD_SWARM.contains(playerUUID);
    }

    /**
     * 检查玩家是否可以构建蜂群
     */
    public boolean canBuildSwarm(Player player) {
        if (!canBuildSwarmInternal(player)) {
            return false;
        }
        return ConstructManager.getInstance().canCreateConstruct(player, SwarmConstructTypes.SWARM)
                && !ConstructManager.getInstance().isBuilding(player, SwarmConstructTypes.SWARM);
    }

    /**
     * 监听属性计算完毕事件，检测玩家是否拥有蜂群构建物品
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        Set<String> instanceItems = new HashSet<>();

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            var item = stack.getItem();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();

            // 实例化机制：命中 Config 蜂群模块物品、或声明 swarm_module_items 机制集的
            // 物品各成一个实例（并集）；实例键为物品 ID
            if (Config.isSwarmModuleItem(item)
                    || (server != null && DefsManager.getEffectiveSpecialMechanicSets(itemId)
                            .contains(SwarmInstanceParams.MECHANIC_SET))) {
                instanceItems.add(itemId);
            }
        }

        boolean hasSwarmModule = !instanceItems.isEmpty();
        boolean canBuildBefore = PLAYER_CAN_BUILD_SWARM.contains(playerUUID);

        if (hasSwarmModule) {
            PLAYER_CAN_BUILD_SWARM.add(playerUUID);
        } else {
            PLAYER_CAN_BUILD_SWARM.remove(playerUUID);

            // 如果玩家之前可以构建蜂群（现在不能），则销毁所有已存在的蜂群
            if (canBuildBefore) {
                ServerPlayer serverPlayer = event.getPlayer();
                if (serverPlayer != null) {
                    destroyAllSwarms(serverPlayer);
                }
            }
        }

        // 实例化机制：实例物品集合 diff——被移除的实例取消构建并销毁其全部蜂群
        Set<String> previousItems = PLAYER_SWARM_INSTANCE_ITEMS.getOrDefault(playerUUID, java.util.Collections.emptySet());
        ServerPlayer playerForDiff = event.getPlayer();
        for (String removedItem : previousItems) {
            if (instanceItems.contains(removedItem) || playerForDiff == null) continue;
            String storageKey = ConstructManager.storageKey(SwarmConstructTypes.SWARM, removedItem);
            ConstructManager.getInstance().cancelBuildingStorage(playerForDiff, storageKey);
            ConstructManager.getInstance().removeConstructsByInstance(playerForDiff, SwarmConstructTypes.SWARM, removedItem);
        }
        PLAYER_SWARM_INSTANCE_ITEMS.put(playerUUID, instanceItems);

        // 如果玩家现在可以构建蜂群（之前不能），则驱动构建循环补满
        if (!canBuildBefore && hasSwarmModule) {
            ServerPlayer serverPlayer = event.getPlayer();
            if (serverPlayer != null) {
                SwarmManager.getInstance().tickBuilds(serverPlayer);
            }
        }
    }

    /**
     * 销毁玩家的所有蜂群
     */
    private static void destroyAllSwarms(Player player) {
        ConstructManager.getInstance().cancelBuilding(player, SwarmConstructTypes.SWARM);

        java.util.List<com.gy_mod.gy_trinket.core.entity.construct.ConstructData> constructDataList =
            ConstructManager.getInstance().getPlayerConstructsByType(player, SwarmConstructTypes.SWARM);

        Map<UUID, net.minecraft.world.entity.Entity> activeEntities =
            ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), SwarmConstructTypes.SWARM);

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (constructDataList != null && !constructDataList.isEmpty() && server != null) {
            for (com.gy_mod.gy_trinket.core.entity.construct.ConstructData data : constructDataList) {
                UUID entityUUID = data.getEntityUUID();
                if (entityUUID == null) continue;

                if (!activeEntities.containsKey(entityUUID)) {
                    for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
                        net.minecraft.world.entity.Entity entity = serverLevel.getEntity(entityUUID);
                        if (entity instanceof SwarmConstructEntity swarm && swarm.isAlive()) {
                            swarm.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    }
                }
            }
        }

        ConstructManager.getInstance().removeConstructsByType(player, SwarmConstructTypes.SWARM);
    }

    private static void clearPlayerCache(UUID playerUUID) {
        PLAYER_CAN_BUILD_SWARM.remove(playerUUID);
        PLAYER_SWARM_INSTANCE_ITEMS.remove(playerUUID);
    }
}
