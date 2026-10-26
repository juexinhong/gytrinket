package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 新建世界时去掉"实验性设置"警告（1.19.2 版实现）。
 * <p>
 * 1.19.2 的 {@code WorldGenSettings.CODEC} 解码后会调用 {@code guardExperimental()}：
 * 它用 {@code LevelStem.stable(dimensions)} 判断"维度表是否刚好只有原版三个"，
 * 因此任何数据包额外维度（本模组的 {@code gytrinket:island}）都会让它返回
 * {@code DataResult.success(this)}——而 1.19.2 捆绑的 DFU 里这个无 Lifecycle 重载默认是
 * {@link Lifecycle#experimental()}。
 * <p>
 * 这条 lifecycle 一路传到 {@code CreateWorldScreen.onCreate()} →
 * {@code WorldOpenFlows.confirmWorldCreation(...)}，于是每次点"创建新的世界"都会弹
 * "这些设置是实验性的"确认框（{@code PrimaryLevelDataMixin} 只覆盖了打开已有世界的路径）。
 * <p>
 * 这里在 {@code guardExperimental} 入口直接钉成 {@link Lifecycle#stable()}，
 * 但**仅当维度表里确实包含本模组的 {@code gytrinket:island}**，避免把别的实验性数据包警告一起吞掉。
 */
@Mixin(WorldGenSettings.class)
public abstract class WorldGenSettingsMixin {

    private static final ResourceKey<LevelStem> GYTRINKET_ISLAND =
            ResourceKey.create(Registry.LEVEL_STEM_REGISTRY, new ResourceLocation("gytrinket", "island"));

    private static boolean gytrinket$loggedStable;

    @Inject(method = "guardExperimental", at = @At("HEAD"), cancellable = true)
    private void gytrinket$stableLifecycleForIslandDimension(CallbackInfoReturnable<DataResult<WorldGenSettings>> cir) {
        WorldGenSettings self = (WorldGenSettings) (Object) this;
        if (self.dimensions() == null || !self.dimensions().containsKey(GYTRINKET_ISLAND)) {
            return;
        }
        if (!gytrinket$loggedStable) {
            gytrinket$loggedStable = true;
            com.gytrinket.gytrinket.gytrinket.LOGGER.info(
                    "[worldgen] 维度表包含 gytrinket:island，实验性设置 lifecycle 按 stable 处理（不弹警告）");
        }
        cir.setReturnValue(DataResult.success(self, Lifecycle.stable()));
    }
}
