package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncChargedAttackMessage {
    private double chargeValue;
    private double chargedDamage;

    public SyncChargedAttackMessage() {}

    public SyncChargedAttackMessage(double chargeValue, double chargedDamage) {
        this.chargeValue = chargeValue;
        this.chargedDamage = chargedDamage;
    }

    public SyncChargedAttackMessage(FriendlyByteBuf buf) {
        this.chargeValue = buf.readDouble();
        this.chargedDamage = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(chargeValue);
        buf.writeDouble(chargedDamage);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedAttackHudRenderer.setChargeValue(chargeValue, chargedDamage);
                if (chargeValue == 0) {
                    com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedAttackInputHandler.resetChargeFromSync();
                }
            });
        });
        context.setPacketHandled(true);
    }
}
