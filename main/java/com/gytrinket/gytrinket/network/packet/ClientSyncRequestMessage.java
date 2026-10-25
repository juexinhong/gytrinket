package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端定义同步请求（C-&gt;S）：客户端进入世界时主动拉取定义覆盖层与物品属性表。
 * <p>
 * 登录阶段由客户端主动拉取，可规避服务端在登录事件里推送时包早于客户端监听器就绪
 * 而被丢弃的时序问题；登录事件推送作为第二道保险保留。
 */
public class ClientSyncRequestMessage {

    public ClientSyncRequestMessage() {
    }

    public ClientSyncRequestMessage(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                NetworkHandler.sendDefsSyncToPlayer(player);
            }
        });
        context.setPacketHandled(true);
    }
}
