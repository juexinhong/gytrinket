package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

public record SetInterceptorWeaponPayload(
    ItemStack weapon
) implements CustomPacketPayload {

    public static final Type<SetInterceptorWeaponPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "set_interceptor_weapon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetInterceptorWeaponPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, SetInterceptorWeaponPayload::weapon,
            SetInterceptorWeaponPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetInterceptorWeaponPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                InterceptorWeaponManager.setWeapon(player.getUUID(), payload.weapon);
                com.gytrinket.gytrinket.network.NetworkHandler.sendInterceptorWeaponToPlayer(player, payload.weapon);

                Map<UUID, Entity> entities = ConstructManager.getInstance()
                    .getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);
                for (Entity entity : entities.values()) {
                    if (entity instanceof WingmanConstructEntity wingman) {
                        wingman.refreshInterceptorData();
                    }
                }
            }
        });
    }
}
