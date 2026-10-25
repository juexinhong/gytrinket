package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.SwitchDroneArrayMessage;
import com.gytrinket.gytrinket.network.packet.ToggleExecuteMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (arraySwitchKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SwitchDroneArrayMessage());
        }

        if (executeToggleKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new ToggleExecuteMessage());
        }
    }
}
