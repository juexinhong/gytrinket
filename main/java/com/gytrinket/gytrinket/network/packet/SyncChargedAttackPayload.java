package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncChargedAttackPayload(double chargeValue, double chargedDamage) implements CustomPacketPayload {
    public static final Type<SyncChargedAttackPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_charged_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChargedAttackPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, SyncChargedAttackPayload::chargeValue,
        ByteBufCodecs.DOUBLE, SyncChargedAttackPayload::chargedDamage,
        SyncChargedAttackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncChargedAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackHudRenderer.setChargeValue(payload.chargeValue, payload.chargedDamage);
        });
    }
}
