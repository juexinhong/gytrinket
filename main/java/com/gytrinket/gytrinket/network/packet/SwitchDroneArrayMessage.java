package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneArrayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwitchDroneArrayMessage {
    public SwitchDroneArrayMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public SwitchDroneArrayMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                DroneArrayManager.getInstance().switchToNextArray(player);
            }
        });
        context.setPacketHandled(true);
    }
}
