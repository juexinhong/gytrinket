package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayManager;
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
