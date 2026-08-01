package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncGhostMoveSpeedPayload(float moveReduction) implements CustomPacketPayload {
    public static final Type<SyncGhostMoveSpeedPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_ghost_move_speed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGhostMoveSpeedPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, SyncGhostMoveSpeedPayload::moveReduction,
        SyncGhostMoveSpeedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncGhostMoveSpeedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GhostFuselageManager.setSyncedMoveReduction(
                    player.getUUID(), payload.moveReduction);
            }
        });
    }
}
