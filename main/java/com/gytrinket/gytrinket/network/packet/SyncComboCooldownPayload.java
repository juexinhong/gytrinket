package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncComboCooldownPayload(boolean inCooldown, int remainingTicks) implements CustomPacketPayload {
    public static final Type<SyncComboCooldownPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_combo_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncComboCooldownPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SyncComboCooldownPayload::inCooldown,
        ByteBufCodecs.INT, SyncComboCooldownPayload::remainingTicks,
        SyncComboCooldownPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncComboCooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncComboCooldownOnClient(payload.inCooldown, payload.remainingTicks);
        });
    }
}
