package com.gy_mod.gy_trinket.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * VertexConsumer 包装器：将顶点颜色的 alpha 通道乘以指定系数。
 * <p>
 * 用于幽灵机身透明度渲染：在 Iris 光影下，setShaderColor 的 alpha 会因
 * FullyBufferedMultiBufferSource 延迟 endBatch 而在绘制时被重置。
 * 通过直接修改顶点颜色 alpha，绕过 shaderColor 时序问题。
 * <p>
 * 注意：返回 this 而非 delegate，确保链式调用中的 color() 也会被拦截。
 */
public class GhostAlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alphaMultiplier;

    public GhostAlphaVertexConsumer(VertexConsumer delegate, float alphaMultiplier) {
        this.delegate = delegate;
        this.alphaMultiplier = alphaMultiplier;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix4f, float x, float y, float z) {
        delegate.vertex(matrix4f, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int color) {
        // ABGR packed int：提取 alpha，乘以系数，重新打包
        int a = (color >> 24) & 0xFF;
        int newA = (int) (a * alphaMultiplier);
        delegate.color((newA << 24) | (color & 0x00FFFFFF));
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color(r, g, b, (int) (a * alphaMultiplier));
        return this;
    }

    @Override
    public VertexConsumer color(float r, float g, float b, float a) {
        delegate.color(r, g, b, a * alphaMultiplier);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int uv) {
        delegate.overlayCoords(uv);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int uv) {
        delegate.uv2(uv);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
