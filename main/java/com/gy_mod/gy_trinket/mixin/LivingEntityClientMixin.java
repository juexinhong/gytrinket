package com.gy_mod.gy_trinket.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityClientMixin {

    private static final TagKey<DamageType> NO_HURT_EFFECT = TagKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation("gytrinket", "no_hurt_effect")
    );

    @Unique
    private DamageSource gytrinket$currentDamageSource;

    @Unique
    private int gytrinket$savedHurtTime;

    @Unique
    private int gytrinket$savedHurtDuration;

    @Unique
    private float gytrinket$savedWalkSpeed;

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void gytrinket$captureState(DamageSource source, CallbackInfo ci) {
        gytrinket$currentDamageSource = source;
        if (source.is(NO_HURT_EFFECT)) {
            LivingEntity self = (LivingEntity) (Object) this;
            gytrinket$savedHurtTime = self.hurtTime;
            gytrinket$savedHurtDuration = self.hurtDuration;
            gytrinket$savedWalkSpeed = self.walkAnimation.speed();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void gytrinket$restoreState(DamageSource source, CallbackInfo ci) {
        gytrinket$currentDamageSource = null;
        if (source.is(NO_HURT_EFFECT)) {
            LivingEntity self = (LivingEntity) (Object) this;
            self.hurtTime = gytrinket$savedHurtTime;
            self.hurtDuration = gytrinket$savedHurtDuration;
            self.walkAnimation.setSpeed(gytrinket$savedWalkSpeed);
        }
    }

    @ModifyArg(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"), index = 1)
    private float gytrinket$silenceHurtSound(float volume) {
        if (gytrinket$currentDamageSource != null && gytrinket$currentDamageSource.is(NO_HURT_EFFECT)) {
            return 0.0F;
        }
        return volume;
    }
}
