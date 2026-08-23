package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 服务端 -> 客户端：随机构建随机池（最多 9 个物品 id）
 */
public record ResponseRandomBuildPayload(List<String> itemIds) implements CustomPacketPayload {
    public static final Type<ResponseRandomBuildPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "response_random_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResponseRandomBuildPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ResponseRandomBuildPayload::itemIds,
        ResponseRandomBuildPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResponseRandomBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleResponseRandomBuild(payload));
    }
}
