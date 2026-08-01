package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

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

            ItemAttributeConfig config = AttributeManager.getItemAttributes(payload.itemId);
            if (config != null) {
                config.addAttribute(payload.attributeName, payload.value);
            } else {
                Map<String, Double> attrs = new HashMap<>();
                attrs.put(payload.attributeName, payload.value);
                AttributeManager.registerItemAttributes(payload.itemId, attrs);
            }

            Config.saveItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
    }
}
