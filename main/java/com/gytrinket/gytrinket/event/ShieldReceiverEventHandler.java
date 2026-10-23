package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.items.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 护盾接收器交互拦截器
 * <p>
 * 原版 Player.interactOn 的顺序为：生物自身交互（狼坐下/站起、村民交易等）先于
 * 物品的 interactLivingEntity 执行并消耗动作，导致护盾接收器对有原版交互行为的
 * 生物（狼/村民等）永远无法生效（1.20.1 与 1.21.1 相同）。
 * <p>
 * 本处理器在更早的事件阶段（PlayerInteractEvent.EntityInteract / EntityInteractSpecific，
 * 位于 interactOn 内生物交互之前触发）接管：手持护盾接收器右键 LivingEntity 时取消事件，
 * 阻止原版交互，并执行记录/移除保护逻辑。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldReceiverEventHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = handle(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = handle(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /**
     * 判定并执行护盾接收器对生物的交互。
     *
     * @return null 表示与本物品无关（不取消事件）；否则返回应设置的交互结果（事件取消）
     */
    private static InteractionResult handle(Player player, ItemStack stack, Entity target) {
        if (stack.isEmpty() || !stack.is(ModItems.SHIELD_RECEIVER)) {
            return null;
        }
        if (!(target instanceof LivingEntity livingTarget)) {
            return null;
        }
        if (player.level().isClientSide()) {
            // 客户端：接管交互，阻止对生物原版交互（坐下/站起/交易等）的本地预测，
            // 实际结果以服务端处理为准并同步
            return InteractionResult.CONSUME;
        }
        // 服务端：执行记录/移除保护
        return handleServer(player, livingTarget);
    }

    private static InteractionResult handleServer(Player player, LivingEntity target) {
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

        // 检查目标实体是否已经被当前玩家保护（内存记录或实体标签，标签随实体复制/重建保留）
        boolean isProtected = ShieldTransferManager.isEntityProtected(player.getUUID(), target.getUUID())
                || player.getUUID().equals(ShieldTransferManager.getEntityTransferTagOwner(target));

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
}
