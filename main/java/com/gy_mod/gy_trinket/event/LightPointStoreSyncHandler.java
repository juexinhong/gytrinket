package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.type.IShieldType;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import com.gy_mod.gy_trinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

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

        sendDataSnapshotToClient(player);
    }

    public static void sendDataSnapshotToClient(ServerPlayer player) {
        UUID uuid = player.getUUID();

        CompoundTag snapshot = new CompoundTag();

        var store = PlayerStoreManager.getPlayerStore(uuid);
        if (store != null) {
            ListTag itemList = new ListTag();
            var handler = store.getItemHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                CompoundTag itemTag = new CompoundTag();
                handler.getStackInSlot(i).save(itemTag);
                itemList.add(itemTag);
            }
            snapshot.put("items", itemList);
        }

        ShieldData shieldData = PlayerDataCenter.getData(uuid, "shield");
        if (shieldData != null) {
            snapshot.putDouble("currentShield", shieldData.getCurrentShield());
            snapshot.putDouble("maxShield", shieldData.getMaxShield());
        }

        String activeType = "none";
        var types = ShieldTypeManager.getPlayerShieldTypes(uuid);
        for (IShieldType.ShieldTypeData data : types) {
            if (data.active()) {
                activeType = data.type().getName();
                break;
            }
        }
        snapshot.putString("activeShieldType", activeType);
        PlayerDataCenter.setData(uuid, "active_shield_type", activeType);

        NetworkHandler.sendPlayerDataSnapshotToClient(player, snapshot);
    }
}
