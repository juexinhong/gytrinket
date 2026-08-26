package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncShieldMessage {
    private double currentShield;
    private double maxShield;
    private int currentCooldown;
    private int maxCooldown;
    private double adaptiveArmorReduction;
    private int siphonStacks;
    private double shieldEffectRadius;
    private int[] protectedEntityIds;
    private boolean auraDamaging;
    private double amplificationProgress;

    public SyncShieldMessage() {}

    public SyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius, int[] protectedEntityIds, boolean auraDamaging, double amplificationProgress) {
        this.currentShield = currentShield;
        this.maxShield = maxShield;
        this.currentCooldown = currentCooldown;
        this.maxCooldown = maxCooldown;
        this.adaptiveArmorReduction = adaptiveArmorReduction;
        this.siphonStacks = siphonStacks;
        this.shieldEffectRadius = shieldEffectRadius;
        this.protectedEntityIds = protectedEntityIds;
        this.auraDamaging = auraDamaging;
        this.amplificationProgress = amplificationProgress;
    }

    public SyncShieldMessage(FriendlyByteBuf buf) {
        this.currentShield = buf.readDouble();
        this.maxShield = buf.readDouble();
        this.currentCooldown = buf.readInt();
        this.maxCooldown = buf.readInt();
        this.adaptiveArmorReduction = buf.readDouble();
        this.siphonStacks = buf.readInt();
        this.shieldEffectRadius = buf.readDouble();
        this.protectedEntityIds = buf.readVarIntArray();
        this.auraDamaging = buf.readBoolean();
        this.amplificationProgress = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(currentShield);
        buf.writeDouble(maxShield);
        buf.writeInt(currentCooldown);
        buf.writeInt(maxCooldown);
        buf.writeDouble(adaptiveArmorReduction);
        buf.writeInt(siphonStacks);
        buf.writeDouble(shieldEffectRadius);
        buf.writeVarIntArray(protectedEntityIds);
        buf.writeBoolean(auraDamaging);
        buf.writeDouble(amplificationProgress);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                    currentShield, maxShield, currentCooldown, maxCooldown,
                    adaptiveArmorReduction, siphonStacks, shieldEffectRadius,
                    protectedEntityIds, auraDamaging, amplificationProgress));
        });
        context.setPacketHandled(true);
    }
}
