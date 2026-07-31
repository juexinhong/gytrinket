package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：通知玩家空挥（左键攻击空气）
 * <p>
 * PlayerInteractEvent.LeftClickEmpty 仅在客户端触发，
 * 需通过网络包通知服务端扣除隐身进度。
 */
public class GhostFuselageAttackMessage {

    public GhostFuselageAttackMessage() {}

    public GhostFuselageAttackMessage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                GhostFuselageManager.onClientSwingAttack(player);
            }
        });
        context.setPacketHandled(true);
    }
}
