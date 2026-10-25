package com.gytrinket.gytrinket.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.Registry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registry.PARTICLE_TYPE_REGISTRY, "gytrinket");

    public static final RegistryObject<SimpleParticleType> REFLECT_SHIELD_PARTICLE = PARTICLE_TYPES.register("reflect_shield", () -> new SimpleParticleType(false));
}