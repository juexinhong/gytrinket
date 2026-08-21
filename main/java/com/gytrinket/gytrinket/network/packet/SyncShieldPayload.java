package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncShieldPayload(double currentShield, double maxShield, int currentCooldown, int maxCooldown,
                                 double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius,
                                 int[] protectedEntityIds, boolean auraDamaging, double amplificationProgress) implements CustomPacketPayload {
    public static final Type<SyncShieldPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_shield"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncShieldPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncShieldPayload decode(RegistryFriendlyByteBuf buf) {
            return new SyncShieldPayload(
                buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readInt(),
                buf.readDouble(), buf.readInt(),
                buf.readDouble(),
                buf.readVarIntArray(),
                buf.readBoolean(),
                buf.readDouble()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncShieldPayload msg) {
            buf.writeDouble(msg.currentShield);
            buf.writeDouble(msg.maxShield);
            buf.writeInt(msg.currentCooldown);
            buf.writeInt(msg.maxCooldown);
            buf.writeDouble(msg.adaptiveArmorReduction);
            buf.writeInt(msg.siphonStacks);
            buf.writeDouble(msg.shieldEffectRadius);
            buf.writeVarIntArray(msg.protectedEntityIds);
            buf.writeBoolean(msg.auraDamaging);
            buf.writeDouble(msg.amplificationProgress);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncShieldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                payload.currentShield, payload.maxShield, payload.currentCooldown, payload.maxCooldown,
                payload.adaptiveArmorReduction, payload.siphonStacks, payload.shieldEffectRadius,
                payload.protectedEntityIds, payload.auraDamaging, payload.amplificationProgress);
        });
    }
}
