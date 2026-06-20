package com.gytrinket.gytrinket.client.effect.particle;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ShieldParticleTickEvent {

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ShieldParticleTickEvent::onClientTick);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ShieldParticleTimerManager.getInstance().tick();
        ShieldParticleRenderManager.getInstance().tick();
    }
}
