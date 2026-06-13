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
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 充能攻击客户端输入处理
 * <p>
 * 核心设计：充能攻击的启动判定在客户端 {@link InputEvent.InteractionKeyMappingTriggered} 中完成，
 * 在原版攻击逻辑执行之前拦截并启动充能，从根源上消除客户端-服务端竞态条件。
 * <p>
 * 客户端负责：
 * 1. 在攻击键按下的瞬间，判定是否启动充能并取消原版攻击
 * 2. 充能期间持续拦截所有攻击输入
 * 3. 松开时释放充能攻击
 * <p>
 * 充能值计算完全由服务端负责，客户端通过 SyncChargedAttackMessage 同步用于HUD显示。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackInputHandler {

    // 客户端充能状态
    private static boolean isCharging = false;

    /**
     * 客户端攻击输入拦截 - 充能攻击的核心入口
     * <p>
     * 在原版攻击逻辑执行之前触发，同时完成充能启动判定和攻击拦截，
     * 消除了依赖 ClientTickEvent 与 handleKeybinds 执行顺序的时序问题。
     * <p>
     * 判定逻辑：
     * - 正在充能中 → 取消攻击
     * - 拥有充能攻击物品 + 攻击强度满 + 不在连击冷却 + 未在充能 → 启动充能并取消攻击
     * - 其他情况 → 允许原版攻击通过
     */
    @SubscribeEvent
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

        AttackStateManager.AttackState attackState = AttackStateInputHandler.getCurrentState();
        if (attackState == AttackStateManager.AttackState.RELEASED && isCharging) {
            // 松开左键 - 释放充能攻击
            releaseAttack(player, minecraft);
        }
        // 注意：PRESSED 状态的充能启动已移至 onAttackKeyInput，此处不再处理
        // 注意：攻击强度反射已移除，因为攻击输入在客户端被取消，攻击强度不会被消耗
    }

    private static void startCharging(Player player) {
        isCharging = true;
        // 通知服务端开始充能
        NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ChargedAttackMessage(0));
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
    }

    /**
     * 获取客户端充能状态（供HUD渲染使用）
     */
    public static boolean isCharging() {
        return isCharging;
    }
}
