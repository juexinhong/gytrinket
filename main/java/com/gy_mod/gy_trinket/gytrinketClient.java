package com.gy_mod.gy_trinket;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.ClientSyncRequestMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// 客户端初始化入口。实际的客户端事件注册（渲染器、按键、粒子等）在 client.ModClient 中处理
// 本类不会在专用服务器上加载
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class gytrinketClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
    }

    /** 进入世界时主动请求定义同步（登录阶段服务端推送可能早于监听器就绪） */
    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        NetworkHandler.INSTANCE.sendToServer(new ClientSyncRequestMessage());
    }
}
