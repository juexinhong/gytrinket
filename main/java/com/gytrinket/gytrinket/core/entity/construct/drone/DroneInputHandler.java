package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 无人机输入处理器
 * <p>
 * 处理玩家输入来控制无人机：
 * <ul>
 *   <li>TAB键：切换阵列模式</li>
 *   <li>I键：切换斩杀归属开关</li>
 * </ul>
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
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
        gytrinket.LOGGER.info("阵列切换按键绑定已注册：Tab 键");
        gytrinket.LOGGER.info("斩杀归属切换按键绑定已注册：I 键");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (arraySwitchKey.consumeClick()) {
            PacketDistributor.sendToServer(new NetworkHandler.SwitchDroneArrayPayload());
        }

        if (executeToggleKey.consumeClick()) {
            PacketDistributor.sendToServer(new NetworkHandler.ToggleExecutePayload());
        }
    }
}