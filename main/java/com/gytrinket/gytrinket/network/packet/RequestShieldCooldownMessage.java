package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
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
                double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                double maxShield = ShieldManager.getMaxShield(player.getUUID());
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    NetworkHandler.buildShieldSyncMessage(player, currentShield, maxShield));
            }
        });
        context.setPacketHandled(true);
    }
}
