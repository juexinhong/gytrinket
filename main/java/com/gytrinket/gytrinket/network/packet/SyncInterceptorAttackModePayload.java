package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.attack_mode.interceptor.InterceptorWeaponClientData;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorAttackMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncInterceptorAttackModePayload(
    String attackModeName
) implements CustomPacketPayload {

    public static final Type<SyncInterceptorAttackModePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_interceptor_attack_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncInterceptorAttackModePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncInterceptorAttackModePayload decode(RegistryFriendlyByteBuf buf) {
            return new SyncInterceptorAttackModePayload(buf.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncInterceptorAttackModePayload msg) {
            buf.writeUtf(msg.attackModeName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncInterceptorAttackModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                InterceptorAttackMode mode = InterceptorAttackMode.byName(payload.attackModeName);
                InterceptorWeaponClientData.setAttackMode(mc.player.getUUID(), mode);
            }
        });
    }
}
