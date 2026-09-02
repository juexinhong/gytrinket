package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S->C：模拟爆炸贴图特效（爆心位置 + 爆炸半径）
 */
public record SimulatedExplosionFXPayload(double x, double y, double z, double radius) implements CustomPacketPayload {
    public static final Type<SimulatedExplosionFXPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "simulated_explosion_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SimulatedExplosionFXPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, SimulatedExplosionFXPayload::x,
        ByteBufCodecs.DOUBLE, SimulatedExplosionFXPayload::y,
        ByteBufCodecs.DOUBLE, SimulatedExplosionFXPayload::z,
        ByteBufCodecs.DOUBLE, SimulatedExplosionFXPayload::radius,
        SimulatedExplosionFXPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SimulatedExplosionFXPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSimulatedExplosionFXMessage(
                payload.x, payload.y, payload.z, payload.radius);
        });
    }
}
