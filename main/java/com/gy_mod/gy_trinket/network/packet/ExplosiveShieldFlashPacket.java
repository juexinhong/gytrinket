package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosiveShieldFlashPacket {
    private double x;
    private double y;
    private double z;

    public ExplosiveShieldFlashPacket() {}

    public ExplosiveShieldFlashPacket(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ExplosiveShieldFlashPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleExplosiveShieldFlashMessage(x, y, z));
        });
        context.setPacketHandled(true);
    }
}
