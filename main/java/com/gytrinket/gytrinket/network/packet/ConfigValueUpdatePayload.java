package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.config.ConfigValueRegistry;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C->S 修改单个配置项（"配置项"界面编辑提交）。
 * 服务端白名单校验 + 范围钳制后应用并落盘，随后广播全量同步刷新所有客户端。
 * 权限：需 2 级（管理员）。
 */
public record ConfigValueUpdatePayload(String id, double value) implements CustomPacketPayload {
    public static final Type<ConfigValueUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_value_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigValueUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ConfigValueUpdatePayload::id,
        ByteBufCodecs.DOUBLE, ConfigValueUpdatePayload::value,
        ConfigValueUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigValueUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            if (!ConfigValueRegistry.applyServer(payload.id, payload.value)) return;

            Config.SPEC.save();

            NetworkHandler.sendConfigValuesToAllPlayers(player, false);
        });
    }
}
