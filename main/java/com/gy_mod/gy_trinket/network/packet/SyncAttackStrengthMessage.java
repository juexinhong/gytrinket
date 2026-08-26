package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncAttackStrengthMessage {
    private boolean reflectToFull;

    public SyncAttackStrengthMessage() {}

    public SyncAttackStrengthMessage(boolean reflectToFull) {
        this.reflectToFull = reflectToFull;
    }

    public SyncAttackStrengthMessage(FriendlyByteBuf buf) {
        this.reflectToFull = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(reflectToFull);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(reflectToFull));
        });
        context.setPacketHandled(true);
    }
}
