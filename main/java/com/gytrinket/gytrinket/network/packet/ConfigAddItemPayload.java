package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
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

public record ConfigAddItemPayload(String itemId) implements CustomPacketPayload {
    public static final Type<ConfigAddItemPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_add_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigAddItemPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigAddItemPayload::itemId,
        ConfigAddItemPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigAddItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            if (payload.itemId != null && !payload.itemId.isEmpty() && !payload.itemId.equals("minecraft:air")) {
                if (!AttributeManager.isItemAttributeRegistered(payload.itemId)) {
                    Map<String, Double> attrs = new HashMap<>();
                    AttributeManager.registerItemAttributes(payload.itemId, attrs);
                    Config.saveItemAttributesConfig();
                }
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
    }
}
