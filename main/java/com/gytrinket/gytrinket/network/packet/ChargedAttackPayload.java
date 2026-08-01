package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ChargedAttackPayload(int action) implements CustomPacketPayload {
    public static final Type<ChargedAttackPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "charged_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChargedAttackPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ChargedAttackPayload::action,
        ChargedAttackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChargedAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            UUID uuid = player.getUUID();

            if (!ChargedAttackManager.hasChargedAttack(player)) {
                return;
            }

            switch (payload.action) {
                case 0 -> {
                    ChargedAttackManager.startCharging(uuid);
                }
                case 1 -> {
                    ChargedAttackManager.updateCharging(uuid, player);
                }
                case 2 -> {
                    ChargedAttackManager.releaseCharge(uuid);
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                }
                case 3 -> {
                    ChargedAttackManager.cancelCharging(uuid);
                    com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker.removePlayer(uuid);
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                }
                case 4 -> {
                    double chargeValue = ChargedAttackManager.getChargeValue(player);
                    ChargedAttackManager.releaseCharge(uuid);
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                    ChargedAttackSweepHandler.executeChargedSweepAttack(player, chargeValue);
                }
            }
        });
    }
}
