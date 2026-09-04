package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.client.network.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S->C 全量配置项同步（不含客户端专属项）。
 * 客户端应用内存值；openScreen 为 true 时同时打开"配置项"界面。
 */
public record ConfigValuesSyncPayload(List<String> ids, List<Double> values, boolean openScreen) implements CustomPacketPayload {
    public static final Type<ConfigValuesSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_values_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigValuesSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigValuesSyncPayload decode(RegistryFriendlyByteBuf buf) {
            int idCount = buf.readVarInt();
            List<String> ids = new ArrayList<>(idCount);
            for (int i = 0; i < idCount; i++) {
                ids.add(buf.readUtf());
            }
            int valueCount = buf.readVarInt();
            List<Double> values = new ArrayList<>(valueCount);
            for (int i = 0; i < valueCount; i++) {
                values.add(buf.readDouble());
            }
            boolean openScreen = buf.readBoolean();
            return new ConfigValuesSyncPayload(ids, values, openScreen);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ConfigValuesSyncPayload msg) {
            buf.writeVarInt(msg.ids.size());
            for (String id : msg.ids) {
                buf.writeUtf(id);
            }
            buf.writeVarInt(msg.values.size());
            for (Double value : msg.values) {
                buf.writeDouble(value);
            }
            buf.writeBoolean(msg.openScreen);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigValuesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleConfigValuesSync(payload);
        });
    }
}
