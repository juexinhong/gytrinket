package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.ExecuteToggleManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleExecutePayload() implements CustomPacketPayload {
    public static final Type<ToggleExecutePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "toggle_execute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleExecutePayload> STREAM_CODEC =
        StreamCodec.unit(new ToggleExecutePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleExecutePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean newState = ExecuteToggleManager.toggle(player.getUUID());
                player.displayClientMessage(
                    Component.translatable(
                        newState ? "message.gytrinket.execute_enabled" : "message.gytrinket.execute_disabled"
                    ), true
                );
            }
        });
    }
}
