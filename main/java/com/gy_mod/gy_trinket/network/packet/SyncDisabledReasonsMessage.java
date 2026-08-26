package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步光点核心各槽位禁用原因（用于容器界面显示灰色遮罩）
 */
public class SyncDisabledReasonsMessage {
    public List<String> reasons;

    public SyncDisabledReasonsMessage() {
        this.reasons = new ArrayList<>();
    }

    public SyncDisabledReasonsMessage(List<String> reasons) {
        this.reasons = reasons;
    }

    public SyncDisabledReasonsMessage(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.reasons = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.reasons.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(reasons.size());
        for (String reason : reasons) {
            buf.writeUtf(reason);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleSyncDisabledReasons(this));
        });
        context.setPacketHandled(true);
    }
}
