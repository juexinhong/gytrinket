package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncGhostMoveSpeedMessage {
    private float moveReduction;

    public SyncGhostMoveSpeedMessage() {}

    public SyncGhostMoveSpeedMessage(float moveReduction) {
        this.moveReduction = moveReduction;
    }

    public SyncGhostMoveSpeedMessage(FriendlyByteBuf buf) {
        this.moveReduction = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(moveReduction);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                GhostFuselageManager.setSyncedMoveReduction(
                    player.getUUID(), moveReduction);
            }
        });
        context.setPacketHandled(true);
    }
}
