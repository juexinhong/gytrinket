package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.shield.ShieldData;
import com.gytrinket.gytrinket.core.shield.type.IShieldType;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
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
    private static ListTag buildItemList(com.gytrinket.gytrinket.storage.PlayerStore store, ServerPlayer player) {
        ListTag itemList = new ListTag();
        var handler = store.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            var stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = (CompoundTag) stack.save(player.registryAccess());
                itemTag.putByte("Slot", (byte) i);
                itemList.add(itemTag);
            }
        }
        return itemList;
    }
}
