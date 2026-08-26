package com.gy_mod.gy_trinket.client.particle;

import com.gy_mod.gy_trinket.particle.ModParticles;
import com.gy_mod.gy_trinket.particle.ReflectShieldParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModParticleProviders {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.REFLECT_SHIELD_PARTICLE.get(), ReflectShieldParticle.Provider::new);
    }
}
