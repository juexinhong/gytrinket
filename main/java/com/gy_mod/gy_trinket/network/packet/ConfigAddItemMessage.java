package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigAddItemMessage {
    private String itemId;

    public ConfigAddItemMessage() {}

    public ConfigAddItemMessage(String itemId) {
        this.itemId = itemId;
    }

    public ConfigAddItemMessage(FriendlyByteBuf buf) {
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

            if (itemId != null && !itemId.isEmpty() && !itemId.equals("minecraft:air")) {
                // 添加物品 = 确保覆写层条目存在（空属性接管；已有权威/覆写属性时幂等忽略）
                DefsManager.ensureItemOverrideEntry(player.server, itemId);

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
