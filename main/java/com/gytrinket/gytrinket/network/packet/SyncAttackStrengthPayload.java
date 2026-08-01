package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncAttackStrengthPayload(boolean reflectToFull) implements CustomPacketPayload {
    public static final Type<SyncAttackStrengthPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_attack_strength"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAttackStrengthPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SyncAttackStrengthPayload::reflectToFull,
        SyncAttackStrengthPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncAttackStrengthPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(payload.reflectToFull);
        });
    }
}
