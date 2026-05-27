package com.gy_mod.gy_trinket.client.effect.particle;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ShieldParticleTickEvent {
    
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleTickEvent::onClientTick);
    }
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ShieldParticleTimerManager.getInstance().tick();
            ShieldParticleRenderManager.getInstance().tick();
        }
    }
}