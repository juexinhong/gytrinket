package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorWeaponManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetInterceptorAmmoPayload(
    ItemStack ammo
) implements CustomPacketPayload {

    public static final Type<SetInterceptorAmmoPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "set_interceptor_ammo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetInterceptorAmmoPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, SetInterceptorAmmoPayload::ammo,
            SetInterceptorAmmoPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetInterceptorAmmoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                InterceptorWeaponManager.setAmmo(player.getUUID(), payload.ammo);
                InterceptorWeaponManager.refreshAllWingmen(player);
            }
        });
    }
}
