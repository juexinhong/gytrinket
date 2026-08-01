package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneArrayManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwitchDroneArrayPayload() implements CustomPacketPayload {
    public static final Type<SwitchDroneArrayPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "switch_drone_array"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchDroneArrayPayload> STREAM_CODEC =
        StreamCodec.unit(new SwitchDroneArrayPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwitchDroneArrayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DroneArrayManager.getInstance().switchToNextArray(player);
            }
        });
    }
}
