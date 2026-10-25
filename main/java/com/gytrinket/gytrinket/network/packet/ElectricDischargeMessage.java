package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ElectricDischargeMessage {
    public ElectricDischargeMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public ElectricDischargeMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                ElectricDischargeManager.releaseElectric(player);
            }
        });
        context.setPacketHandled(true);
    }
}
