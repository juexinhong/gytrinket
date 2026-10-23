package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.core.world.IslandTeleporter;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link LevelMixin} 的 lightmap 对应物：孤岛的静态天空光为 7 级，
 * 因此 lightmap 固定使用 7/15，而不是随昼夜变化的 daylight 值。
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