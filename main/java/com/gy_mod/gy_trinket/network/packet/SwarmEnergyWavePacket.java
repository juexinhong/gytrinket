package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwarmEnergyWavePacket {
    private int entityId;
    private double x, y, z;
    private double dirX, dirY, dirZ;
    private boolean isRepair;

    public SwarmEnergyWavePacket() {}

    public SwarmEnergyWavePacket(int entityId, double x, double y, double z, double dirX, double dirY, double dirZ, boolean isRepair) {
        this.entityId = entityId;
        this.x = x; this.y = y; this.z = z;
        this.dirX = dirX; this.dirY = dirY; this.dirZ = dirZ;
        this.isRepair = isRepair;
    }

    public SwarmEnergyWavePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.x = buf.readDouble(); this.y = buf.readDouble(); this.z = buf.readDouble();
        this.dirX = buf.readDouble(); this.dirY = buf.readDouble(); this.dirZ = buf.readDouble();
        this.isRepair = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
        buf.writeBoolean(isRepair);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.addSwarmWave(
                    entityId, x, y, z, dirX, dirY, dirZ, isRepair
                ));
        });
        context.setPacketHandled(true);
    }
}
