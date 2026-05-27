package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class LightPointStoreSyncHandler {

    @SubscribeEvent
    public static void onLightPointStoreChanged(PlayerLightPointStoreChangedEvent event) {
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(event.getPlayerUUID());
        if (player == null) {
            return;
        }

        var store = PlayerStoreManager.getPlayerStore(event.getPlayerUUID());
        if (store != null) {
            ListTag itemList = new ListTag();
            var handler = store.getItemHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                var item = handler.getStackInSlot(i);
                CompoundTag itemTag = new CompoundTag();
                item.save(itemTag);
                itemList.add(itemTag);
            }
            NetworkHandler.sendLightPointCoreSyncToClient(player, itemList, handler.getSlots());
        }
    }
}