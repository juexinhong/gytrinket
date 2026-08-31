package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.menu.LightPointCoreMenu;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：请求整理光点核心容器（容器界面内鼠标中键，按创造物品栏排序）
 */
public class SortLightPointCoreMessage {
    public SortLightPointCoreMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public SortLightPointCoreMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (player.containerMenu instanceof LightPointCoreMenu menu) {
                menu.sortContainer();
                menu.broadcastChanges();
            }
        });
        context.setPacketHandled(true);
    }
}
