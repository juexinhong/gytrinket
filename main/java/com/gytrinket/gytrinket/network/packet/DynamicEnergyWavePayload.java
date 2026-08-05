package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.effect.energywave.EnergyWaveVisualManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 动态能量波网络包：客户端实时更新单个能量波的长度/宽度/朝向。
 */
public record DynamicEnergyWavePayload(int id, boolean active,
                                       double x, double y, double z,
                                       double dirX, double dirY, double dirZ,
                                       double length, double width) implements CustomPacketPayload {

    public static final Type<DynamicEnergyWavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "dynamic_energy_wave"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicEnergyWavePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DynamicEnergyWavePayload decode(RegistryFriendlyByteBuf buf) {
            return new DynamicEnergyWavePayload(
                    buf.readVarInt(), buf.readBoolean(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, DynamicEnergyWavePayload msg) {
            buf.writeVarInt(msg.id);
            buf.writeBoolean(msg.active);
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
            buf.writeDouble(msg.length);
            buf.writeDouble(msg.width);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DynamicEnergyWavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active) {
                EnergyWaveVisualManager.addOrUpdateDynamicWave(
                        payload.id, payload.x, payload.y, payload.z,
                        payload.dirX, payload.dirY, payload.dirZ,
                        payload.length, payload.width);
            } else {
                EnergyWaveVisualManager.removeDynamicWave(payload.id);
            }
        });
    }
}
