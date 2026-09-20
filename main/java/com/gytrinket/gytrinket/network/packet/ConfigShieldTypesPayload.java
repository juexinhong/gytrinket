package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 物品护盾类型定义 payload（配置面板「护盾类型」按钮）。
 * <p>
 * 写入运行时覆盖文件后立即重新加载生效（不重载数据包），types 为空列表表示移除该物品的全部护盾类型。
 */
public record ConfigShieldTypesPayload(String itemId, List<String> types) implements CustomPacketPayload {
    public static final Type<ConfigShieldTypesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_shield_types"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigShieldTypesPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigShieldTypesPayload::itemId,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ConfigShieldTypesPayload::types,
        ConfigShieldTypesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigShieldTypesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (ResourceLocation.tryParse(payload.itemId) == null) return;

            DefsManager.updateShieldTypeOverride(player.server, payload.itemId, payload.types());

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            player.sendSystemMessage(Component.translatable("message.gytrinket.defs_applied"));
        });
    }
}
