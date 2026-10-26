package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进入世界时去掉"实验性设置"警告（1.19.2 版实现）。
 * <p>
 * {@code WorldOpenFlows#doLoadLevel} 里
 * <pre>boolean flag1 = worlddata.worldGenSettingsLifecycle() != Lifecycle.stable();</pre>
 * 只要为真就会进入"使用实验性设置的世界已不受支持"的备份确认流程。
 * <p>
 * 为什么无条件覆写：本模组任何时候都带 {@code gytrinket:island} 维度（数据包注册），
 * 而 1.19.2 切换世界类型（超平坦等）会用 {@code WorldPreset.recreateWorldGenSettings()} 的
 * 固定三维度表重建 worldGenSettings，island 会从维度表里消失；按 island 判断会漏掉这类世界。
 * 本模组自带自定义维度，"维度表只有原版三个"的稳定性判定对它天然不成立，
 * 因此统一按 stable 处理（产品取舍：会连带吞掉"与本模组无关的实验性数据包"警告）。
 */
@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin {

    @Inject(method = "worldGenSettingsLifecycle", at = @At("HEAD"), cancellable = true)
    private void gytrinket$stableLifecycle(CallbackInfoReturnable<Lifecycle> cir) {
        WorldData self = (WorldData) (Object) this;
        if (self.worldGenSettings() != null) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
