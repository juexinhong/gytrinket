package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.shield.type.AmplificationShieldType;
import com.gy_mod.gy_trinket.core.shield.type.AuraShieldType;
import com.gy_mod.gy_trinket.core.shield.type.SiphonShieldType;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestShieldCooldownMessage {
    public RequestShieldCooldownMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestShieldCooldownMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
                int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
                double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                double maxShield = ShieldManager.getMaxShield(player.getUUID());
                double adaptiveArmorReduction = AdaptiveArmorManager.calculateDamageReduction(player);
                int siphonStacks = SiphonShieldType.getSiphonStacks(player.getUUID());
                double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
                int[] protectedEntityIds = ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
                boolean auraDamaging = AuraShieldType.isAuraDamaging(player.getUUID());
                double amplificationProgress = AmplificationShieldType.getProgress(player.getUUID());
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging, amplificationProgress));
            }
        });
        context.setPacketHandled(true);
    }
}
