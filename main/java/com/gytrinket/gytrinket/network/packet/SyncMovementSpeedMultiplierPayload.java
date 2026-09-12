package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.modifier.player.movement.MovementSpeedMultiplierCache;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：同步玩家速度聚合乘数到客户端
 * <p>
 * 游泳速度乘数在客户端 travel（本地权威移动）中消费，
 * 服务端在聚合乘数变化时发送本包（无 UUID 字段：接收者即目标玩家）。
 */
public record SyncMovementSpeedMultiplierPayload(double multiplier) implements CustomPacketPayload {
    public static final Type<SyncMovementSpeedMultiplierPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_movement_speed_multiplier"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SyncMovementSpeedMultiplierPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, SyncMovementSpeedMultiplierPayload::multiplier,
        SyncMovementSpeedMultiplierPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncMovementSpeedMultiplierPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player != null) {
                MovementSpeedMultiplierCache.set(player.getUUID(), payload.multiplier());
            }
        });
    }
}
