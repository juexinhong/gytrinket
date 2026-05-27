package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShieldParticlePacket {
    
    private final double x, y, z;
    private final double dirX, dirY, dirZ;
    private final double originX, originY, originZ;
    
    public ShieldParticlePacket(double x, double y, double z, 
                               double dirX, double dirY, double dirZ,
                               double originX, double originY, double originZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }
    
    public ShieldParticlePacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.dirX = buf.readDouble();
        this.dirY = buf.readDouble();
        this.dirZ = buf.readDouble();
        this.originX = buf.readDouble();
        this.originY = buf.readDouble();
        this.originZ = buf.readDouble();
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
    }
    
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleRenderManager.getInstance()
                .addParticle(x, y, z, dirX, dirY, dirZ, originX, originY, originZ);
        });
        context.get().setPacketHandled(true);
    }
}