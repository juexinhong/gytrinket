package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.level.ModLevelManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：消耗 1 个刷新点刷新随机构建随机池
 */
public class RequestRefreshRandomPoolMessage {
    public RequestRefreshRandomPoolMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestRefreshRandomPoolMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!Config.isRandomBuildEnabled()) return;

            if (!ModLevelManager.consumeRandomPoints(player.getUUID(), 1)) {
                player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.not_enough_random_points"));
                return;
            }
            // 尽量不与上一轮重复：剔除旧池后重新生成
            NetworkHandler.sendRandomBuildPoolToPlayer(player, true);
            // 同步刷新点消耗
            NetworkHandler.sendModLevelSyncToPlayer(player);
        });
        context.setPacketHandled(true);
    }
}
