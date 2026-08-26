package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：随机构建随机池（最多 9 个物品 id）
 */
public class ResponseRandomBuildMessage {
    public List<String> itemIds;

    public ResponseRandomBuildMessage() {
        this.itemIds = new ArrayList<>();
    }

    public ResponseRandomBuildMessage(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public ResponseRandomBuildMessage(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.itemIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.itemIds.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(itemIds.size());
        for (String itemId : itemIds) {
            buf.writeUtf(itemId);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleResponseRandomBuild(this));
        });
        context.setPacketHandled(true);
    }
}
