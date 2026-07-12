package com.gytrinket.gytrinket.core.taskmaster;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 督战者物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有督战者模块，
 * 并更新动态属性（构建速度和移动速度）。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class TaskmasterItemChecker {

    private TaskmasterItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();

        if (player == null) {
            TaskmasterManager.removeDynamicAttributes(playerUUID);
            return;
        }

        boolean hasTaskmaster = PlayerStoreUtils.hasActiveItem(player, Config::isTaskmasterItem);

        if (hasTaskmaster) {
            TaskmasterManager.updateDynamicAttributes(playerUUID, player);
        } else {
            TaskmasterManager.removeDynamicAttributes(playerUUID);
        }
    }
}
