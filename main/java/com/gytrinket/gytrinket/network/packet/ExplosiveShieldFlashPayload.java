package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExplosiveShieldFlashPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ExplosiveShieldFlashPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "explosive_shield_flash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplosiveShieldFlashPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::x,
        ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::y,
        ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::z,
        ExplosiveShieldFlashPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ExplosiveShieldFlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleExplosiveShieldFlashMessage(payload.x, payload.y, payload.z);
        });
    }
}
