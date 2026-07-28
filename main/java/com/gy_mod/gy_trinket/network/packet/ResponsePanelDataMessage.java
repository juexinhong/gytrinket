package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ResponsePanelDataMessage {
    public Map<String, Double> attributes;
    public ListTag items;
    public int slotCount;
    public CompoundTag upgradeData;
    public ListTag upgradeTargets;
    public int modLevel;
    public int upgradeExp;
    public int upgradePoints;

    public ResponsePanelDataMessage() {}

    public ResponsePanelDataMessage(Map<String, Double> attributes, ListTag items, int slotCount,
                                     CompoundTag upgradeData, ListTag upgradeTargets,
                                     int modLevel, int upgradeExp, int upgradePoints) {
        this.attributes = attributes;
        this.items = items;
        this.slotCount = slotCount;
        this.upgradeData = upgradeData;
        this.upgradeTargets = upgradeTargets;
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
    }

    public ResponsePanelDataMessage(FriendlyByteBuf buf) {
        this.attributes = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            double value = buf.readDouble();
            this.attributes.put(name, value);
        }
        CompoundTag tag = buf.readNbt();
        this.items = tag != null ? tag.getList("items", 10) : new ListTag();
        this.slotCount = tag != null ? tag.getInt("slotCount") : 0;
        this.upgradeData = tag != null ? tag.getCompound("upgradeData") : new CompoundTag();
        this.upgradeTargets = tag != null ? tag.getList("upgradeTargets", 10) : new ListTag();
        this.modLevel = tag != null ? tag.getInt("modLevel") : 0;
        this.upgradeExp = tag != null ? tag.getInt("upgradeExp") : 0;
        this.upgradePoints = tag != null ? tag.getInt("upgradePoints") : 0;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(attributes.size());
        for (var entry : attributes.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeDouble(entry.getValue());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("items", items);
        tag.putInt("slotCount", slotCount);
        tag.put("upgradeData", upgradeData);
        tag.put("upgradeTargets", upgradeTargets);
        tag.putInt("modLevel", modLevel);
        tag.putInt("upgradeExp", upgradeExp);
        tag.putInt("upgradePoints", upgradePoints);
        buf.writeNbt(tag);
    }

    public static void handle(ResponsePanelDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleResponsePanelData(msg));
        });

        context.setPacketHandled(true);
    }
}
