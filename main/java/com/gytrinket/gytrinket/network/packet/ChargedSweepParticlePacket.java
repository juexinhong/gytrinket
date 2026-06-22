package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedSweepRenderData;
import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedSweepRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S->C: 充能横扫粒子渲染数据包
 * <p>
 * 服务端 sweepAttack() 只在服务端被调用，客户端无法直接获取渲染数据。
 * 通过此网络包将渲染数据发送到客户端。
 */
public record ChargedSweepParticlePacket(
    double x, double y, double z,
    float yaw, float pitch, float scale,
    long gameTime, int lifetime
) implements CustomPacketPayload {

    public static final Type<ChargedSweepParticlePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "charged_sweep_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChargedSweepParticlePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChargedSweepParticlePacket decode(RegistryFriendlyByteBuf buf) {
            return new ChargedSweepParticlePacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarLong(), buf.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ChargedSweepParticlePacket msg) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeFloat(msg.yaw);
            buf.writeFloat(msg.pitch);
            buf.writeFloat(msg.scale);
            buf.writeVarLong(msg.gameTime);
            buf.writeVarInt(msg.lifetime);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChargedSweepParticlePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChargedSweepRenderer.addSweep(new ChargedSweepRenderData(
                payload.x, payload.y, payload.z,
                payload.yaw, payload.pitch,
                payload.scale,
                payload.gameTime, payload.lifetime
            ));
        });
    }
}
