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

public record ConfigRemoveAttrPayload(String itemId, String attributeName) implements CustomPacketPayload {
    public static final Type<ConfigRemoveAttrPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_remove_attr"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigRemoveAttrPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigRemoveAttrPayload::itemId,
        ByteBufCodecs.STRING_UTF8, ConfigRemoveAttrPayload::attributeName,
        ConfigRemoveAttrPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigRemoveAttrPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            // 从覆写层移除单条属性（剩余集整体写回；显式空 = 覆盖为无属性）
            DefsManager.removeItemAttribute(player.server, payload.itemId, payload.attributeName);

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
    }
}
