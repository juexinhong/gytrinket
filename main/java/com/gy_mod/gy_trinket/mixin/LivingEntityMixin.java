package com.gy_mod.gy_trinket.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    private static final TagKey<DamageType> NO_HURT_EFFECT = TagKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation("gytrinket", "no_hurt_effect")
    );

    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onPlayHurtSound(DamageSource source, CallbackInfo ci) {
        if (source.is(NO_HURT_EFFECT)) {
            ci.cancel();
        }
    }
}
