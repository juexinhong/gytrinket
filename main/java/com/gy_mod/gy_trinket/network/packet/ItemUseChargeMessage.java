package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 长按右键充能网络包（通用使用键充能，不依赖任何具体物品）
 * <p>
 * release=false：客户端右键按下，请求开始充能
 * release=true：客户端右键松开（或界面打开/失焦兜底取消），请求释放充能
 */
public class ItemUseChargeMessage {

    private final boolean release;

    public ItemUseChargeMessage(boolean release) {
        this.release = release;
    }

    public ItemUseChargeMessage(FriendlyByteBuf buf) {
        this.release = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.release);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (this.release) {
                ChargedAttackManager.releaseChargeFromItemUse(player);
            } else {
                ChargedAttackManager.startItemUseCharge(player);
            }
        });
        context.setPacketHandled(true);
    }
}
