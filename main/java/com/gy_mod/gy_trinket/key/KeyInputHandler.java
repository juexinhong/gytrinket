package com.gy_mod.gy_trinket.key;

import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 按键输入处理器
 * 处理玩家按键输入，当按下 G 键时向服务端请求属性
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class KeyInputHandler {
    // 按键绑定：G 键
    private static KeyMapping attributeKey;

    /**
     * 静态初始化按键绑定
     */
    static {
        // 创建按键绑定：G 键，无修饰键，类别为游戏玩法
        attributeKey = new KeyMapping(
            "key.gytrinket.show_attributes",  // 按键名称（用于本地化）
            InputConstants.Type.KEYSYM,        // 按键类型：键盘
            InputConstants.KEY_G,              // 按键：G
            "category.gytrinket.gameplay"      // 类别：游戏玩法
        );
    }

    /**
     * 注册按键绑定
     * @param event 注册按键事件
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 使用 Forge 提供的事件注册按键绑定
        event.register(attributeKey);
        gytrinket.LOGGER.info("属性显示按键绑定已注册：G 键");
    }

    /**
     * 监听客户端 Tick 事件
     * 在每个客户端 Tick 中检查按键是否被按下
     * @param event Tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 只在 Tick 的开始阶段处理
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        // 检查是否按下了属性显示键
        if (attributeKey.consumeClick()) {
            // 获取当前玩家
            var player = Minecraft.getInstance().player;
            if (player != null) {
                // 向服务端发送请求属性的消息
                NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.RequestAttributesMessage());
                gytrinket.LOGGER.info("向服务端发送属性请求");
            }
        }
    }

    /**
     * 获取按键绑定对象
     * @return 按键绑定
     */
    public static KeyMapping getAttributeKey() {
        return attributeKey;
    }
}