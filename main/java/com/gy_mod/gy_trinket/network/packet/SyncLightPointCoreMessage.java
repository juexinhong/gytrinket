package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncLightPointCoreMessage {
    private int slotCount;
    private ListTag itemList;

    public SyncLightPointCoreMessage() {}

    public SyncLightPointCoreMessage(ListTag itemList, int slotCount) {
        this.itemList = itemList;
        this.slotCount = slotCount;
    }

    public SyncLightPointCoreMessage(FriendlyByteBuf buf) {
        this.slotCount = buf.readInt();
        CompoundTag tag = buf.readNbt();
        this.itemList = tag != null ? tag.getList("items", 10) : new ListTag();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(slotCount);
        CompoundTag tag = new CompoundTag();
        tag.put("items", itemList);
        buf.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncLightPointCoreMessage(itemList, slotCount));
        });
        context.setPacketHandled(true);
    }
}
