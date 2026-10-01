package com.gytrinket.gytrinket;

import com.gytrinket.gytrinket.network.packet.ClientSyncRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
// 注意：实际的客户端事件注册（渲染器、按键、粒子等）在 client.ModClient 中处理
@Mod(value = gytrinket.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class gytrinketClient {
    public gytrinketClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        gytrinket.LOGGER.info("HELLO FROM CLIENT SETUP");
        gytrinket.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    /**
     * 客户端进入世界时主动向服务端拉取定义覆盖层与物品属性表，使 AttributeManager
     * 静态属性表在重启进服后即可填充（物品属性 tooltip 无需先打开一次配置界面）。
     * 服务端登录事件推送作为第二道保险。
     */
    @SubscribeEvent
    static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        PacketDistributor.sendToServer(new ClientSyncRequestPayload());
    }
}
