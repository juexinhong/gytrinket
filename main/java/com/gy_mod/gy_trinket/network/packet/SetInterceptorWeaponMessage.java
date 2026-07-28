package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SetInterceptorWeaponMessage {
    private final ItemStack weapon;

    public SetInterceptorWeaponMessage() {
        this.weapon = ItemStack.EMPTY;
    }

    public SetInterceptorWeaponMessage(ItemStack weapon) {
        this.weapon = weapon;
    }

    public SetInterceptorWeaponMessage(FriendlyByteBuf buf) {
        this.weapon = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItemStack(weapon, false);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                InterceptorWeaponManager.setWeapon(player.getUUID(), weapon);
                // Sync back to client
                NetworkHandler.sendInterceptorWeaponToPlayer(player, weapon);

                // Update all wingman entities for this player
                updateWingmanWeapons(player, weapon);
            }
        });
        context.setPacketHandled(true);
    }

    private static void updateWingmanWeapons(ServerPlayer player, ItemStack weapon) {
        com.gy_mod.gy_trinket.core.entity.construct.ConstructManager cm =
            com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance();
        Map<UUID, net.minecraft.world.entity.Entity> entities =
            cm.getActiveConstructEntities(player.getUUID(),
                com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes.WINGMAN);
        for (net.minecraft.world.entity.Entity entity : entities.values()) {
            if (entity instanceof com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity wingman) {
                wingman.refreshInterceptorData();
            }
        }
    }
}
