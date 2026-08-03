package com.gytrinket.gytrinket.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * MultiBufferSource 包装器：将 getBuffer 调用重定向到 entityTranslucent，
 * 并返回 GhostAlphaVertexConsumer 修改顶点颜色 alpha。
 * <p>
 * 用于幽灵机身透明度渲染：
 * - 将 entityCutoutNoCull 替换为 entityTranslucent，启用半透明混合（TRANSLUCENT_TRANSPARENCY）
 * - 通过顶点颜色 alpha 实现透明度，绕过 Iris 下 setShaderColor 的 endBatch 时序问题
 * <p>
 * 注意：所有 RenderType 都会被替换为 entityTranslucent(texture)。
 * 对于装备层等使用自己 RenderType 的层，会使用玩家皮肤纹理，
 * 但由于装备层通常使用不同的 RenderType（如 armorCutoutNoCull），
 * 不会被此包装器影响（因为 getBuffer 会被调用不同的 RenderType）。
 * <p>
 * 实际上，此包装器只在手动渲染时使用，且只影响 PlayerRenderer.render 内部的 getBuffer 调用。
 * PlayerRenderer 的 super.render (LivingEntityRenderer.render) 中的 getBuffer 调用
 * 使用 getRenderType 返回的 RenderType（通常是 entityCutoutNoCull），
 * 会被替换为 entityTranslucent。其他层（如 CapeLayer、Deadmau5HeadLayer）使用自己的 RenderType。
 */
public class GhostAlphaBufferSource implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final float alpha;
    private final ResourceLocation texture;

    public GhostAlphaBufferSource(MultiBufferSource delegate, float alpha, ResourceLocation texture) {
        this.delegate = delegate;
        this.alpha = alpha;
        this.texture = texture;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        // 将所有 RenderType 替换为 entityTranslucent(texture)
        // 这样玩家模型会使用半透明混合（TRANSLUCENT_TRANSPARENCY）
        RenderType translucentType = RenderType.entityTranslucent(texture);
        VertexConsumer original = delegate.getBuffer(translucentType);
        return new GhostAlphaVertexConsumer(original, alpha);
    }
}
