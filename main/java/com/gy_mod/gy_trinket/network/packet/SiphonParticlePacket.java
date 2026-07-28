package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SiphonParticlePacket {
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetHeight;
    private double playerHeadX;
    private double playerHeadY;
    private double playerHeadZ;

    public SiphonParticlePacket() {}

    public SiphonParticlePacket(double targetX, double targetY, double targetZ, double targetHeight,
                                double playerHeadX, double playerHeadY, double playerHeadZ) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.targetHeight = targetHeight;
        this.playerHeadX = playerHeadX;
        this.playerHeadY = playerHeadY;
        this.playerHeadZ = playerHeadZ;
    }

    public SiphonParticlePacket(FriendlyByteBuf buf) {
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();
        this.targetHeight = buf.readDouble();
        this.playerHeadX = buf.readDouble();
        this.playerHeadY = buf.readDouble();
        this.playerHeadZ = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeDouble(targetHeight);
        buf.writeDouble(playerHeadX);
        buf.writeDouble(playerHeadY);
        buf.writeDouble(playerHeadZ);
    }

    public static void handle(SiphonParticlePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSiphonParticlesMessage(
                    msg.targetX, msg.targetY, msg.targetZ, msg.targetHeight,
                    msg.playerHeadX, msg.playerHeadY, msg.playerHeadZ
                );
            });
        });

        context.setPacketHandled(true);
    }
}
