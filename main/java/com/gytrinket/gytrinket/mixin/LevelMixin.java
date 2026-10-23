package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.world.IslandTeleporter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The island has no day/night cycle.  Keep the effective sky light at level 7
 * (raw sky light 15 minus a constant sky darkening of 8) for both gameplay
 * light checks and the client lightmap.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true)
    private void gytrinket$islandSkyDarken(CallbackInfoReturnable<Integer> cir) {
        Level level = (Level) (Object) this;
        if (level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            cir.setReturnValue(8);
        }
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void gytrinket$islandDayTime(CallbackInfoReturnable<Long> cir) {
        Level level = (Level) (Object) this;
        if (level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            cir.setReturnValue(16000L);
        }
    }
}
