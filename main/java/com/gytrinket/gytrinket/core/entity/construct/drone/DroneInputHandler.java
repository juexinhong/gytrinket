package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.SwitchDroneArrayMessage;
import com.gytrinket.gytrinket.network.packet.ToggleExecuteMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 无人机输入处理器
 * <p>
 * 处理玩家输入来控制无人机：
 * <ul>
 *   <li>TAB键：切换阵列模式</li>
 *   <li>I键：切换斩杀归属开关</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class DroneInputHandler {
    private static KeyMapping arraySwitchKey;
    private static KeyMapping executeToggleKey;

    static {
        arraySwitchKey = new KeyMapping(
            "key.gytrinket.switch_array",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_TAB,
            "category.gytrinket.gameplay"
        );

        executeToggleKey = new KeyMapping(
            "key.gytrinket.toggle_execute",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_I,
            "category.gytrinket.gameplay"
        );
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(arraySwitchKey);
        event.register(executeToggleKey);
    }

    /**
     * 阵列切换按键的原始输入通道。
     * <p>
     * Forge 1.19.2 的 {@code KeyMapping.click(Key)} 只给 MAP 中该按键的**最后一个** mapping 累加 clickCount；
     * 原版 keyPlayerList 与我们的默认 TAB 冲突且注册更晚，导致 TAB 的点击全被玩家列表吃掉、阵列永远切不了。
     * 这里用 Forge 的原始按键事件按 mapping 当前绑定键（支持改键）直接触发，绕开 MAP 的单键单映射限制。
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (arraySwitchKey.matches(event.getKey(), event.getScanCode())) {
            gytrinket.LOGGER.info("[drone] array switch raw key pressed -> send SwitchDroneArrayMessage");
            NetworkHandler.INSTANCE.sendToServer(new SwitchDroneArrayMessage());
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

        // 阵列切换走 onKeyInput 原始事件（TAB 与原版玩家列表冲突，consumeClick 收不到点击），
        // 这里只处理斩杀开关。
        if (executeToggleKey.consumeClick()) {
            gytrinket.LOGGER.info("[drone] execute toggle key pressed -> send ToggleExecuteMessage");
            NetworkHandler.INSTANCE.sendToServer(new ToggleExecuteMessage());
        }
    }
}
