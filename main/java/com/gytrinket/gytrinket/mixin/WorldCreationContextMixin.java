package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
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
 * 这个 stability 由数据包应用阶段计算（{@code WorldGenSettings.CODEC} 解码 lifecycle
 * 再 {@code .add(registryAccess.allElementsLifecycle())}）。
 * <p>
 * 为什么不再按"维度表包含 gytrinket:island"判断：1.19.2 切换世界类型（超平坦/放大化/单一群系等）
 * 会走 {@code WorldPreset.recreateWorldGenSettings()}，它用预设固定的三维度表（overworld/nether/end）
 * 重建 worldGenSettings，**本模组的 island 维度会从维度表里消失**（预设表本身也带
 * {@code Lifecycle.experimental()} 基底），因此按 island 判定必然漏掉非默认世界类型。
 * <p>
 * 本模组自带自定义维度，1.19.2 的"维度表必须正好只有原版三个才算 stable"判定对它天然不成立，
 * 所以这里统一按 stable 处理（产品取舍：会连带吞掉"与本模组无关的实验性数据包"确认框）。
 */
@Mixin(WorldCreationContext.class)
public abstract class WorldCreationContextMixin {

    @Inject(method = "worldSettingsStability", at = @At("HEAD"), cancellable = true)
    private void gytrinket$stableWorldSettings(CallbackInfoReturnable<Lifecycle> cir) {
        cir.setReturnValue(Lifecycle.stable());
    }
}
