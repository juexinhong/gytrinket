package com.gy_mod.gy_trinket.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, "gytrinket");

    public static final RegistryObject<SimpleParticleType> REFLECT_SHIELD_PARTICLE = PARTICLE_TYPES.register("reflect_shield", () -> new SimpleParticleType(false));
}