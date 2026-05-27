package com.gy_mod.gy_trinket.storage;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家存储管理器
 * 负责管理所有玩家的 PlayerStore 实例
 * 提供获取、创建、保存和加载玩家存储的功能
 * 该类仅提供方法
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerStoreManager {
    // 存储所有玩家的 PlayerStore 实例，键是玩家 UUID
    private static final Map<UUID, PlayerStore> PLAYER_STORES = new HashMap<>();

    // 禁止实例化
    private PlayerStoreManager() {}

    /**
     * 获取玩家的存储，如果不存在则创建新的
     * @param player 玩家实例
     * @return PlayerStore 实例
     */
    public static PlayerStore getOrCreatePlayerStore(Player player) {
        return getOrCreatePlayerStore(player.getUUID());
    }

    /**
     * 获取玩家的存储，如果不存在则创建新的
     * @param playerUUID 玩家 UUID
     * @return PlayerStore 实例
     */
    public static PlayerStore getOrCreatePlayerStore(UUID playerUUID) {
        return PLAYER_STORES.computeIfAbsent(playerUUID, PlayerStore::new);
    }

    /**
     * 获取玩家的存储，如果不存在则返回 null
     * @param player 玩家实例
     * @return PlayerStore 实例，不存在则返回 null
     */
    public static PlayerStore getPlayerStore(Player player) {
        return getPlayerStore(player.getUUID());
    }

    /**
     * 获取玩家的存储，如果不存在则返回 null
     * @param playerUUID 玩家 UUID
     * @return PlayerStore 实例，不存在则返回 null
     */
    public static PlayerStore getPlayerStore(UUID playerUUID) {
        return PLAYER_STORES.get(playerUUID);
    }

    /**
     * 检查玩家是否有存储
     * @param player 玩家实例
     * @return 是否存在该玩家的存储
     */
    public static boolean hasPlayerStore(Player player) {
        return hasPlayerStore(player.getUUID());
    }

    /**
     * 检查玩家是否有存储
     * @param playerUUID 玩家 UUID
     * @return 是否存在该玩家的存储
     */
    public static boolean hasPlayerStore(UUID playerUUID) {
        return PLAYER_STORES.containsKey(playerUUID);
    }

    /**
     * 移除玩家的存储
     * @param player 玩家实例
     */
    public static void removePlayerStore(Player player) {
        removePlayerStore(player.getUUID());
    }

    /**
     * 移除玩家的存储
     * @param playerUUID 玩家 UUID
     */
    public static void removePlayerStore(UUID playerUUID) {
        PLAYER_STORES.remove(playerUUID);
    }

    /**
     * 将玩家的存储保存到 NBT 数据
     * @param player 玩家实例
     * @return 包含存储内容的 NBT 标签
     */
    public static net.minecraft.nbt.CompoundTag saveToNBT(Player player) {
        PlayerStore store = getPlayerStore(player);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (store == null) {
            return tag;
        }

        net.minecraft.nbt.ListTag itemsTag = new net.minecraft.nbt.ListTag();
        ItemStackHandler handler = store.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
            if (!stack.isEmpty()) {
                stack.save(itemTag);
            }
            itemsTag.add(itemTag);
        }
        tag.put("Items", itemsTag);

        return tag;
    }

    /**
     * 从 NBT 数据加载玩家的存储
     * @param player 玩家实例
     * @param tag 包含存储内容的 NBT 标签
     */
    public static void loadFromNBT(Player player, net.minecraft.nbt.CompoundTag tag) {
        if (!tag.contains("Items")) {
            // 如果没有 Items 数据，直接创建空存储
            getOrCreatePlayerStore(player);
            return;
        }

        PlayerStore store = getOrCreatePlayerStore(player);
        ItemStackHandler handler = store.getItemHandler();
        net.minecraft.nbt.ListTag itemsTag = tag.getList("Items", 10);

        for (int i = 0; i < handler.getSlots() && i < itemsTag.size(); i++) {
            net.minecraft.nbt.CompoundTag itemTag = itemsTag.getCompound(i);
            if (!itemTag.isEmpty()) {
                ItemStack stack = ItemStack.of(itemTag);
                handler.setStackInSlot(i, stack);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerUUID = player.getUUID();
        removePlayerStore(playerUUID);
        gytrinket.LOGGER.debug("玩家 {} 退出，清理玩家存储", playerUUID);
    }
}