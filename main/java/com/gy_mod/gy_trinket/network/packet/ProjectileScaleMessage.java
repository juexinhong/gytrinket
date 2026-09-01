package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 弹射物大小同步消息（S -> C）
 * <p>
 * 同步弹射物的渲染缩放值，客户端据此在渲染时缩放弹射物模型。
 * 缩放仅影响渲染，不改变弹射物碰撞箱与命中判定。
 */
public class ProjectileScaleMessage {
    private int entityId;
    private float scale;

    public ProjectileScaleMessage() {}

    public ProjectileScaleMessage(int entityId, float scale) {
        this.entityId = entityId;
        this.scale = scale;
    }

    public ProjectileScaleMessage(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.scale = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(scale);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleProjectileScaleMessage(
                    entityId, scale));
        });
        context.setPacketHandled(true);
    }
}
