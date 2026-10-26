package com.gytrinket.gytrinket.network.packet;
import com.gytrinket.gytrinket.core.defs.DefsItemOverrides;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigDeleteItemMessage {
    private String itemId;

    public ConfigDeleteItemMessage() {}

    public ConfigDeleteItemMessage(String itemId) {
        this.itemId = itemId;
    }

    public ConfigDeleteItemMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            // UI 删除物品（三态）：无数据文件→真删除；有数据文件→写禁用态；已禁用→回退数据文件版本
            DefsItemOverrides.deleteItemOverride(player.server, itemId);

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
