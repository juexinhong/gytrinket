package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestAttributesMessage {
    public RequestAttributesMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestAttributesMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                var attributes = AttributeManager.getPlayerAttributes(player);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new ResponseAttributesMessage(attributes));
            }
        });
        context.setPacketHandled(true);
    }
}
