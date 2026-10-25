package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.client.projectile.ClientProjectileScaleCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弹射物模型缩放
 * <p>
 * 在 EntityRenderDispatcher.render 内部、vanilla 已完成 pushPose + translate
 * （平移到实体渲染位置）之后、具体渲染器绘制之前注入：
 * 再 push 一层并缩放，缩放中心为实体位置本身。
 * <p>
 * 不能在方法 HEAD（世界原点）处缩放：vanilla 的 translate 发生在缩放之后，
 * 平移向量会被一起放大/缩小，模型会被甩离真实位置（视觉上"消失"）。
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    private static final String RENDER_METHOD =
        "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String RENDERER_RENDER_TARGET =
        "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Unique
    private boolean gytrinket$scalePushed = false;

    @Inject(method = RENDER_METHOD, at = @At(value = "INVOKE", target = RENDERER_RENDER_TARGET))
    private void gytrinket$pushProjectileScale(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Projectile)) {
            return;
        }

        // 缓存优先（服务端权威值）；本地玩家自己的弹射物在包未到时本地即时推导（零滞后）
        float scale = ClientProjectileScaleCache.getRenderScale((Projectile) entity);
        if (scale == 1.0F) {
            return;
        }

        gytrinket$scalePushed = true;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = RENDER_METHOD, at = @At(value = "INVOKE", target = RENDERER_RENDER_TARGET, shift = At.Shift.AFTER))
    private void gytrinket$popProjectileScale(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (gytrinket$scalePushed) {
            gytrinket$scalePushed = false;
            poseStack.popPose();
        }
    }
}
