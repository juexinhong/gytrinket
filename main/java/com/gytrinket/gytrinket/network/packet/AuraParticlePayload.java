package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AuraParticlePayload(double x, double y, double z, double radius) implements CustomPacketPayload {
    public static final Type<AuraParticlePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "aura_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuraParticlePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, AuraParticlePayload::x,
        ByteBufCodecs.DOUBLE, AuraParticlePayload::y,
        ByteBufCodecs.DOUBLE, AuraParticlePayload::z,
        ByteBufCodecs.DOUBLE, AuraParticlePayload::radius,
        AuraParticlePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AuraParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleAuraParticlesMessage(
                payload.x, payload.y, payload.z, payload.radius);
        });
    }
}
