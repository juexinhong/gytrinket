package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.AttackStateManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AttackStatePayload(int stateOrdinal, int holdTicks) implements CustomPacketPayload {
    public static final Type<AttackStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttackStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AttackStatePayload::stateOrdinal,
        ByteBufCodecs.VAR_INT, AttackStatePayload::holdTicks,
        AttackStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AttackStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AttackStateManager.updatePlayerState(
                    player.getUUID(), payload.stateOrdinal, payload.holdTicks);
            }
        });
    }
}
