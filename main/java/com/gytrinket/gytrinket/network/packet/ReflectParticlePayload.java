package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReflectParticlePayload(double x, double y, double z, double dirX, double dirY, double dirZ,
                                      int particleCount, double maxAngleDegrees, double speedMultiplier) implements CustomPacketPayload {
    public static final Type<ReflectParticlePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "reflect_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReflectParticlePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ReflectParticlePayload decode(RegistryFriendlyByteBuf buf) {
            return new ReflectParticlePayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ReflectParticlePayload msg) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
            buf.writeVarInt(msg.particleCount);
            buf.writeDouble(msg.maxAngleDegrees);
            buf.writeDouble(msg.speedMultiplier);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReflectParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleReflectParticlesMessage(
                payload.x, payload.y, payload.z, payload.dirX, payload.dirY, payload.dirZ,
                payload.particleCount, payload.maxAngleDegrees, payload.speedMultiplier);
        });
    }
}
