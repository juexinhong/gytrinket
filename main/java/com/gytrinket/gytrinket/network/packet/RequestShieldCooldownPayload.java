package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
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
                int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
                int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
                double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                double maxShield = ShieldManager.getMaxShield(player.getUUID());
                double adaptiveArmorReduction = com.gytrinket.gytrinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
                int siphonStacks = com.gytrinket.gytrinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
                double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
                int[] protectedEntityIds = com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
                boolean auraDamaging = com.gytrinket.gytrinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
                PacketDistributor.sendToPlayer(player,
                    new SyncShieldPayload(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging));
            }
        });
    }
}
