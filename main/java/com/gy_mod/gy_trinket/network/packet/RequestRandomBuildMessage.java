package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.random_build.RandomBuildManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：请求随机构建随机池
 * 已有持久化随机池时直接返回（重进游戏后保持），否则生成新池
 */
public class RequestRandomBuildMessage {
    public RequestRandomBuildMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestRandomBuildMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (RandomBuildManager.hasStoredPool(player.getUUID())) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new ResponseRandomBuildMessage(RandomBuildManager.getCurrentPool(player.getUUID())));
            } else {
                NetworkHandler.sendRandomBuildPoolToPlayer(player);
            }
        });
        context.setPacketHandled(true);
    }
}
