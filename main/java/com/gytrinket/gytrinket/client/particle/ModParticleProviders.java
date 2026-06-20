package com.gytrinket.gytrinket.client.particle;

import com.gytrinket.gytrinket.particle.ModParticles;
import com.gytrinket.gytrinket.particle.ReflectShieldParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "gytrinket", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModParticleProviders {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.REFLECT_SHIELD_PARTICLE.get(), ReflectShieldParticle.Provider::new);
    }
}
