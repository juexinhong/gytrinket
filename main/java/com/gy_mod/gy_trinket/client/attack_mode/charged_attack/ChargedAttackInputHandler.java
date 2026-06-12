package com.gy_mod.gy_trinket.client.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.client.attack_mode.AttackModeClientUtil;
import com.gy_mod.gy_trinket.client.attack_mode.AttackStateInputHandler;
import com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler;
import com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 充能攻击客户端输入处理
 * <p>
 * 客户端只负责：
 * 1. 检测 PRESSED 状态并通知服务端开始充能
 * 2. 充能期间处理攻击强度反射
 * 3. 松开时释放充能攻击
 * <p>
 * 充能值计算完全由服务端负责，客户端通过 SyncChargedAttackMessage 同步用于HUD显示。
 * 攻击禁用由服务端 AttackModeManager 处理。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackInputHandler {

    // 客户端充能状态
    private static boolean isCharging = false;
    // 充能期间是否已完成首次反射（攻击强度首次低于0.5时反射为1）
    private static boolean hasReflectedOnce = false;

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

        AttackStateManager.AttackState attackState = AttackStateInputHandler.getCurrentState();
        if (attackState == AttackStateManager.AttackState.PRESSED) {
            // 刚按下左键 - 检查攻击强度是否满，满足则开始充能
            float attackStrength = player.getAttackStrengthScale(0.0F);
            if (attackStrength >= 1.0F) {
                // 连击冷却期间禁止充能
                if (!BurstFireClientHandler.isInComboCooldown(player.getUUID())) {
                    startCharging(player);
                }
            }
        } else if (attackState == AttackStateManager.AttackState.HELD) {
            // 持续按住 - 处理攻击强度反射
            if (isCharging) {
                updateCharging(player);
            }
        } else if (attackState == AttackStateManager.AttackState.RELEASED && isCharging) {
            // 松开左键 - 释放充能攻击
            releaseAttack(player, minecraft);
        }
    }

    private static void startCharging(Player player) {
        isCharging = true;
        hasReflectedOnce = false;
        // 通知服务端开始充能
        NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(0));
    }

    private static void updateCharging(Player player) {
        float attackStrength = player.getAttackStrengthScale(0.0F);

        // 充能期间首次攻击强度低于0.5时，反射为1
        if (!hasReflectedOnce && attackStrength < 0.5F) {
            AttackModeClientUtil.reflectAttackStrengthToFull(player);
            hasReflectedOnce = true;
            attackStrength = 1.0F;
        }

        // 攻击强度小于1时暂停充能（通知服务端）
        if (attackStrength < 1.0F) {
            return;
        }

        // 通知服务端继续充能
        NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(1));
    }

    private static void releaseAttack(Player player, Minecraft minecraft) {
        resetCharge();

        // 寻找准星对准的目标
        Entity target = AttackModeClientUtil.findTargetInCrosshair(player);
        if (target instanceof LivingEntity) {
            // 有目标：发送释放消息
            NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(2));

            // 客户端执行攻击
            minecraft.gameMode.attack(player, target);

            // 重置攻击强度
            AttackModeClientUtil.resetAttackStrengthTicker(player);
        } else {
            // 无目标：发送取消消息，清空充能
            NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(3));
        }
    }

    private static void resetCharge() {
        isCharging = false;
        hasReflectedOnce = false;
    }

    /**
     * 获取客户端充能状态（供HUD渲染使用）
     */
    public static boolean isCharging() {
        return isCharging;
    }
}
