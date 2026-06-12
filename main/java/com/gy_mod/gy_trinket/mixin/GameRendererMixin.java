package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.client.MixinBridge;
import com.gy_mod.gy_trinket.client.shield.ShieldHudRenderer;
import com.gy_mod.gy_trinket.client.shield.type.SiphonClientData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onBobHurt(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        // 护盾>0时取消镜头摇晃
        double currentShield = ShieldHudRenderer.getInstance().getCurrentShield();
        if (currentShield > 0) {
            int[] protectedIds = SiphonClientData.getProtectedEntityIds();
            if (protectedIds == null || protectedIds.length == 0) {
                ci.cancel();
                return;
            }
        }

        // no_hurt_effect 伤害直接取消镜头摇晃
        if (MixinBridge.isSuppressBobHurt()) {
            ci.cancel();
        }
    }
}
