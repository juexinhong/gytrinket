package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleExecuteMessage {
    public ToggleExecuteMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public ToggleExecuteMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                boolean newState = ExecuteToggleManager.toggle(player.getUUID());
                player.displayClientMessage(
                    Component.translatable(
                        newState ? "message.gytrinket.execute_enabled" : "message.gytrinket.execute_disabled"
                    ), true
                );
            }
        });
        context.setPacketHandled(true);
    }
}
