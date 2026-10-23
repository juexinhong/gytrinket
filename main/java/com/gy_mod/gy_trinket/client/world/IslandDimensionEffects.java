package com.gy_mod.gy_trinket.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
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
        // SkyType.NONE 告诉光影包 / 其它天空渲染器：本维度没有原版样式的天空。
        // 自己的全屏天空由 IslandSky 负责，光影包不得替换它。
        super(192.0F, true, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // 固定为星云底部的深紫蓝霾；不随日照变色，保证地平线与地形雾一致。
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
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        // 无光影时只记录状态，绘制推迟到 AFTER_CUTOUT_BLOCKS；
        // 有光影时这里立即绘制，保证光影包不接管孤岛天空。
        IslandSky.prepare(level, partialTick, camera, isFoggy, projectionMatrix);
        return true;
    }

    /** 本维度不绘制雨雪。 */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    /** 本维度不做客户端降雨 tick。 */
    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }
}