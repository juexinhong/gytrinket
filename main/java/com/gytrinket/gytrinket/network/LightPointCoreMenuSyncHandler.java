package com.gytrinket.gytrinket.network;

import com.gytrinket.gytrinket.event.PlayerAttributesUpdateEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.menu.LightPointCoreMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 光点核心容器禁用状态同步
 * 玩家光点核心物品变化（属性重算完成后）时，若正打开光点核心容器，重发禁用原因刷新遮罩与提示
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class LightPointCoreMenuSyncHandler {

    private LightPointCoreMenuSyncHandler() {}

    @SubscribeEvent
    public static void onPlayerAttributesPost(PlayerAttributesUpdateEvent.Post event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerUUID());
        if (player == null) return;
        if (player.containerMenu instanceof LightPointCoreMenu) {
            NetworkHandler.sendDisabledReasonsToPlayer(player);
        }
    }
}
