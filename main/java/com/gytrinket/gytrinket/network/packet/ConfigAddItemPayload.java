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
                // 添加物品 = 确保覆写层条目存在（空属性接管；已有权威/覆写属性时幂等忽略）
                DefsManager.ensureItemOverrideEntry(player.server, payload.itemId);

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
    }
}
