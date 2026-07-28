package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.assault.AssaultManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AssaultAttackMessage {
    public AssaultAttackMessage() {}

    public AssaultAttackMessage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(AssaultAttackMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                AssaultManager.triggerAssault(player);
            }
        });

        context.setPacketHandled(true);
    }
}
