package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncBurstFiringPayload(boolean isBurstFiring) implements CustomPacketPayload {
    public static final Type<SyncBurstFiringPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_burst_firing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBurstFiringPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SyncBurstFiringPayload::isBurstFiring,
        SyncBurstFiringPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncBurstFiringPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncBurstFiringOnClient(payload.isBurstFiring);
        });
    }
}
