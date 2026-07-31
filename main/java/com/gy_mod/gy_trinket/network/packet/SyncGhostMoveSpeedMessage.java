package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：同步移动导致的隐身消耗量
 * <p>
 * 客户端计算真实位移速度和消耗量，仅发送消耗值以减少网络压力。
 * 值相同时不发送（客户端缓存上次发送值）。
 */
public class SyncGhostMoveSpeedMessage {
    /** 移动导致的隐身进度消耗量（0.0~1.0） */
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                GhostFuselageManager.setSyncedMoveReduction(player.getUUID(), moveReduction);
            }
        });
        context.setPacketHandled(true);
    }
}
