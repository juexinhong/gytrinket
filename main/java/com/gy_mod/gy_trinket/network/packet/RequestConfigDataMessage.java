package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestConfigDataMessage {
    public RequestConfigDataMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public RequestConfigDataMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                if (!player.hasPermissions(2)) return;
                // 打开配置界面：先热重载磁盘覆盖文件（分层 override_layer_N.json + UI 文件），保证界面读取磁盘最新状态
                com.gy_mod.gy_trinket.core.defs.DefsManager.applyOverrides(player.server);
                NetworkHandler.sendConfigDataToPlayer(player);
                NetworkHandler.sendDefsSyncToPlayer(player);
            }
        });
        context.setPacketHandled(true);
    }
}
