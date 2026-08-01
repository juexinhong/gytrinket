package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record ResponseAttributesPayload(Map<String, Double> attributes) implements CustomPacketPayload {
    public static final Type<ResponseAttributesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "response_attributes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResponseAttributesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ResponseAttributesPayload decode(RegistryFriendlyByteBuf buf) {
            Map<String, Double> attributes = new HashMap<>();
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                String name = buf.readUtf();
                double value = buf.readDouble();
                attributes.put(name, value);
            }
            return new ResponseAttributesPayload(attributes);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ResponseAttributesPayload msg) {
            buf.writeInt(msg.attributes.size());
            for (var entry : msg.attributes.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeDouble(entry.getValue());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResponseAttributesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleResponseAttributesMessage(payload.attributes);
        });
    }
}
