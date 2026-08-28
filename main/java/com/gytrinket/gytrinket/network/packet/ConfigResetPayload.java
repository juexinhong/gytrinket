package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigResetPayload() implements CustomPacketPayload {
    public static final Type<ConfigResetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_reset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigResetPayload> STREAM_CODEC =
        StreamCodec.unit(new ConfigResetPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigResetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            // 重置运行时覆盖（特殊机制/护盾类型），恢复数据包默认定义
            DefsManager.resetOverrides(player.server);

            AttributeManager.resetToDefaults();
            Config.resetItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
            // 同步空覆盖层到所有客户端，面板/提示恢复默认显示
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
        });
    }
}
