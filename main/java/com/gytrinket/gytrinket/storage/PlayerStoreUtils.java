package com.gytrinket.gytrinket.storage;

import com.gytrinket.gytrinket.core.disable.DisableSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 玩家存储工具类
 * 提供物品栏遍历检查的通用方法
 */
public class PlayerStoreUtils {

    private PlayerStoreUtils() {}

    /**
     * 检查玩家是否拥有指定类型的活跃物品（未被禁用）
     *
     * @param player 玩家
     * @param itemCheck 物品检查谓词
     * @return 是否拥有匹配的活跃物品
     */
    public static boolean hasActiveItem(Player player, Predicate<Item> itemCheck) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(player.getUUID());
        if (store == null) return false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(player.getUUID(), stack) && itemCheck.test(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
