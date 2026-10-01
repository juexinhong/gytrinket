package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端定义同步请求（C-&gt;S）：客户端进入世界（LoggingIn）时主动拉取定义覆盖层与
 * 物品属性表。登录阶段客户端主动拉取可规避服务端在登录事件中推送的时序不可靠问题
 * （连接建立阶段包可能早于客户端监听器就绪而被丢弃），登录事件推送作为第二道保险保留。
 */
public record ClientSyncRequestPayload() implements CustomPacketPayload {
    public static final Type<ClientSyncRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "client_sync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSyncRequestPayload> STREAM_CODEC =
        StreamCodec.unit(new ClientSyncRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClientSyncRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConfigDefsSyncPayload.sendDefinitionsToPlayer(player);
            }
        });
    }
}
