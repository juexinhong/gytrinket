package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncGhostStealthPayload(int entityId, float stealthProgress) implements CustomPacketPayload {
    public static final Type<SyncGhostStealthPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_ghost_stealth"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGhostStealthPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SyncGhostStealthPayload::entityId,
        ByteBufCodecs.FLOAT, SyncGhostStealthPayload::stealthProgress,
        SyncGhostStealthPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncGhostStealthPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageClientData.setStealthProgress(
                payload.entityId, payload.stealthProgress);
        });
    }
}
