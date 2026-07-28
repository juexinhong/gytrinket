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

    public static void handle(SyncAttackStrengthMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(msg.reflectToFull);
            });
        });

        context.setPacketHandled(true);
    }
}
