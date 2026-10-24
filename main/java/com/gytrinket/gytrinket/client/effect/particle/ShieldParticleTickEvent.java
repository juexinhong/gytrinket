package com.gytrinket.gytrinket.client.effect.particle;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ShieldParticleTickEvent {

    /** 开发用：冻结粒子年龄，便于在固定姿态下截图观察 */
    public static boolean freezeAging = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ShieldParticleTickEvent::onClientTick);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (freezeAging) {
            return;
        }
        ShieldParticleTimerManager.getInstance().tick();
        ShieldParticleRenderManager.getInstance().tick();
    }
}
