package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LightningRenderMessage {
    private List<double[]> segments;
    private int duration;
    private float maxWidth;

    public LightningRenderMessage() {
        this.segments = new ArrayList<>();
        this.duration = 1;
        this.maxWidth = -1.0f;
    }

    public LightningRenderMessage(List<ElectricDischargeManager.LightningSegment> segments) {
        this(segments, 6, -1.0f);
    }

    public LightningRenderMessage(List<ElectricDischargeManager.LightningSegment> segments, int duration, float maxWidth) {
        this.segments = new ArrayList<>();
        for (var segment : segments) {
            this.segments.add(new double[] {
                segment.start().x, segment.start().y, segment.start().z,
                segment.end().x, segment.end().y, segment.end().z
            });
        }
        this.duration = duration;
        this.maxWidth = maxWidth;
    }

    public LightningRenderMessage(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.duration = buf.readInt();
        this.maxWidth = buf.readFloat();
        this.segments = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double[] segment = new double[6];
            for (int j = 0; j < 6; j++) {
                segment[j] = buf.readDouble();
            }
            this.segments.add(segment);
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(segments.size());
        buf.writeInt(duration);
        buf.writeFloat(maxWidth);
        for (double[] segment : segments) {
            for (double value : segment) {
                buf.writeDouble(value);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                List<ElectricDischargeManager.LightningSegment> lightningSegments = new ArrayList<>();
                for (double[] segment : segments) {
                    lightningSegments.add(new ElectricDischargeManager.LightningSegment(
                        new Vec3(segment[0], segment[1], segment[2]),
                        new Vec3(segment[3], segment[4], segment[5])
                    ));
                }
                com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.client.LightningRenderManager.addLightning(lightningSegments, duration, maxWidth);
            });
        });
        context.setPacketHandled(true);
    }
}
