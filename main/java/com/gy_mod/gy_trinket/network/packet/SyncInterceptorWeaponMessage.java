package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncInterceptorWeaponMessage {
    private final ItemStack weapon;

    public SyncInterceptorWeaponMessage() {
        this.weapon = ItemStack.EMPTY;
    }

    public SyncInterceptorWeaponMessage(ItemStack weapon) {
        this.weapon = weapon;
    }

    public SyncInterceptorWeaponMessage(FriendlyByteBuf buf) {
        this.weapon = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItemStack(weapon, false);
    }

    @SuppressWarnings("resource")
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    com.gy_mod.gy_trinket.client.attack_mode.interceptor.InterceptorWeaponClientData.setWeapon(mc.player.getUUID(), weapon);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
