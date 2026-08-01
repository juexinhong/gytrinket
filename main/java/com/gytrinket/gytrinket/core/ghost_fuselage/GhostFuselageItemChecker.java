package com.gytrinket.gytrinket.core.ghost_fuselage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 幽灵机身物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有幽灵机身物品
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class GhostFuselageItemChecker {

    private GhostFuselageItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();
        if (player == null) {
            GhostFuselageManager.setHasGhostFuselage(playerUUID, false);
            return;
        }

        boolean hasGhostFuselage = PlayerStoreUtils.hasActiveItem(player, Config::isGhostFuselageItem);

        GhostFuselageManager.setHasGhostFuselage(playerUUID, hasGhostFuselage);
    }
}
