package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 新建世界时去掉"实验性设置"确认框（1.19.2 版实现，客户端）。
 * <p>
 * {@code CreateWorldScreen.onCreate()} 直接把
 * {@code worldGenSettingsComponent.settings().worldSettingsStability()} 交给
 * {@code WorldOpenFlows.confirmWorldCreation(...)}：stable 直接建世界，
 * {@code experimental} 弹"这些设置是实验性的"确认框。
 * <p>
 * 这个 stability 由数据包应用阶段重新计算（{@code WorldGenSettings.CODEC} 解码 lifecycle
 * 再 {@code .add(registryAccess.allElementsLifecycle())}）。本模组的 {@code gytrinket:island}
 * 维度会让"维度表只有原版三个"的稳定性判定失败，注册表元素生命周期也会被拖成 experimental，
 * 于是每次新建世界都弹框。上游 {@code WorldGenSettings.guardExperimental()} 的修正
 * （{@link WorldGenSettingsMixin}）不足以覆盖 {@code allElementsLifecycle} 这一层，
 * 因此这里在消费端直接钉成 stable。
 * <p>
 * 仍按维度表是否包含 {@code gytrinket:island} 限定，避免把与模组无关的实验性数据包警告一起吞掉。
 */
@Mixin(WorldCreationContext.class)
public abstract class WorldCreationContextMixin {

    private static final ResourceKey<LevelStem> GYTRINKET_ISLAND =
            ResourceKey.create(Registry.LEVEL_STEM_REGISTRY, new ResourceLocation("gytrinket", "island"));

    @Inject(method = "worldSettingsStability", at = @At("HEAD"), cancellable = true)
    private void gytrinket$stableSettingsForIslandDimension(CallbackInfoReturnable<Lifecycle> cir) {
        WorldCreationContext self = (WorldCreationContext) (Object) this;
        if (self.worldGenSettings() != null
                && self.worldGenSettings().dimensions() != null
                && self.worldGenSettings().dimensions().containsKey(GYTRINKET_ISLAND)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
