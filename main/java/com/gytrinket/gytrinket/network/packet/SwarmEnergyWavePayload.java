package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwarmEnergyWavePayload(int entityId, double x, double y, double z, double dirX, double dirY, double dirZ, boolean isRepair) implements CustomPacketPayload {
    public static final Type<SwarmEnergyWavePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "swarm_energy_wave"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwarmEnergyWavePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SwarmEnergyWavePayload decode(RegistryFriendlyByteBuf buf) {
            return new SwarmEnergyWavePayload(
                buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SwarmEnergyWavePayload msg) {
            buf.writeVarInt(msg.entityId);
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
            buf.writeBoolean(msg.isRepair);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwarmEnergyWavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.effect.energywave.EnergyWaveVisualManager.addSwarmWave(
                payload.entityId,
                payload.x, payload.y, payload.z,
                payload.dirX, payload.dirY, payload.dirZ,
                payload.isRepair
            );
        });
    }
}
