package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShieldParticlePacket {
    
    private final int entityId;
    private final double originOffsetX, originOffsetY, originOffsetZ;
    private final double offsetX, offsetY, offsetZ;
    private final double dirX, dirY, dirZ;
    private final int delayTicks;
    
    public ShieldParticlePacket(int entityId,
                               double originOffsetX, double originOffsetY, double originOffsetZ,
                               double offsetX, double offsetY, double offsetZ,
                               double dirX, double dirY, double dirZ,
                               int delayTicks) {
        this.entityId = entityId;
        this.originOffsetX = originOffsetX;
        this.originOffsetY = originOffsetY;
        this.originOffsetZ = originOffsetZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.delayTicks = delayTicks;
    }
    
    public ShieldParticlePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.originOffsetX = buf.readDouble();
        this.originOffsetY = buf.readDouble();
        this.originOffsetZ = buf.readDouble();
        this.offsetX = buf.readDouble();
        this.offsetY = buf.readDouble();
        this.offsetZ = buf.readDouble();
        this.dirX = buf.readDouble();
        this.dirY = buf.readDouble();
        this.dirZ = buf.readDouble();
        this.delayTicks = buf.readVarInt();
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeDouble(originOffsetX);
        buf.writeDouble(originOffsetY);
        buf.writeDouble(originOffsetZ);
        buf.writeDouble(offsetX);
        buf.writeDouble(offsetY);
        buf.writeDouble(offsetZ);
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
        buf.writeVarInt(delayTicks);
    }
    
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if (delayTicks > 0) {
                com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleTimerManager.getInstance()
                    .addPendingParticle(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks);
            } else {
                com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleRenderManager.getInstance()
                    .addParticle(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ);
            }
        });
        context.get().setPacketHandled(true);
    }
}
