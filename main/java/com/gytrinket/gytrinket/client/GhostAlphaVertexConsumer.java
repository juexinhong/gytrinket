package com.gytrinket.gytrinket.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * VertexConsumer 包装器：将顶点颜色的 alpha 通道乘以指定系数。
 * <p>
 * 用于幽灵机身透明度渲染：在 Iris 光影下，setShaderColor 的 alpha 会因
 * FullyBufferedMultiBufferSource 延迟 endBatch 而在绘制时被重置。
 * 通过直接修改顶点颜色 alpha，绕过 shaderColor 时序问题。
 * <p>
 * 注意：返回 this 而非 delegate，确保链式调用中的 setColor() 也会被拦截。
 */
public class GhostAlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alphaMultiplier;

    public GhostAlphaVertexConsumer(VertexConsumer delegate, float alphaMultiplier) {
        this.delegate = delegate;
        this.alphaMultiplier = alphaMultiplier;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        // ABGR packed int：提取 alpha，乘以系数，重新打包
        int a = (color >> 24) & 0xFF;
        int newA = (int) (a * alphaMultiplier);
        delegate.setColor((newA << 24) | (color & 0x00FFFFFF));
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, (int) (a * alphaMultiplier));
        return this;
    }

    @Override
    public VertexConsumer setColor(float r, float g, float b, float a) {
        delegate.setColor(r, g, b, a * alphaMultiplier);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int overlay) {
        delegate.setOverlay(overlay);
        return this;
    }

    @Override
    public VertexConsumer setLight(int light) {
        delegate.setLight(light);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
