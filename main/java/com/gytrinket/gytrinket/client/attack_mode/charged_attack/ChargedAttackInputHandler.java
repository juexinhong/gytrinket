package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

import com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil;
import com.gytrinket.gytrinket.client.attack_mode.AttackStateInputHandler;
import com.gytrinket.gytrinket.core.attack_mode.AttackStateManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.ChargedAttackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 充能攻击客户端输入处理
 * <p>
 * 攻击输入拦截由 {@link com.gytrinket.gytrinket.mixin.MinecraftClientMixin} 在原版方法层面完成，
 * 可兼容 Better Combat 等通过 Mixin 接管攻击行为的模组。
 * <p>
 * 本类负责：
 * 1. 提供 {@link #startCharging} 和 {@link #isCharging()} 供 Mixin 调用
 * 2. 在客户端 tick 中检测松开左键，释放充能攻击
 * <p>
 * 充能值计算完全由服务端负责，客户端通过 SyncChargedAttackMessage 同步用于HUD显示。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackInputHandler {

    // 客户端充能状态
    private static boolean isCharging = false;
    // 充能启动后的等待计数器，用于避免启动帧误触发释放
    // startAttack 在 Render 线程中触发，而 onClientTick.Post 在同一帧稍后执行，
    // 此时 AttackStateInputHandler 尚未将状态从 RELEASED 更新为 PRESSED，
    // 因此需要跳过启动后的前几 tick 的松开检测
    private static int chargeStartDelay = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Player player = minecraft.player;

        if (!AttackModeClientUtil.hasChargedAttackItem()) {
            if (isCharging) {
                resetCharge();
            }
            return;
        }

        // 递减启动延迟计数器
        if (chargeStartDelay > 0) {
            chargeStartDelay--;
        }

        // 充能期间攻击强度低于0.5时立即释放（切换物品会重置攻击强度）
        // 防止玩家利用空手高攻击速度充能后切换到高伤害武器释放
        if (isCharging && chargeStartDelay == 0 && player.getAttackStrengthScale(0.0F) < 0.5F) {
            releaseAttack(player, minecraft);
            return;
        }

        AttackStateManager.AttackState attackState = AttackStateInputHandler.getCurrentState();

        // 只有启动延迟结束后才检测松开
        if (chargeStartDelay == 0 && attackState == AttackStateManager.AttackState.RELEASED && isCharging) {
            releaseAttack(player, minecraft);
        }
    }

    /**
     * 启动充能（由 MinecraftClientMixin 调用）
     */
    public static void startCharging() {
        isCharging = true;
        // 等待 2 tick，确保 AttackStateInputHandler 已将状态更新为 PRESSED/HELD
        chargeStartDelay = 2;
        // 先补发一次按住状态包再请求启动充能：
        // startAttack 在输入事件中触发，早于本 tick 的 AttackStateInputHandler 状态同步，
        // 高延迟下 ChargedAttackPayload(0) 会先于 AttackStatePayload(PRESSED) 到达服务端，
        // 服务端在状态确认前的 RELEASED 默认态会立即误释放（0 充能值）。
        // TCP 保序，先发状态包可确保服务端先确认 HELD 再启动充能
        PacketDistributor.sendToServer(
            new com.gytrinket.gytrinket.network.packet.AttackStatePayload(
                AttackStateManager.AttackState.PRESSED.ordinal(), 0));
        // 通知服务端开始充能
        PacketDistributor.sendToServer(new ChargedAttackPayload(0));
    }

    private static void releaseAttack(Player player, Minecraft minecraft) {
        resetCharge();

        // 寻找准星对准的目标（含展示框、画等非生物实体）
        Entity target = AttackModeClientUtil.findTargetInCrosshair(player);
        if (target != null) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (ChargedAttackSweepHandler.isSwordItem(mainHandItem)) {
                // 剑类充能攻击：发送action=4，服务端执行自定义横扫伤害
                // 生物目标由横扫处理；非生物目标（展示框/画等）由服务端穿透伤害处理
                PacketDistributor.sendToServer(new ChargedAttackPayload(4));
                // 仍需挥动手臂和重置攻击强度
                player.swing(InteractionHand.MAIN_HAND);
                AttackModeClientUtil.resetAttackStrengthTicker(player);
            } else {
                // 非剑类充能攻击：使用原版攻击+充能加成
                PacketDistributor.sendToServer(new ChargedAttackPayload(2));
                minecraft.gameMode.attack(player, target);
                AttackModeClientUtil.resetAttackStrengthTicker(player);
            }
        } else {
            // 无目标：发送取消消息，清空充能
            PacketDistributor.sendToServer(new ChargedAttackPayload(3));
        }
    }

    private static void resetCharge() {
        isCharging = false;
        chargeStartDelay = 0;
    }

    /**
     * 获取客户端充能状态（供 Mixin 和 HUD 渲染使用）
     */
    public static boolean isCharging() {
        return isCharging;
    }
}
