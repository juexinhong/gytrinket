package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReflectParticlePacket {
    private double x;
    private double y;
    private double z;
    private double dirX;
    private double dirY;
    private double dirZ;
    private int particleCount;
    private double maxAngleDegrees;
    private double speedMultiplier;

    public ReflectParticlePacket() {}

    public ReflectParticlePacket(double x, double y, double z, double dirX, double dirY, double dirZ,
                                 int particleCount, double maxAngleDegrees, double speedMultiplier) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.particleCount = particleCount;
        this.maxAngleDegrees = maxAngleDegrees;
        this.speedMultiplier = speedMultiplier;
    }

    public ReflectParticlePacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.dirX = buf.readDouble();
        this.dirY = buf.readDouble();
        this.dirZ = buf.readDouble();
        this.particleCount = buf.readInt();
        this.maxAngleDegrees = buf.readDouble();
        this.speedMultiplier = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
        buf.writeInt(particleCount);
        buf.writeDouble(maxAngleDegrees);
        buf.writeDouble(speedMultiplier);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleReflectParticlesMessage(
                    x, y, z, dirX, dirY, dirZ, particleCount, maxAngleDegrees, speedMultiplier);
            });
        });

        context.setPacketHandled(true);
    }
}
