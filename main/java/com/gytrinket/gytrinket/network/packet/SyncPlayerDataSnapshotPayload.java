package com.gytrinket.gytrinket.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerDataSnapshotPayload(CompoundTag snapshotData) implements CustomPacketPayload {
    public static final Type<SyncPlayerDataSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_player_data_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerDataSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncPlayerDataSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new SyncPlayerDataSnapshotPayload(tag != null ? tag : new CompoundTag());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncPlayerDataSnapshotPayload msg) {
            buf.writeNbt(msg.snapshotData);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncPlayerDataSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.datacenter.ClientDataCenter.loadFromNBT(payload.snapshotData);
        });
    }
}
