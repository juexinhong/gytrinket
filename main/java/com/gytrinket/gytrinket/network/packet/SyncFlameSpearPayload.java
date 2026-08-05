package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.weapon.flamespear.FlameSpearHudRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 焰矛HUD同步包（服务端→客户端）。
 * 同步当前充能值与当前充能速率，供HUD实时显示。
 */
public record SyncFlameSpearPayload(double chargeValue, double chargeRate) implements CustomPacketPayload {

    public static final Type<SyncFlameSpearPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_flame_spear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFlameSpearPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SyncFlameSpearPayload::chargeValue,
            ByteBufCodecs.DOUBLE, SyncFlameSpearPayload::chargeRate,
            SyncFlameSpearPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncFlameSpearPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FlameSpearHudRenderer.setChargeData(payload.chargeValue, payload.chargeRate));
    }
}
