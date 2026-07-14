package com.gy_mod.gy_trinket.client.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.client.attack_mode.AttackModeClientUtil;
import com.gy_mod.gy_trinket.client.attack_mode.AttackStateInputHandler;
import com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler;
import com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 充能攻击客户端输入处理
 * <p>
 * 攻击输入拦截由 {@link com.gy_mod.gy_trinket.mixin.MinecraftClientMixin} 在原版方法层面完成，
 * 可兼容 Better Combat 等通过 Mixin 接管攻击行为的模组。
 * <p>
 * 本类负责：
 * 1. 提供 {@link #startCharging()} 和 {@link #isCharging()} 供 Mixin 调用
 * 2. 在客户端 tick 中检测松开左键，释放充能攻击
 * <p>
 * 充能值计算完全由服务端负责，客户端通过 SyncChargedAttackMessage 同步用于HUD显示。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackInputHandler {

    // 客户端充能状态
    private static boolean isCharging = false;
    // 充能启动后的等待计数器，用于避免启动帧误触发释放
    // startAttack 在 Render 线程中触发，而 onClientTick 在同一帧稍后执行，
    // 此时 AttackStateInputHandler 尚未将状态从 RELEASED 更新为 PRESSED，
    // 因此需要跳过启动后的前几 tick 的松开检测
    private static int chargeStartDelay = 0;

    /**
     * 客户端攻击输入拦截 - 充能攻击的核心入口
     * <p>
     * 使用 HIGHEST 优先级确保在其他模组处理攻击输入之前拦截，
     * 防止其他修改客户端攻击行为的模组覆盖本模组的充能攻击方式。
     * <p>
     * 判定逻辑：
     * - 正在充能中 → 取消攻击
     * - 拥有充能攻击物品 + 攻击强度满 + 不在连击冷却 + 未在充能 → 启动充能并取消攻击
     * - 其他情况 → 允许原版攻击通过
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackKeyInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Player player = minecraft.player;

        // 瞄准方块时不拦截，允许正常挖掘（服务端攻击强度检查会自然暂停充能）
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        // 正在充能中：取消所有原版攻击输入
        if (isCharging) {
            event.setCanceled(true);
            return;
        }

        // 初始攻击拦截：拥有充能攻击物品且攻击强度满时，启动充能并取消本次攻击
        // 此判定与攻击拦截在同一事件中完成，不存在时序依赖
        if (AttackModeClientUtil.hasChargedAttackItem()) {
            float attackStrength = player.getAttackStrengthScale(0.0F);
            if (attackStrength >= 1.0F) {
                if (!BurstFireClientHandler.isInComboCooldown(player.getUUID())) {
                    startCharging(player);
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

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
     * 启动充能（由 MinecraftClientMixin 和 InteractionKeyMappingTriggered 调用）
     */
    private static void startCharging(Player player) {
        isCharging = true;
        // 等待 2 tick，确保 AttackStateInputHandler 已将状态更新为 PRESSED/HELD
        chargeStartDelay = 2;
        // 通知服务端开始充能
        NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(0));
    }

    /**
     * 启动充能（无参版本，供 MinecraftClientMixin 调用）
     */
    public static void startCharging() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            startCharging(minecraft.player);
        }
    }

    private static void releaseAttack(Player player, Minecraft minecraft) {
        resetCharge();

        // 寻找准星对准的目标
        Entity target = AttackModeClientUtil.findTargetInCrosshair(player);
        if (target instanceof LivingEntity) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (ChargedAttackSweepHandler.isSwordItem(mainHandItem)) {
                // 剑类充能攻击：发送action=4，服务端执行自定义横扫伤害
                // 不调用原版attack，服务端处理一切
                NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(4));
                // 仍需挥动手臂和重置攻击强度
                player.swing(InteractionHand.MAIN_HAND);
                AttackModeClientUtil.resetAttackStrengthTicker(player);
            } else {
                // 非剑类充能攻击：使用原版攻击+充能加成
                NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(2));
                minecraft.gameMode.attack(player, target);
                AttackModeClientUtil.resetAttackStrengthTicker(player);
            }
        } else {
            // 无目标：发送取消消息，清空充能
            NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(3));
        }
    }

    private static void resetCharge() {
        isCharging = false;
        chargeStartDelay = 0;
    }

    /**
     * 获取客户端充能状态（供HUD渲染使用）
     */
    public static boolean isCharging() {
        return isCharging;
    }
}
