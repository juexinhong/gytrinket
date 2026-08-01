package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

public record RequestAttributesPayload() implements CustomPacketPayload {
    public static final Type<RequestAttributesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_attributes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAttributesPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestAttributesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestAttributesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                var attributes = AttributeManager.getPlayerAttributes(serverPlayer);
                PacketDistributor.sendToPlayer(serverPlayer, new ResponseAttributesPayload(attributes));
            }
        });
    }
}
