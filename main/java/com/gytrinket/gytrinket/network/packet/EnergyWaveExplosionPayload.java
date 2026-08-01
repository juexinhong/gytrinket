package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EnergyWaveExplosionPayload(double x, double y, double z, double dirX, double dirY, double dirZ,
                                         double splashLength, int positionSyncEntityId, int colorType,
                                         double offsetDistance) implements CustomPacketPayload {
    public static final Type<EnergyWaveExplosionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "energy_wave_explosion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnergyWaveExplosionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EnergyWaveExplosionPayload decode(RegistryFriendlyByteBuf buf) {
            return new EnergyWaveExplosionPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, EnergyWaveExplosionPayload msg) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
            buf.writeDouble(msg.splashLength);
            buf.writeVarInt(msg.positionSyncEntityId);
            buf.writeVarInt(msg.colorType);
            buf.writeDouble(msg.offsetDistance);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EnergyWaveExplosionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.effect.energywave.EnergyWaveVisualManager.addExplosionWave(
                payload.x, payload.y, payload.z,
                payload.dirX, payload.dirY, payload.dirZ,
                payload.splashLength,
                payload.positionSyncEntityId,
                payload.colorType,
                payload.offsetDistance
            );
        });
    }
}
