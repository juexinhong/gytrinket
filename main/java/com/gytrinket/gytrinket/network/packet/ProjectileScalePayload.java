package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：同步弹射物的渲染缩放值
 * <p>
 * 弹射物大小管理器据此在客户端渲染时缩放弹射物模型。
 * 缩放仅影响渲染，不改变弹射物碰撞箱与命中判定。
 */
public record ProjectileScalePayload(int entityId, float scale) implements CustomPacketPayload {
    public static final Type<ProjectileScalePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "projectile_scale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileScalePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ProjectileScalePayload::entityId,
        ByteBufCodecs.FLOAT, ProjectileScalePayload::scale,
        ProjectileScalePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ProjectileScalePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleProjectileScale(payload));
    }
}
