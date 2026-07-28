package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncComboCooldownMessage {
    private boolean inCooldown;
    private int remainingTicks;

    public SyncComboCooldownMessage() {}

    public SyncComboCooldownMessage(boolean inCooldown, int remainingTicks) {
        this.inCooldown = inCooldown;
        this.remainingTicks = remainingTicks;
    }

    public SyncComboCooldownMessage(FriendlyByteBuf buf) {
        this.inCooldown = buf.readBoolean();
        this.remainingTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(inCooldown);
        buf.writeInt(remainingTicks);
    }

    public static void handle(SyncComboCooldownMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncComboCooldownOnClient(msg.inCooldown, msg.remainingTicks);
            });
        });

        context.setPacketHandled(true);
    }
}
