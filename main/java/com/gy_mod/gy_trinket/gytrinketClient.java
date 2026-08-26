package com.gy_mod.gy_trinket;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// 客户端初始化入口。实际的客户端事件注册（渲染器、按键、粒子等）在 client.ModClient 中处理
// 本类不会在专用服务器上加载
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class gytrinketClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        gytrinket.LOGGER.info("HELLO FROM CLIENT SETUP");
        gytrinket.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
