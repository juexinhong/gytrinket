package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 同步幽灵机身隐身进度到客户端
 * 广播给所有追踪该玩家的客户端，使其他玩家也能看到隐身效果
 */
public class SyncGhostStealthMessage {
    private int entityId;
    private float stealthProgress;

    public SyncGhostStealthMessage() {}

    public SyncGhostStealthMessage(int entityId, float stealthProgress) {
        this.entityId = entityId;
        this.stealthProgress = stealthProgress;
    }

    public SyncGhostStealthMessage(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.stealthProgress = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(stealthProgress);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageClientData.setStealthProgress(entityId, stealthProgress);
            });
        });
        context.setPacketHandled(true);
    }
}
