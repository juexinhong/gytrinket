package com.gy_mod.gy_trinket.storage;

import com.gy_mod.gy_trinket.compat.CuriosCompat;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 玩家存储工具类
 * 提供物品栏遍历检查的通用方法
 * <p>
 * 光点核心内容扩展：以下"已装备物品"统一指 光点核心存储 + Curios 饰品栏 的并集，
 * 未安装 Curios 时自动退化为仅光点核心存储。
 */
public class PlayerStoreUtils {

    private PlayerStoreUtils() {}

    /**
     * 获取玩家全部已装备物品（光点核心存储 + Curios 饰品栏），只读扫描用。
     */
    public static List<ItemStack> getAllEquippedStacks(Player player) {
        List<ItemStack> result = new ArrayList<>();
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
        }
        if (CuriosCompat.isCuriosLoaded()) {
            result.addAll(CuriosCompat.getEquippedCurios(player));
        }
        return result;
    }

    /**
     * 获取玩家全部已装备物品的注册 ID 集合（光点核心存储 + Curios 饰品栏）。
     */
    public static Set<String> getAllEquippedItemIds(Player player) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : getAllEquippedStacks(player)) {
            ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }

    /**
     * 从 UUID 获取玩家已装备物品的注册 ID 集合。
     * 玩家在线时包含 Curios 饰品栏物品；离线时仅光点核心存储。
     */
    public static Set<String> getAllEquippedItemIds(UUID playerUUID) {
        ServerPlayer player = CuriosCompat.getServerPlayer(playerUUID);
        if (player != null) {
            return getAllEquippedItemIds(player);
        }
        // 离线兜底：仅扫描光点核心存储
        Set<String> ids = new LinkedHashSet<>();
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        return ids;
    }

    /**
     * 从 UUID 获取玩家已装备物品栈（光点核心存储 + Curios 饰品栏）。
     * 玩家在线时包含饰品栏物品；离线时仅光点核心存储。
     */
    public static List<ItemStack> getEquippedStacks(UUID playerUUID) {
        ServerPlayer player = CuriosCompat.getServerPlayer(playerUUID);
        if (player != null) {
            return getAllEquippedStacks(player);
        }
        // 离线兜底：仅光点核心存储
        List<ItemStack> result = new ArrayList<>();
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
        }
        return result;
    }

    /**
     * 检查玩家是否拥有指定类型的活跃物品（未被禁用），范围含 Curios 饰品栏
     *
     * @param player 玩家
     * @param itemCheck 物品检查谓词
     * @return 是否拥有匹配的活跃物品
     */
    public static boolean hasActiveItem(Player player, Predicate<Item> itemCheck) {
        for (ItemStack stack : getAllEquippedStacks(player)) {
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(player.getUUID(), stack) && itemCheck.test(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}

