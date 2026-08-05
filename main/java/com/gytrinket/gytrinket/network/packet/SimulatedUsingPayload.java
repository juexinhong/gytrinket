package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.weapon.flamespear.FlameSpearManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 焰矛模拟充能状态包（客户端→服务端）。
 * <p>
 * 客户端检测右键按键状态并发送，服务端据此充能/消退，
 * 不进入真实"使用物品"状态（避免移动减速）。
 */
public record SimulatedUsingPayload(int playerId, boolean using) implements CustomPacketPayload {

    public static final Type<SimulatedUsingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "simulated_using"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SimulatedUsingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SimulatedUsingPayload decode(RegistryFriendlyByteBuf buf) {
            return new SimulatedUsingPayload(buf.readVarInt(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SimulatedUsingPayload msg) {
            buf.writeVarInt(msg.playerId);
            buf.writeBoolean(msg.using);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SimulatedUsingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FlameSpearManager.setSimulatedUsing(payload.playerId, payload.using));
    }
}
