package com.gytrinket.gytrinket.client.effect.particle;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ShieldParticleTickEvent {

    /** 开发用：冻结粒子年龄，便于在固定姿态下截图观察 */
    public static boolean freezeAging = false;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleTickEvent::onClientTick);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (freezeAging) {
            return;
        }
        ShieldParticleTimerManager.getInstance().tick();
        ShieldParticleRenderManager.getInstance().tick();
    }
}
