package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AttackStateMessage {
    private int stateOrdinal;
    private int holdTicks;

    public AttackStateMessage() {}

    public AttackStateMessage(int stateOrdinal, int holdTicks) {
        this.stateOrdinal = stateOrdinal;
        this.holdTicks = holdTicks;
    }

    public AttackStateMessage(FriendlyByteBuf buf) {
        this.stateOrdinal = buf.readVarInt();
        this.holdTicks = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(stateOrdinal);
        buf.writeVarInt(holdTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                AttackStateManager.updatePlayerState(
                    player.getUUID(), stateOrdinal, holdTicks);
            }
        });
        context.setPacketHandled(true);
    }
}
