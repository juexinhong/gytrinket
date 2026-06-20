package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShieldParticlePacket(
    int entityId,
    double originOffsetX, double originOffsetY, double originOffsetZ,
    double offsetX, double offsetY, double offsetZ,
    double dirX, double dirY, double dirZ,
    int delayTicks
) implements CustomPacketPayload {

    public static final Type<ShieldParticlePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "shield_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldParticlePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ShieldParticlePacket decode(RegistryFriendlyByteBuf buf) {
            return new ShieldParticlePacket(
                buf.readInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ShieldParticlePacket msg) {
            buf.writeInt(msg.entityId);
            buf.writeDouble(msg.originOffsetX);
            buf.writeDouble(msg.originOffsetY);
            buf.writeDouble(msg.originOffsetZ);
            buf.writeDouble(msg.offsetX);
            buf.writeDouble(msg.offsetY);
            buf.writeDouble(msg.offsetZ);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
            buf.writeVarInt(msg.delayTicks);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShieldParticlePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.delayTicks > 0) {
                com.gytrinket.gytrinket.client.effect.particle.ShieldParticleTimerManager.getInstance()
                    .addPendingParticle(payload.entityId, payload.originOffsetX, payload.originOffsetY, payload.originOffsetZ,
                        payload.offsetX, payload.offsetY, payload.offsetZ,
                        payload.dirX, payload.dirY, payload.dirZ, payload.delayTicks);
            } else {
                com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderManager.getInstance()
                    .addParticle(payload.entityId, payload.originOffsetX, payload.originOffsetY, payload.originOffsetZ,
                        payload.offsetX, payload.offsetY, payload.offsetZ,
                        payload.dirX, payload.dirY, payload.dirZ);
            }
        });
    }
}
