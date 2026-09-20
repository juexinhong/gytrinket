package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigUpdatePayload(String itemId, String attributeName, double value) implements CustomPacketPayload {
    public static final Type<ConfigUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigUpdatePayload::itemId,
        ByteBufCodecs.STRING_UTF8, ConfigUpdatePayload::attributeName,
        ByteBufCodecs.DOUBLE, ConfigUpdatePayload::value,
        ConfigUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            // 属性写回覆写层（权威 + 覆写合并基线上 upsert，立即落盘生效）
            DefsManager.updateItemAttribute(player.server, payload.itemId, payload.attributeName, payload.value);

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
    }
}
