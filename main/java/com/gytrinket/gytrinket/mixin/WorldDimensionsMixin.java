package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版在 WorldDimensions 中硬编码：维度总数不为 3（overworld/nether/end）时，
 * 整个 level_stem registry 标记为 experimental；且任何非原版维度 key 的 stem
 * 也会被标记 experimental。导致带自定义维度的世界弹"实验性内容"警告。
 * <p>
 * 这里将两处判定都强制为 stable，消除创建/进入带自定义维度世界时的实验性警告。
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {

    /**
     * bake() 构造 level_stem registry 时的总体 lifecycle：
     * 原版为 维度数==3 ? stable : experimental，这里恒为 stable。
     */
    @Redirect(
            method = "bake",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Lifecycle;experimental()Lcom/mojang/serialization/Lifecycle;"
            )
    )
    private Lifecycle gytrinket$bakeRegistryStable() {
        return Lifecycle.stable();
    }

    /**
     * 每个维度的 RegistrationInfo lifecycle：原版对非原版维度硬编码 experimental，
     * 这里恒为 stable（否则其他 mod 维度会把 registry lifecycle 拉回 experimental）。
     */
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void gytrinket$checkStabilityStable(ResourceKey<LevelStem> key, LevelStem stem,
                                                       CallbackInfoReturnable<Lifecycle> cir) {
        cir.setReturnValue(Lifecycle.stable());
    }
}
