package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigUpdateMessage {
    private String itemId;
    private String attributeName;
    private double value;

    public ConfigUpdateMessage() {}

    public ConfigUpdateMessage(String itemId, String attributeName, double value) {
        this.itemId = itemId;
        this.attributeName = attributeName;
        this.value = value;
    }

    public ConfigUpdateMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.attributeName = buf.readUtf();
        this.value = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeUtf(attributeName);
        buf.writeDouble(value);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            // 属性写回覆写层（权威 + 覆写合并基线上 upsert，立即落盘生效）
            DefsManager.updateItemAttribute(player.server, itemId, attributeName, value);

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
