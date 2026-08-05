package com.gytrinket.gytrinket.client.weapon.flamespear;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 焰矛模拟充能客户端检测。
 * <p>
 * 客户端检测右键按键状态与手持焰矛，状态变化时发送数据包到服务端。
 * 不进入真实"使用物品"状态，避免移动减速。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class FlameSpearClientHandler {

    private static boolean wasUsing = false;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player) {
            return;
        }

        boolean using = Minecraft.getInstance().options.keyUse.isDown()
                && !player.getMainHandItem().isEmpty()
                && Config.isFlameSpearItem(player.getMainHandItem().getItem());

        if (using != wasUsing) {
            wasUsing = using;
            NetworkHandler.sendSimulatedUsingToServer(player.getId(), using);
        }
    }
}
