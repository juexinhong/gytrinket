package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestShieldCooldownPayload() implements CustomPacketPayload {
    public static final Type<RequestShieldCooldownPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "request_shield_cooldown"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestShieldCooldownPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestShieldCooldownPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestShieldCooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                double maxShield = ShieldManager.getMaxShield(player.getUUID());
                // 复用统一的同步消息构建逻辑（与 sendShieldSyncToPlayer 同一实现）
                PacketDistributor.sendToPlayer(player,
                    NetworkHandler.buildShieldSyncMessage(player, currentShield, maxShield));
            }
        });
    }
}
