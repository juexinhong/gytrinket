package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChargedSweepParticleMessage {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float scale;
    private final long gameTime;
    private final int lifetime;

    public ChargedSweepParticleMessage(double x, double y, double z,
                                       float yaw, float pitch, float scale,
                                       long gameTime, int lifetime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.gameTime = gameTime;
        this.lifetime = lifetime;
    }

    public ChargedSweepParticleMessage(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
        this.scale = buf.readFloat();
        this.gameTime = buf.readVarLong();
        this.lifetime = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(scale);
        buf.writeVarLong(gameTime);
        buf.writeVarInt(lifetime);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedSweepRenderer.addSweep(
                    new com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedSweepRenderData(
                        x, y, z,
                        yaw, pitch,
                        scale,
                        gameTime, lifetime
                    )
                );
            });
        });
        context.setPacketHandled(true);
    }
}
