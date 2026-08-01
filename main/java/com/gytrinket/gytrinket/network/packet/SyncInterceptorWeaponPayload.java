package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.attack_mode.interceptor.InterceptorWeaponClientData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncInterceptorWeaponPayload(
    ItemStack weapon
) implements CustomPacketPayload {

    public static final Type<SyncInterceptorWeaponPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_interceptor_weapon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncInterceptorWeaponPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, SyncInterceptorWeaponPayload::weapon,
            SyncInterceptorWeaponPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncInterceptorWeaponPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                InterceptorWeaponClientData.setWeapon(mc.player.getUUID(), payload.weapon);
            }
        });
    }
}
