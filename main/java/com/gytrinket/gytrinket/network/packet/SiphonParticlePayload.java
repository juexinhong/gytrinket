package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SiphonParticlePayload(double targetX, double targetY, double targetZ, double targetHeight,
                                     double playerHeadX, double playerHeadY, double playerHeadZ) implements CustomPacketPayload {
    public static final Type<SiphonParticlePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "siphon_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SiphonParticlePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SiphonParticlePayload decode(RegistryFriendlyByteBuf buf) {
            return new SiphonParticlePayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SiphonParticlePayload msg) {
            buf.writeDouble(msg.targetX);
            buf.writeDouble(msg.targetY);
            buf.writeDouble(msg.targetZ);
            buf.writeDouble(msg.targetHeight);
            buf.writeDouble(msg.playerHeadX);
            buf.writeDouble(msg.playerHeadY);
            buf.writeDouble(msg.playerHeadZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SiphonParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSiphonParticlesMessage(
                payload.targetX, payload.targetY, payload.targetZ, payload.targetHeight,
                payload.playerHeadX, payload.playerHeadY, payload.playerHeadZ
            );
        });
    }
}
