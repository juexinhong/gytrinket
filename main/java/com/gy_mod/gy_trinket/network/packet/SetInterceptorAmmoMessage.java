package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端消息：设置拦截机弹药
 */
public class SetInterceptorAmmoMessage {
    private final ItemStack ammo;

    public SetInterceptorAmmoMessage() {
        this.ammo = ItemStack.EMPTY;
    }

    public SetInterceptorAmmoMessage(ItemStack ammo) {
        this.ammo = ammo;
    }

    public SetInterceptorAmmoMessage(FriendlyByteBuf buf) {
        this.ammo = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItemStack(ammo, false);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                InterceptorWeaponManager.setAmmo(player.getUUID(), ammo);
                InterceptorWeaponManager.refreshAllWingmen(player);
            }
        });
        context.setPacketHandled(true);
    }
}
