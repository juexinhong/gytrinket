package com.gytrinket.gytrinket.items;

import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.PASS;
        }

        // 检查玩家是否装备了护盾移植模块
        if (!ShieldTransferManager.hasShieldTransferItem(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.need_transfer_module"));
            return InteractionResult.FAIL;
        }

        // 检查目标实体是否是玩家自己
        if (target == player) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.cannot_add_self"));
            return InteractionResult.FAIL;
        }

        // 检查目标实体是否已经被当前玩家保护
        boolean isProtected = ShieldTransferManager.isEntityProtected(player.getUUID(), target.getUUID());

        if (isProtected) {
            // 移除实体的护盾保护
            ShieldTransferManager.removeProtectionForEntity(player.getUUID(), target.getUUID());
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.target_removed"));
        } else {
            // 添加实体的护盾保护
            ShieldTransferManager.transferShieldToEntity(player, target);
            player.sendSystemMessage(Component.translatable("message.gytrinket.shield_receiver.target_added"));
        }

        return InteractionResult.SUCCESS;
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
