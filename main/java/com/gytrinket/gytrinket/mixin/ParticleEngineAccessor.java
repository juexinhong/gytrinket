package com.gytrinket.gytrinket.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor("spriteSets")
    Map<ResourceLocation, SpriteSet> gytrinket$getSpriteSets();
}
