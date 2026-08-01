package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.client.LightningRenderManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LightningRenderPayload(List<double[]> segments, int duration, float maxWidth) implements CustomPacketPayload {
    public static final Type<LightningRenderPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "lightning_render"));

    public static LightningRenderPayload fromSegments(List<ElectricDischargeManager.LightningSegment> segments) {
        return fromSegments(segments, 8, -1.0f);
    }

    public static LightningRenderPayload fromSegments(List<ElectricDischargeManager.LightningSegment> segments, int duration, float maxWidth) {
        return new LightningRenderPayload(segments.stream().map(segment -> new double[] {
            segment.start().x, segment.start().y, segment.start().z,
            segment.end().x, segment.end().y, segment.end().z
        }).toList(), duration, maxWidth);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LightningRenderPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LightningRenderPayload decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readInt();
            int duration = buf.readInt();
            float maxWidth = buf.readFloat();
            List<double[]> segments = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                double[] segment = new double[6];
                for (int j = 0; j < 6; j++) {
                    segment[j] = buf.readDouble();
                }
                segments.add(segment);
            }
            return new LightningRenderPayload(segments, duration, maxWidth);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LightningRenderPayload msg) {
            buf.writeInt(msg.segments.size());
            buf.writeInt(msg.duration);
            buf.writeFloat(msg.maxWidth);
            for (double[] segment : msg.segments) {
                for (double value : segment) {
                    buf.writeDouble(value);
                }
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LightningRenderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<ElectricDischargeManager.LightningSegment> lightningSegments = new ArrayList<>();
            for (double[] segment : payload.segments) {
                lightningSegments.add(new ElectricDischargeManager.LightningSegment(
                    new Vec3(segment[0], segment[1], segment[2]),
                    new Vec3(segment[3], segment[4], segment[5])
                ));
            }
            LightningRenderManager.addLightning(lightningSegments, payload.duration, payload.maxWidth);
        });
    }
}
