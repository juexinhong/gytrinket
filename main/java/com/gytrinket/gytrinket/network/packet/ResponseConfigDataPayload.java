package com.gytrinket.gytrinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ResponseConfigDataPayload(ListTag itemConfigData, List<String> allAttributeNames,
                                         boolean openScreen) implements CustomPacketPayload {
    public static final Type<ResponseConfigDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "response_config_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResponseConfigDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ResponseConfigDataPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            ListTag itemConfigData = tag != null ? tag.getList("items", 10) : new ListTag();
            List<String> allAttributeNames = new ArrayList<>();
            if (tag != null) {
                ListTag attrsList = tag.getList("allAttrs", 10);
                for (int i = 0; i < attrsList.size(); i++) {
                    allAttributeNames.add(attrsList.getCompound(i).getString("name"));
                }
            }
            boolean openScreen = tag != null && tag.getBoolean("openScreen");
            return new ResponseConfigDataPayload(itemConfigData, allAttributeNames, openScreen);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ResponseConfigDataPayload msg) {
            CompoundTag tag = new CompoundTag();
            tag.put("items", msg.itemConfigData);
            ListTag attrsList = new ListTag();
            for (String attr : msg.allAttributeNames) {
                CompoundTag attrTag = new CompoundTag();
                attrTag.putString("name", attr);
                attrsList.add(attrTag);
            }
            tag.put("allAttrs", attrsList);
            tag.putBoolean("openScreen", msg.openScreen);
            buf.writeNbt(tag);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResponseConfigDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientPacketHandler.handleResponseConfigData(payload);
        });
    }
}
