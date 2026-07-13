package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 蜂群管理器
 * <p>
 * 处理蜂群的构建条件检测和管理逻辑。
 * 玩家光点核心中需包含指定蜂群模块物品才能构建。
 * 护盾相关行为（修复模式/破裂增益）由实体每刻轮询护盾状态实现，无需在此挂钩事件。
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class SwarmManager {
    private static final SwarmManager INSTANCE = new SwarmManager();

    /** 玩家构建条件缓存 */
    private static final Set<UUID> PLAYER_CAN_BUILD_SWARM = new HashSet<>();

    private SwarmManager() {}

    public static SwarmManager getInstance() {
        return INSTANCE;
    }

    /**
     * 开始构建蜂群
     */
    public void startBuildingSwarm(Player player) {
        ConstructType type = ConstructManager.getInstance().getConstructType(SwarmConstructTypes.SWARM);
        if (type == null) return;

        SwarmBuilder builder = new SwarmBuilder(player, type);
        ConstructManager.getInstance().startBuilding(player, builder);
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

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            clearPlayerCache(playerUUID);
            return;
        }

        boolean hasSwarmModule = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                var item = stack.getItem();
                if (Config.isSwarmModuleItem(item)) {
                    hasSwarmModule = true;
                }
            }
        }

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

        // 如果玩家现在可以构建蜂群（之前不能），则开始构建（补满至上限）
        if (!canBuildBefore && hasSwarmModule) {
            ServerPlayer serverPlayer = event.getPlayer();
            if (serverPlayer != null && SwarmManager.getInstance().canBuildSwarm(serverPlayer)) {
                SwarmManager.getInstance().startBuildingSwarm(serverPlayer);
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

        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

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
    }
}
