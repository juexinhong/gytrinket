package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C->S 请求全量配置项同步（打开"配置项"界面时）。权限：需 2 级（管理员）。 */
public record ConfigValuesRequestPayload() implements CustomPacketPayload {
    public static final Type<ConfigValuesRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_values_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigValuesRequestPayload> STREAM_CODEC =
        StreamCodec.unit(new ConfigValuesRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigValuesRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) return;
                NetworkHandler.sendConfigValuesToPlayer(player, true);
            }
        });
    }
}
