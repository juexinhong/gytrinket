package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPlayerDataSnapshotMessage {
    private CompoundTag snapshotData;

    public SyncPlayerDataSnapshotMessage() {}

    public SyncPlayerDataSnapshotMessage(CompoundTag snapshotData) {
        this.snapshotData = snapshotData;
    }

    public SyncPlayerDataSnapshotMessage(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        this.snapshotData = tag != null ? tag : new CompoundTag();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(snapshotData);
    }

    public static void handle(SyncPlayerDataSnapshotMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.datacenter.ClientDataCenter.loadFromNBT(msg.snapshotData);
            });
        });

        context.setPacketHandled(true);
    }
}
