package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.modifier.player.movement.MovementSpeedMultiplierCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：同步玩家速度聚合乘数到客户端
 * <p>
 * 游泳速度乘数在客户端 travel（本地权威移动）中消费，
 * 服务端在聚合乘数变化时发送本包（无 UUID 字段：接收者即目标玩家）。
 */
public class SyncMovementSpeedMultiplierMessage {
    private double multiplier;

    public SyncMovementSpeedMultiplierMessage() {}

    public SyncMovementSpeedMultiplierMessage(double multiplier) {
        this.multiplier = multiplier;
    }

    public SyncMovementSpeedMultiplierMessage(FriendlyByteBuf buf) {
        this.multiplier = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(multiplier);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                MovementSpeedMultiplierCache.set(player.getUUID(), multiplier);
            }
        });
        context.setPacketHandled(true);
    }
}
