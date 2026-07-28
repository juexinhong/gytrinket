package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestConfigDataMessage {
    public RequestConfigDataMessage() {}

    public RequestConfigDataMessage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            NetworkHandler.sendConfigDataToPlayer(player);
        });
        context.setPacketHandled(true);
    }
}
