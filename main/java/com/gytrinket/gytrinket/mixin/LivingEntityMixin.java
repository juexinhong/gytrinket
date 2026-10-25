package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.damage.ModDamageTypes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onPlayHurtSound(DamageSource source, CallbackInfo ci) {
        if (ModDamageTypes.hasNoHurtEffect(source)) {
            ci.cancel();
        }
    }
}

