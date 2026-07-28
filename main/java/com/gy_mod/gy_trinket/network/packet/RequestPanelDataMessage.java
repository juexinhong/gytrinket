package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestPanelDataMessage {
    public RequestPanelDataMessage() {}

    public RequestPanelDataMessage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(RequestPanelDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                NetworkHandler.sendPanelUpdate(player);
            }
        });

        context.setPacketHandled(true);
    }
}
