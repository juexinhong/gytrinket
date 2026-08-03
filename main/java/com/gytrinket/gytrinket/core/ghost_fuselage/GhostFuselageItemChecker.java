package com.gytrinket.gytrinket.core.ghost_fuselage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        boolean hasGhostFuselage = PlayerStoreUtils.hasActiveItem(player, Config::isGhostFuselageItem);

        GhostFuselageManager.setHasGhostFuselage(player, hasGhostFuselage);
    }
}
