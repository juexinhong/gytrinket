package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncBurstFiringMessage {
    private boolean isBurstFiring;

    public SyncBurstFiringMessage() {}

    public SyncBurstFiringMessage(boolean isBurstFiring) {
        this.isBurstFiring = isBurstFiring;
    }

    public SyncBurstFiringMessage(FriendlyByteBuf buf) {
        this.isBurstFiring = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isBurstFiring);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncBurstFiringOnClient(isBurstFiring));
        });
        context.setPacketHandled(true);
    }
}
