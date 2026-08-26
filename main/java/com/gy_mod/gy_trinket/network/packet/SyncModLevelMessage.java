package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步光点等级/经验/升级点/刷新点（HUD 提示与面板显示用）
 */
public class SyncModLevelMessage {
    public int modLevel;
    public int upgradeExp;
    public int upgradePoints;
    public int randomPoints;

    public SyncModLevelMessage() {}

    public SyncModLevelMessage(int modLevel, int upgradeExp, int upgradePoints, int randomPoints) {
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
        this.randomPoints = randomPoints;
    }

    public SyncModLevelMessage(FriendlyByteBuf buf) {
        this.modLevel = buf.readInt();
        this.upgradeExp = buf.readInt();
        this.upgradePoints = buf.readInt();
        this.randomPoints = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(modLevel);
        buf.writeInt(upgradeExp);
        buf.writeInt(upgradePoints);
        buf.writeInt(randomPoints);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleSyncModLevel(this));
        });
        context.setPacketHandled(true);
    }
}
