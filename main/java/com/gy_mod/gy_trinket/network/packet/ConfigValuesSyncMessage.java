package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.client.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S->C 全量配置项同步（不含客户端专属项）。
 * 客户端应用内存值；openScreen 为 true 时同时打开"配置项"界面。
 */
public class ConfigValuesSyncMessage {
    public final List<String> ids;
    public final List<Double> values;
    public final boolean openScreen;

    public ConfigValuesSyncMessage(List<String> ids, List<Double> values, boolean openScreen) {
        this.ids = ids;
        this.values = values;
        this.openScreen = openScreen;
    }

    public ConfigValuesSyncMessage(FriendlyByteBuf buf) {
        int idCount = buf.readVarInt();
        this.ids = new ArrayList<>(idCount);
        for (int i = 0; i < idCount; i++) {
            this.ids.add(buf.readUtf());
        }
        int valueCount = buf.readVarInt();
        this.values = new ArrayList<>(valueCount);
        for (int i = 0; i < valueCount; i++) {
            this.values.add(buf.readDouble());
        }
        this.openScreen = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id);
        }
        buf.writeVarInt(values.size());
        for (Double value : values) {
            buf.writeDouble(value);
        }
        buf.writeBoolean(openScreen);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ClientPacketHandler.handleConfigValuesSync(this);
        });
        context.setPacketHandled(true);
    }
}
