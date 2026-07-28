package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ResponseConfigDataMessage {
    public ListTag itemConfigData;
    public List<String> allAttributeNames;
    public boolean openScreen;

    public ResponseConfigDataMessage() {}

    public ResponseConfigDataMessage(ListTag itemConfigData, List<String> allAttributeNames, boolean openScreen) {
        this.itemConfigData = itemConfigData;
        this.allAttributeNames = allAttributeNames;
        this.openScreen = openScreen;
    }

    public ResponseConfigDataMessage(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        this.itemConfigData = tag != null ? tag.getList("items", 10) : new ListTag();
        this.allAttributeNames = new ArrayList<>();
        if (tag != null) {
            ListTag attrsList = tag.getList("allAttrs", 10);
            for (int i = 0; i < attrsList.size(); i++) {
                allAttributeNames.add(attrsList.getCompound(i).getString("name"));
            }
        }
        this.openScreen = tag != null && tag.getBoolean("openScreen");
    }

    public void toBytes(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        tag.put("items", itemConfigData);
        ListTag attrsList = new ListTag();
        for (String attr : allAttributeNames) {
            CompoundTag attrTag = new CompoundTag();
            attrTag.putString("name", attr);
            attrsList.add(attrTag);
        }
        tag.put("allAttrs", attrsList);
        tag.putBoolean("openScreen", openScreen);
        buf.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleResponseConfigData(this));
        });
        context.setPacketHandled(true);
    }
}
