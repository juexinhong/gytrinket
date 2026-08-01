package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientNetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncLightPointCorePayload(ListTag itemList, int slotCount) implements CustomPacketPayload {
    public static final Type<SyncLightPointCorePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_light_point_core"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLightPointCorePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncLightPointCorePayload decode(RegistryFriendlyByteBuf buf) {
            int slotCount = buf.readInt();
            CompoundTag tag = buf.readNbt();
            ListTag itemList = tag != null ? tag.getList("items", 10) : new ListTag();
            return new SyncLightPointCorePayload(itemList, slotCount);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncLightPointCorePayload msg) {
            buf.writeInt(msg.slotCount);
            CompoundTag tag = new CompoundTag();
            tag.put("items", msg.itemList);
            buf.writeNbt(tag);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncLightPointCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientNetworkHandler.handleSyncLightPointCoreMessage(payload.itemList, payload.slotCount);
        });
    }
}
