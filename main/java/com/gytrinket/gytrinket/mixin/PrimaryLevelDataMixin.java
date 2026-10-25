package com.gytrinket.gytrinket.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进入带自定义维度的世界时，去掉"实验性设置"警告（1.19.2 版实现）。
 * <p>
 * 1.20.1 分支用 {@code WorldDimensionsMixin} 把 level-stem 注册表的 lifecycle 钉成 stable；
 * 1.19.2 没有 {@code WorldDimensions} 类，但判定入口同样清晰：
 * {@code WorldOpenFlows#doLoadLevel} 里
 * <pre>boolean flag1 = worlddata.worldGenSettingsLifecycle() != Lifecycle.stable();</pre>
 * 只要为真就会弹出"使用实验性设置的世界已不受支持"确认框。
 * <p>
 * 这里在 {@link PrimaryLevelData} 上把这个 lifecycle 覆写成 stable —— 但**仅当该世界的维度表里
 * 确实包含本模组的 {@code gytrinket:island}**，避免顺手把别的实验性数据包警告也一起吞掉。
 */
@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin {

    private static final ResourceLocation GYTRINKET_ISLAND = new ResourceLocation("gytrinket", "island");

    @Inject(method = "worldGenSettingsLifecycle", at = @At("HEAD"), cancellable = true)
    private void gytrinket$stableLifecycleForIslandDimension(CallbackInfoReturnable<Lifecycle> cir) {
        WorldData self = (WorldData) (Object) this;
        if (self.worldGenSettings().dimensions().containsKey(GYTRINKET_ISLAND)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
