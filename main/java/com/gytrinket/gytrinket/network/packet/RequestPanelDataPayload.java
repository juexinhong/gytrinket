package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestPanelDataPayload() implements CustomPacketPayload {
    public static final Type<RequestPanelDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_panel_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPanelDataPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestPanelDataPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestPanelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NetworkHandler.sendPanelUpdate(player);
            }
        });
    }
}
