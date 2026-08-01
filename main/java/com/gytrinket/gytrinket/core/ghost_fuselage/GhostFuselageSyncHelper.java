package com.gytrinket.gytrinket.core.ghost_fuselage;

import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.SyncGhostStealthPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 幽灵机身网络同步辅助类
 */
public class GhostFuselageSyncHelper {

    private GhostFuselageSyncHelper() {}

    /**
     * 广播隐身进度到所有追踪该玩家的客户端
     */
    public static void sendStealthProgress(ServerPlayer player, double progress) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SyncGhostStealthPayload(player.getId(), (float) progress));
    }
}
