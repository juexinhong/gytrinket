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

/**
 * 特殊机制集合增删 payload（配置面板「机制+ / 机制-」Shift 按钮）。
 * <p>
 * mechanicSet 为具体机制集合名（tooltip_rules itemSet），remove=false 添加、true 移除。
 * 写入运行时覆盖文件后立即重新加载生效（不重载数据包，不触发数据包校验/安全模式）。
 */
public record ConfigSpecialMechanicPayload(String itemId, String mechanicSet, boolean remove) implements CustomPacketPayload {
    public static final Type<ConfigSpecialMechanicPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_special_mechanic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSpecialMechanicPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigSpecialMechanicPayload::itemId,
        ByteBufCodecs.STRING_UTF8, ConfigSpecialMechanicPayload::mechanicSet,
        ByteBufCodecs.BOOL, ConfigSpecialMechanicPayload::remove,
        ConfigSpecialMechanicPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigSpecialMechanicPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (ResourceLocation.tryParse(payload.itemId) == null) return;
            if (payload.mechanicSet == null || payload.mechanicSet.isEmpty()) return;

            DefsManager.updateSpecialMechanicSet(player.server, payload.itemId, payload.mechanicSet, payload.remove());

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            player.sendSystemMessage(Component.translatable("message.gytrinket.defs_applied"));
        });
    }
}
