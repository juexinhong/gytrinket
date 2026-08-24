package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 服务端 -> 客户端：同步光点核心各槽位禁用原因（用于容器界面显示灰色遮罩）
 */
public record SyncDisabledReasonsPayload(List<String> reasons) implements CustomPacketPayload {
    public static final Type<SyncDisabledReasonsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_disabled_reasons"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDisabledReasonsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncDisabledReasonsPayload::reasons,
        SyncDisabledReasonsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncDisabledReasonsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleSyncDisabledReasons(payload));
    }
}
