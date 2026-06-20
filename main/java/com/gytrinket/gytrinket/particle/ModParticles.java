package com.gytrinket.gytrinket.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, "gytrinket");

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REFLECT_SHIELD_PARTICLE = PARTICLE_TYPES.register("reflect_shield", () -> new SimpleParticleType(false));
}
