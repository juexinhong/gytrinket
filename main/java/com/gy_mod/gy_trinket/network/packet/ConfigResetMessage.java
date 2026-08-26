package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigResetMessage {
    public ConfigResetMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public ConfigResetMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            AttributeManager.resetToDefaults();
            Config.resetItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
