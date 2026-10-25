package com.gytrinket.gytrinket.client.particle;

import com.gytrinket.gytrinket.particle.ModParticles;
import com.gytrinket.gytrinket.particle.ReflectShieldParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModParticleProviders {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.register(ModParticles.REFLECT_SHIELD_PARTICLE.get(), ReflectShieldParticle.Provider::new);
    }
}
