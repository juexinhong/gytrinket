package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigReorderMessage {
    public final int fromIndex;
    public final int toIndex;

    public ConfigReorderMessage(int fromIndex, int toIndex) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    public ConfigReorderMessage(FriendlyByteBuf buf) {
        this.fromIndex = buf.readVarInt();
        this.toIndex = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(fromIndex);
        buf.writeVarInt(toIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            AttributeManager.reorderItem(fromIndex, toIndex);
            Config.saveItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
