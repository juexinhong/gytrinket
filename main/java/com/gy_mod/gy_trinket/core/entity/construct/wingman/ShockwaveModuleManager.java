package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 震撼弹模块管理器
 * <p>
 * 检查玩家光点核心是否拥有震撼弹模块物品，
 * 若有则提升爆破弹的爆炸伤害和溅射长度。
 */
public class ShockwaveModuleManager {

    /**
     * 检查玩家光点核心是否拥有震撼弹模块
     */
    public static boolean hasShockwaveModule(UUID playerUUID) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isShockwaveModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
