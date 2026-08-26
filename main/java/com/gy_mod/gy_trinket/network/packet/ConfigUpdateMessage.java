package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.ItemAttributeConfig;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
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

            ItemAttributeConfig config = AttributeManager.getItemAttributes(itemId);
            if (config != null) {
                config.addAttribute(attributeName, value);
            } else {
                Map<String, Double> attrs = new HashMap<>();
                attrs.put(attributeName, value);
                AttributeManager.registerItemAttributes(itemId, attrs);
            }

            Config.saveItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
