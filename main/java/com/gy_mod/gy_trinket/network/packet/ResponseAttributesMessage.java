package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ResponseAttributesMessage {
    private Map<String, Double> attributes;

    public ResponseAttributesMessage() {}

    public ResponseAttributesMessage(Map<String, Double> attributes) {
        this.attributes = attributes;
    }

    public ResponseAttributesMessage(FriendlyByteBuf buf) {
        Map<String, Double> attributes = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            double value = buf.readDouble();
            attributes.put(name, value);
        }
        this.attributes = attributes;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(attributes.size());
        for (var entry : attributes.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeDouble(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleResponseAttributesMessage(attributes);
            });
        });

        context.setPacketHandled(true);
    }
}
