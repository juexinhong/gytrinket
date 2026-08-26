package com.gy_mod.gy_trinket.core.engineering_fuselage;

import com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.items.ModItems;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * 工程机身物品检测器
 * <p>
 * 监听属性计算事件和构造体列表变更事件，检查玩家光点核心中是否有工程机身物品，
 * 并更新动态构建速度属性。
 * <p>
 * 使用 LOW 优先级（onAttributesCalculated），确保在 DroneManager/WingmanManager/SwarmManager
 * （NORMAL 优先级）更新构建条件后执行，避免使用过期的 canPlayerBuildConstruct 数据。
 * <p>
 * 监听 PlayerConstructListChangedEvent，在构造体部署/移除时实时更新未部署数量，
 * 因为 PlayerAttributesCalculatedEvent 仅在光点核心物品变化时触发，不会在构造体变化时触发。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class EngineeringFuselageItemChecker {

    private EngineeringFuselageItemChecker() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();

        if (player == null) {
            EngineeringFuselageManager.removeDynamicAttributes(playerUUID);
            return;
        }

        boolean hasEngineeringFuselage = PlayerStoreUtils.hasActiveItem(player,
                item -> item == ModItems.ENGINEERING_FUSELAGE.get());

        if (hasEngineeringFuselage) {
            EngineeringFuselageManager.updateDynamicAttributes(playerUUID, player);
        } else {
            EngineeringFuselageManager.removeDynamicAttributes(playerUUID);
        }
    }

    /**
     * 监听构造体列表变更（部署/移除），实时更新工程机身的动态构建速度属性。
     * <p>
     * 当构造体被部署或移除时，未部署数量发生变化，需要重新计算构建速度加成。
     */
    @SubscribeEvent
    public static void onConstructListChanged(PlayerConstructListChangedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();
        if (player == null) {
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
            }
            if (player == null) {
                return;
            }
        }

        if (player.level().isClientSide) {
            return;
        }

        boolean hasEngineeringFuselage = PlayerStoreUtils.hasActiveItem(player,
                item -> item == ModItems.ENGINEERING_FUSELAGE.get());

        if (hasEngineeringFuselage) {
            EngineeringFuselageManager.updateDynamicAttributes(playerUUID, player);
        }
    }
}
