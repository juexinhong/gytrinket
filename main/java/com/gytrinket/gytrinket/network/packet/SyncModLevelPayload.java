package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：同步光点等级/经验/升级点/刷新点（HUD 提示与面板显示用）
 */
public record SyncModLevelPayload(int modLevel, int upgradeExp, int upgradePoints, int randomPoints) implements CustomPacketPayload {
    public static final Type<SyncModLevelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_mod_level"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncModLevelPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, SyncModLevelPayload::modLevel,
        ByteBufCodecs.INT, SyncModLevelPayload::upgradeExp,
        ByteBufCodecs.INT, SyncModLevelPayload::upgradePoints,
        ByteBufCodecs.INT, SyncModLevelPayload::randomPoints,
        SyncModLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncModLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleSyncModLevel(payload));
    }
}
