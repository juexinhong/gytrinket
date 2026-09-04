package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S 请求全量配置项同步（打开"配置项"界面时发送）。
 * 权限：需 2 级（管理员）。
 */
public class ConfigValuesRequestMessage {
    public ConfigValuesRequestMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public ConfigValuesRequestMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                if (!player.hasPermissions(2)) return;
                NetworkHandler.sendConfigValuesToPlayer(player, true);
            }
        });
        context.setPacketHandled(true);
    }
}
