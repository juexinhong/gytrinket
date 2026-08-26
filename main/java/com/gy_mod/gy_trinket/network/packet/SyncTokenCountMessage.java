package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步玩家背包中持有的代币数量（随机构建代币机制，背包内容变动时更新）
 */
public class SyncTokenCountMessage {
    public int tokenCount;

    public SyncTokenCountMessage() {}

    public SyncTokenCountMessage(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public SyncTokenCountMessage(FriendlyByteBuf buf) {
        this.tokenCount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(tokenCount);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleSyncTokenCount(this));
        });
        context.setPacketHandled(true);
    }
}
