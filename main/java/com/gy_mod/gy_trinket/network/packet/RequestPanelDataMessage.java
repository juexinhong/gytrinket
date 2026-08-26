package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestPanelDataMessage {
    public RequestPanelDataMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestPanelDataMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                NetworkHandler.sendPanelUpdate(player);
            }
        });
        context.setPacketHandled(true);
    }
}
