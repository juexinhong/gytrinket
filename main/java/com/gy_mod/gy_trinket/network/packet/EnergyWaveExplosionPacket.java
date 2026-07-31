package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnergyWaveExplosionPacket {
    private double x, y, z;
    private double dirX, dirY, dirZ;
    private double splashLength;
    private int positionSyncEntityId;
    private int colorType;
    private double offsetDistance;

    public EnergyWaveExplosionPacket() {}

    public EnergyWaveExplosionPacket(double x, double y, double z, double dirX, double dirY, double dirZ, double splashLength) {
        this(x, y, z, dirX, dirY, dirZ, splashLength, -1, 0, 0.0);
    }

    public EnergyWaveExplosionPacket(double x, double y, double z, double dirX, double dirY, double dirZ, double splashLength, int positionSyncEntityId) {
        this(x, y, z, dirX, dirY, dirZ, splashLength, positionSyncEntityId, 0, 0.0);
    }

    public EnergyWaveExplosionPacket(double x, double y, double z, double dirX, double dirY, double dirZ, double splashLength, int positionSyncEntityId, int colorType) {
        this(x, y, z, dirX, dirY, dirZ, splashLength, positionSyncEntityId, colorType, 0.0);
    }

    public EnergyWaveExplosionPacket(double x, double y, double z, double dirX, double dirY, double dirZ, double splashLength, int positionSyncEntityId, int colorType, double offsetDistance) {
        this.x = x; this.y = y; this.z = z;
        this.dirX = dirX; this.dirY = dirY; this.dirZ = dirZ;
        this.splashLength = splashLength;
        this.positionSyncEntityId = positionSyncEntityId;
        this.colorType = colorType;
        this.offsetDistance = offsetDistance;
    }

    public EnergyWaveExplosionPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.dirX = buf.readDouble();
        this.dirY = buf.readDouble();
        this.dirZ = buf.readDouble();
        this.splashLength = buf.readDouble();
        this.positionSyncEntityId = buf.readInt();
        this.colorType = buf.readInt();
        this.offsetDistance = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
        buf.writeDouble(splashLength);
        buf.writeInt(positionSyncEntityId);
        buf.writeInt(colorType);
        buf.writeDouble(offsetDistance);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.addExplosionWave(
                    x, y, z, dirX, dirY, dirZ, splashLength, positionSyncEntityId, colorType, offsetDistance
                );
            });
        });
        context.setPacketHandled(true);
    }
}
