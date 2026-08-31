package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.menu.LightPointCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C->S：请求整理光点核心容器（界面内鼠标中键触发）
 * 服务端对当前打开的光点核心菜单执行创造物品栏顺序排序整理
 */
public record SortLightPointCorePayload() implements CustomPacketPayload {
    public static final Type<SortLightPointCorePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sort_light_point_core"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SortLightPointCorePayload> STREAM_CODEC =
        StreamCodec.unit(new SortLightPointCorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SortLightPointCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof LightPointCoreMenu menu) {
                menu.sortContainer();
                menu.broadcastChanges();
            }
        });
    }
}
