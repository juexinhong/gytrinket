package com.gy_mod.gy_trinket.core.ghost_fuselage;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.SyncGhostStealthMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 幽灵机身网络同步辅助类
 */
public class GhostFuselageSyncHelper {

    private GhostFuselageSyncHelper() {}

    /**
     * 广播隐身进度到所有追踪该玩家的客户端
     */
    public static void sendStealthProgress(ServerPlayer player, double progress) {
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
            new SyncGhostStealthMessage(player.getId(), (float) progress));
    }
}
