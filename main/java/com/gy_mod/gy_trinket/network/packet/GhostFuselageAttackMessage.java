package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GhostFuselageAttackMessage {
    public GhostFuselageAttackMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public GhostFuselageAttackMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                GhostFuselageManager.onClientSwingAttack(player);
            }
        });
        context.setPacketHandled(true);
    }
}
