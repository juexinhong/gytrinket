package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleExecuteMessage {
    public ToggleExecuteMessage() {}

    public ToggleExecuteMessage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                boolean newState = ExecuteToggleManager.toggle(player.getUUID());
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                        newState ? "message.gytrinket.execute_enabled" : "message.gytrinket.execute_disabled"
                    ), true
                );
            }
        });

        context.setPacketHandled(true);
    }
}
