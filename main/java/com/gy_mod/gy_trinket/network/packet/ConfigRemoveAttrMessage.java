package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigRemoveAttrMessage {
    private String itemId;
    private String attributeName;

    public ConfigRemoveAttrMessage() {}

    public ConfigRemoveAttrMessage(String itemId, String attributeName) {
        this.itemId = itemId;
        this.attributeName = attributeName;
    }

    public ConfigRemoveAttrMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.attributeName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeUtf(attributeName);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            // 从覆写层移除单条属性（剩余集整体写回；显式空 = 覆盖为无属性）
            DefsManager.removeItemAttribute(player.server, itemId, attributeName);

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
