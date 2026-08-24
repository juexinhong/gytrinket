package com.gytrinket.gytrinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record ResponsePanelDataPayload(Map<String, Double> attributes, ListTag items, int slotCount,
                                        CompoundTag upgradeData, ListTag upgradeTargets,
                                        int modLevel, int upgradeExp, int upgradePoints, int randomPoints,
                                        int tokenCount,
                                        String[] disabledReasons) implements CustomPacketPayload {
    public static final Type<ResponsePanelDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "response_panel_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePanelDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ResponsePanelDataPayload decode(RegistryFriendlyByteBuf buf) {
            Map<String, Double> attributes = new HashMap<>();
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                String name = buf.readUtf();
                double value = buf.readDouble();
                attributes.put(name, value);
            }
            CompoundTag tag = buf.readNbt();
            ListTag items = tag != null ? tag.getList("items", 10) : new ListTag();
            int slotCount = tag != null ? tag.getInt("slotCount") : 0;
            CompoundTag upgradeData = tag != null ? tag.getCompound("upgradeData") : new CompoundTag();
            ListTag upgradeTargets = tag != null ? tag.getList("upgradeTargets", 10) : new ListTag();
            int modLevel = tag != null ? tag.getInt("modLevel") : 0;
            int upgradeExp = tag != null ? tag.getInt("upgradeExp") : 0;
            int upgradePoints = tag != null ? tag.getInt("upgradePoints") : 0;
            int randomPoints = tag != null ? tag.getInt("randomPoints") : 0;
            int tokenCount = tag != null ? tag.getInt("tokenCount") : 0;
            String[] disabledReasons = new String[0];
            if (tag != null && tag.contains("disabledReasons")) {
                ListTag reasonsTag = tag.getList("disabledReasons", net.minecraft.nbt.Tag.TAG_STRING);
                disabledReasons = new String[reasonsTag.size()];
                for (int i = 0; i < reasonsTag.size(); i++) {
                    disabledReasons[i] = reasonsTag.getString(i);
                }
            }
            return new ResponsePanelDataPayload(attributes, items, slotCount, upgradeData, upgradeTargets, modLevel, upgradeExp, upgradePoints, randomPoints, tokenCount, disabledReasons);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ResponsePanelDataPayload msg) {
            buf.writeInt(msg.attributes.size());
            for (var entry : msg.attributes.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeDouble(entry.getValue());
            }
            CompoundTag tag = new CompoundTag();
            tag.put("items", msg.items);
            tag.putInt("slotCount", msg.slotCount);
            tag.put("upgradeData", msg.upgradeData);
            tag.put("upgradeTargets", msg.upgradeTargets);
            tag.putInt("modLevel", msg.modLevel);
            tag.putInt("upgradeExp", msg.upgradeExp);
            tag.putInt("upgradePoints", msg.upgradePoints);
            tag.putInt("randomPoints", msg.randomPoints);
            tag.putInt("tokenCount", msg.tokenCount);
            ListTag reasonsTag = new ListTag();
            if (msg.disabledReasons != null) {
                for (String s : msg.disabledReasons) {
                    reasonsTag.add(net.minecraft.nbt.StringTag.valueOf(s));
                }
            }
            tag.put("disabledReasons", reasonsTag);
            buf.writeNbt(tag);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResponsePanelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientPacketHandler.handleResponsePanelData(payload);
        });
    }
}
