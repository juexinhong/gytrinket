package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：消耗 1 个刷新点刷新随机构建随机池
 */
public record RequestRefreshRandomPoolPayload() implements CustomPacketPayload {
    public static final Type<RequestRefreshRandomPoolPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_refresh_random_pool"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRefreshRandomPoolPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestRefreshRandomPoolPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestRefreshRandomPoolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!Config.isRandomBuildEnabled()) return;

            if (!ModLevelManager.consumeRandomPoints(player.getUUID(), 1)) {
                player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.not_enough_random_points"));
                return;
            }
            // 尽量不与上一轮重复：剔除旧池后重新生成
            NetworkHandler.sendRandomBuildPoolToPlayer(player, true);
            // 同步刷新点消耗
            NetworkHandler.sendModLevelSyncToPlayer(player);
        });
    }
}
