package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 战场（孤岛）维度的天空与雾。
 * <p>
 * 天空由 {@link IslandSky} 在着色器里逐像素生成：倾斜的星云带、暗尘裂谷、
 * 多层星场与极光，覆盖完整球面并在地平线处与地形雾无缝衔接。
 * 这里只负责维度效果入口与雾色，不再绘制任何圆盘、环或渐变穹顶。
 */
public class IslandDimensionEffects extends DimensionSpecialEffects {

    public IslandDimensionEffects() {
        // SkyType.NONE tells shader packs / other sky renderers that this
        // dimension has no vanilla-style sky to draw.  Our own fullscreen sky
        // is still rendered from DimensionSpecialEffects.renderSky and the
        // deferred pass in IslandSky, so shader packs must not replace it.
        super(192.0F, true, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // 固定为星云底部的深紫蓝霾；不随日照变色，保证天空地平线与地形雾永远一致。
        return IslandSky.FOG_COLOR;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        // 自绘天空没有原版日月，也不需要原版的橙红日出染色。
        return null;
    }

    /** 这个维度不用原版云层，天空完全交给自绘天球。 */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick,
                                com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ,
                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView,
                             Camera camera, Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        if (ShaderModCompat.isShadersEnabled()) {
            // The real draw happens in GameRendererMixin after Iris' final
            // composite; here we only mark the sky visible.
            IslandSky.prepare(level, partialTick, camera, isFoggy);
            return true;
        }
        IslandSky.prepare(level, partialTick, camera, isFoggy);
        return true;
    }

    /** No rain or snow rendering in this dimension. */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    /** No client-side rain ticking/particles in this dimension. */
    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }
}
