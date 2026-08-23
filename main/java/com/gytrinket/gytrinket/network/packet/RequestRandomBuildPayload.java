package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.random_build.RandomBuildManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：请求随机构建随机池
 * 已有持久化随机池时直接返回（重进游戏后保持），否则生成新池
 */
public record RequestRandomBuildPayload() implements CustomPacketPayload {
    public static final Type<RequestRandomBuildPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_random_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRandomBuildPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestRandomBuildPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestRandomBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (RandomBuildManager.hasStoredPool(player.getUUID())) {
                PacketDistributor.sendToPlayer(player,
                    new ResponseRandomBuildPayload(RandomBuildManager.getCurrentPool(player.getUUID())));
            } else {
                NetworkHandler.sendRandomBuildPoolToPlayer(player);
            }
        });
    }
}
