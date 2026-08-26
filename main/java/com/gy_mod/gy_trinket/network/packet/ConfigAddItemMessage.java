package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
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
                if (!AttributeManager.isItemAttributeRegistered(itemId)) {
                    Map<String, Double> attrs = new HashMap<>();
                    AttributeManager.registerItemAttributes(itemId, attrs);
                    Config.saveItemAttributesConfig();
                }
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
