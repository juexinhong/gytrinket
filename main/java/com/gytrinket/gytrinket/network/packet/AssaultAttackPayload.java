package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.assault.AssaultManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AssaultAttackPayload() implements CustomPacketPayload {
    public static final Type<AssaultAttackPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "assault_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssaultAttackPayload> STREAM_CODEC =
        StreamCodec.unit(new AssaultAttackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AssaultAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AssaultManager.triggerAssault(player);
            }
        });
    }
}
