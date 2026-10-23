package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.world.IslandTeleporter;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lightmap counterpart of {@link LevelMixin}: the island's static sky light is
 * level 7, so the lightmap uses 7/15 instead of the moving daylight value.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "getSkyDarken(F)F", at = @At("HEAD"), cancellable = true)
    private void gytrinket$islandLightmapSkyDarken(float partialTick, CallbackInfoReturnable<Float> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            cir.setReturnValue(7.0F / 15.0F);
        }
    }
}
