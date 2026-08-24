package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：同步玩家背包中持有的代币数量（随机构建代币机制，背包内容变动时更新）
 */
public record SyncTokenCountPayload(int tokenCount) implements CustomPacketPayload {
    public static final Type<SyncTokenCountPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_token_count"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTokenCountPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, SyncTokenCountPayload::tokenCount,
        SyncTokenCountPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncTokenCountPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleSyncTokenCount(payload));
    }
}
