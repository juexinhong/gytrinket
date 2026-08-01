package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorAttackMode;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

public record SetInterceptorAttackModePayload(
    String attackModeName
) implements CustomPacketPayload {

    public static final Type<SetInterceptorAttackModePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "set_interceptor_attack_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetInterceptorAttackModePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SetInterceptorAttackModePayload decode(RegistryFriendlyByteBuf buf) {
            return new SetInterceptorAttackModePayload(buf.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SetInterceptorAttackModePayload msg) {
            buf.writeUtf(msg.attackModeName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetInterceptorAttackModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                InterceptorAttackMode mode = InterceptorAttackMode.byName(payload.attackModeName);
                InterceptorWeaponManager.setAttackMode(player.getUUID(), mode);
                com.gytrinket.gytrinket.network.NetworkHandler.sendInterceptorAttackModeToPlayer(player, mode);

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
