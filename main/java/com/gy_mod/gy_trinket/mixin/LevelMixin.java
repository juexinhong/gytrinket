package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.core.world.IslandTeleporter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 孤岛维度没有昼夜循环。把生效天空光钳在 7 级
 * （原始 15 减常量 8），服务端光照判定与客户端 lightmap 使用同一数值。
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