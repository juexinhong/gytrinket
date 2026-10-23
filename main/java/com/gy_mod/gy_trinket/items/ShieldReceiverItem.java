package com.gy_mod.gy_trinket.items;

import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 护盾接收器物品
 * 用于将实体纳入或移除护盾移植保护
 */
public class ShieldReceiverItem extends Item {

    public ShieldReceiverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        // 检查玩家是否装备了护盾移植模块
        if (!ShieldTransferManager.hasShieldTransferItem(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.need_transfer_module"));
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        // 显示当前保护的实体列表
        List<LivingEntity> protectedEntities = ShieldTransferManager.getProtectedEntities(player.getUUID(), level);
        if (protectedEntities.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.no_protected"));
        } else {
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.protected_list"));
            for (LivingEntity entity : protectedEntities) {
                player.sendSystemMessage(Component.literal("- " + entity.getName().getString()));
            }
        }

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}

