package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GhostFuselageAttackPayload() implements CustomPacketPayload {

    public static final Type<GhostFuselageAttackPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "ghost_fuselage_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostFuselageAttackPayload> STREAM_CODEC =
        StreamCodec.unit(new GhostFuselageAttackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GhostFuselageAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GhostFuselageManager.onClientSwingAttack(player);
            }
        });
    }
}
