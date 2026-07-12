package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
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
 * 僚机管理器
 * <p>
 * 处理僚机的构建条件检测和管理逻辑。
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class WingmanManager {
    private static final WingmanManager INSTANCE = new WingmanManager();

    /** 玩家构建条件缓存 */
    private static final Set<UUID> PLAYER_CAN_BUILD_WINGMAN = new HashSet<>();

    private WingmanManager() {
        ConstructManager.getInstance().registerBuildConditionChecker(
                WingmanConstructTypes.WINGMAN,
                player -> PLAYER_CAN_BUILD_WINGMAN.contains(player.getUUID())
        );
    }

    public static WingmanManager getInstance() {
        return INSTANCE;
    }

    /**
     * 开始构建僚机
     */
    public void startBuildingWingman(Player player) {
        ConstructType type = ConstructManager.getInstance().getConstructType(WingmanConstructTypes.WINGMAN);
        if (type == null) return;

        WingmanBuilder builder = new WingmanBuilder(player, type);
        ConstructManager.getInstance().startBuilding(player, builder);
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

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            clearPlayerCache(playerUUID);
            return;
        }

        boolean hasWingmanModule = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                var item = stack.getItem();
                if (Config.isWingmanModuleItem(item)) {
                    hasWingmanModule = true;
                }
            }
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

        // 如果玩家现在可以构建僚机（之前不能），则开始构建
        if (!canBuildBefore && hasWingmanModule) {
            ServerPlayer serverPlayer = event.getPlayer();
            if (serverPlayer != null && WingmanManager.getInstance().canBuildWingman(serverPlayer)) {
                WingmanManager.getInstance().startBuildingWingman(serverPlayer);
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
    }
}
