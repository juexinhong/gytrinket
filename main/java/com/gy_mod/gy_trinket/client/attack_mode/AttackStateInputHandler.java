package com.gy_mod.gy_trinket.client.attack_mode;

import com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 攻击状态系统 - 客户端输入处理
 * <p>
 * 检测玩家左键的三种状态：PRESSED（按下）、HELD（按住）、RELEASED（松开）
 * 并通过网络同步到服务端，供其他系统查询
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class AttackStateInputHandler {

    private static AttackStateManager.AttackState currentState = AttackStateManager.AttackState.RELEASED;
    private static AttackStateManager.AttackState previousState = AttackStateManager.AttackState.RELEASED;
    private static int holdTicks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        boolean isLeftClickDown = minecraft.options.keyAttack.isDown();

        previousState = currentState;

        if (isLeftClickDown) {
            if (previousState == AttackStateManager.AttackState.RELEASED) {
                // 从松开到按下 -> PRESSED
                currentState = AttackStateManager.AttackState.PRESSED;
                holdTicks = 0;
            } else {
                // 已经在按下/按住状态 -> HELD
                currentState = AttackStateManager.AttackState.HELD;
                holdTicks++;
            }
        } else {
            if (previousState != AttackStateManager.AttackState.RELEASED) {
                // 从按下/按住到松开 -> RELEASED
                currentState = AttackStateManager.AttackState.RELEASED;
                holdTicks = 0;
            }
        }

        // 同步状态到服务端
        syncToServer();
    }

    private static void syncToServer() {
        NetworkHandler.INSTANCE.sendToServer(
            new NetworkHandler.AttackStateMessage(currentState.ordinal(), holdTicks)
        );
    }

    /**
     * 获取当前攻击状态（客户端）
     */
    public static AttackStateManager.AttackState getCurrentState() {
        return currentState;
    }

    /**
     * 获取按住时长（tick数）（客户端）
     */
    public static int getHoldTicks() {
        return holdTicks;
    }

    /**
     * 是否刚刚按下左键（边沿触发）（客户端）
     */
    public static boolean isJustPressed() {
        return currentState == AttackStateManager.AttackState.PRESSED;
    }

    /**
     * 是否正在按住左键（客户端）
     */
    public static boolean isHeld() {
        return currentState == AttackStateManager.AttackState.HELD || currentState == AttackStateManager.AttackState.PRESSED;
    }

    /**
     * 是否松开左键（客户端）
     */
    public static boolean isReleased() {
        return currentState == AttackStateManager.AttackState.RELEASED;
    }
}
