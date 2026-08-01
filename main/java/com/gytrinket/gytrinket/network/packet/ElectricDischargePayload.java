package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ElectricDischargePayload() implements CustomPacketPayload {
    public static final Type<ElectricDischargePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "electric_discharge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricDischargePayload> STREAM_CODEC =
        StreamCodec.unit(new ElectricDischargePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ElectricDischargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ElectricDischargeManager.releaseElectric(player);
            }
        });
    }
}
