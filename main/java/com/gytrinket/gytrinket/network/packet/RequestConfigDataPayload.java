package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestConfigDataPayload() implements CustomPacketPayload {
    public static final Type<RequestConfigDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_config_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestConfigDataPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestConfigDataPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestConfigDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) return;
                // 打开配置界面：先热重载磁盘覆盖文件（分层 override_layer_N.json + UI 文件），保证界面读取磁盘最新状态
                com.gytrinket.gytrinket.core.defs.DefsManager.applyOverrides(player.server);
                NetworkHandler.sendConfigDataToPlayer(player);
                NetworkHandler.sendDefsOverridesToPlayer(player);
            }
        });
    }
}
