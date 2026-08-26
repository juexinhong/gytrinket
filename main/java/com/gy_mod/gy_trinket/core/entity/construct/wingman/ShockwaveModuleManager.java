package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 震撼弹模块管理器
 * <p>
 * 检查玩家已装备物品（光点核心存储 + Curios 饰品栏）是否拥有震撼弹模块物品，
 * 若有则提升爆破弹的爆炸伤害和溅射长度。
 */
public class ShockwaveModuleManager {

    /**
     * 检查玩家已装备物品（光点核心存储 + Curios 饰品栏）是否拥有震撼弹模块
     */
    public static boolean hasShockwaveModule(UUID playerUUID) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isShockwaveModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
