package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncGhostStealthMessage {
    private int entityId;
    private float stealthProgress;

    public SyncGhostStealthMessage() {}

    public SyncGhostStealthMessage(int entityId, float stealthProgress) {
        this.entityId = entityId;
        this.stealthProgress = stealthProgress;
    }

    public SyncGhostStealthMessage(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.stealthProgress = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(stealthProgress);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageClientData.setStealthProgress(
                    entityId, stealthProgress));
        });
        context.setPacketHandled(true);
    }
}
