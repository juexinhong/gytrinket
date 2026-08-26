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
            ListTag itemList = buildItemList(store, player);
            NetworkHandler.sendLightPointCoreSyncToClient(player, itemList, store.getItemHandler().getSlots());
        }

        sendDataSnapshotToClient(player);
    }

    public static void sendDataSnapshotToClient(ServerPlayer player) {
        UUID uuid = player.getUUID();

        CompoundTag snapshot = new CompoundTag();

        var store = PlayerStoreManager.getPlayerStore(uuid);
        if (store != null) {
            snapshot.put("items", buildItemList(store, player));
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

    /**
     * 构建物品列表 - 只保存非空物品，带Slot索引
     */
    private static ListTag buildItemList(PlayerStore store, ServerPlayer player) {
        ListTag itemList = new ListTag();
        var handler = store.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            var stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = stack.save(new CompoundTag());
                itemTag.putByte("Slot", (byte) i);
                itemList.add(itemTag);
            }
        }
        return itemList;
    }
}
