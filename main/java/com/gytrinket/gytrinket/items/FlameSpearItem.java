package com.gytrinket.gytrinket.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 焰矛（充能武器）。
 * <p>
 * 使用模拟充能：右键时由客户端按键检测通知服务端充能（不进入真实"使用物品"状态，
 * 避免移动减速），松开右键快速消退。
 */
public class FlameSpearItem extends Item {

    public FlameSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 不进入真实使用状态，充能与消退由 FlameSpearManager 依据客户端模拟状态处理
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
