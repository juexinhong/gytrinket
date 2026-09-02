package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

import com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil;
import com.gytrinket.gytrinket.client.attack_mode.AttackStateInputHandler;
import com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler;
import com.gytrinket.gytrinket.core.attack_mode.AttackStateManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.ChargedAttackPayload;
import com.gytrinket.gytrinket.network.packet.ItemUseChargePayload;
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
    // 长按右键充能状态（与左键充能 isCharging 分离，避免左键松开检测误触发右键充能释放）
    private static boolean isRightCharging = false;

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
            if (isRightCharging) {
                cancelRightCharging();
            }
            return;
        }

        // 右键充能期间打开界面或窗口失焦：视为松开右键，释放充能
        if (isRightCharging && (minecraft.screen != null || !minecraft.isWindowActive())) {
            cancelRightCharging();
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
     * 长按右键充能开始（由 MouseHandlerMixin 在右键按下时调用）
     */
    public static void startChargingFromRightButton() {
        if (isCharging || isRightCharging) {
            return;
        }
        if (!AttackModeClientUtil.hasChargedAttackItem()) {
            return;
        }
        // 点射连击冷却期间禁止充能（与左键充能同一客户端门控；弹射物点射物品冷却由服务端拦截）
        Player player = Minecraft.getInstance().player;
        if (player != null && BurstFireClientHandler.isInComboCooldown(player.getUUID())) {
            return;
        }
        isRightCharging = true;
        // 通知服务端开始右键充能
        PacketDistributor.sendToServer(new ItemUseChargePayload(false));
    }

    /**
     * 长按右键充能释放（由 MouseHandlerMixin 在右键松开时调用）
     */
    public static void releaseChargingFromRightButton() {
        if (!isRightCharging) {
            return;
        }
        isRightCharging = false;
        // 通知服务端释放充能（进入消退期，不触发攻击行为）
        PacketDistributor.sendToServer(new ItemUseChargePayload(true));
    }

    /**
     * 兜底取消右键充能（界面打开/失焦/模块失效）：等同松开右键
     */
    private static void cancelRightCharging() {
        isRightCharging = false;
        PacketDistributor.sendToServer(new ItemUseChargePayload(true));
    }

    /**
     * 获取客户端充能状态（供 Mixin 和 HUD 渲染使用）
     * 左键充能与右键充能任一进行中均视为充能中（右键充能期间阻止左键攻击）
     */
    public static boolean isCharging() {
        return isCharging || isRightCharging;
    }
}
