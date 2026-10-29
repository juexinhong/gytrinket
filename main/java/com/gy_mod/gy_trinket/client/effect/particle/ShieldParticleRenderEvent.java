package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 护盾粒子渲染事件分发。
 * <p>
 * 光影模式下采用两阶段渲染（与 1.21.1 对齐）：
 * - AFTER_PARTICLES（Iris composite 之前）：仅保存正确的矩阵，不渲染
 * - AFTER_LEVEL（Iris composite 之后）：使用保存的矩阵渲染
 * <p>
 * 原因：AFTER_PARTICLES 阶段 Iris 的 shouldOverrideShaders()=true，
 * 未知着色器 apply() 的 TAIL 会调用 DepthColorStorage.disableDepthColor() 禁用 colorMask，
 * 导致颜色无法写入。AFTER_LEVEL 阶段 shouldOverrideShaders()=false，colorMask 不受影响。
 */
public class ShieldParticleRenderEvent {

    // 在AFTER_PARTICLES时保存的矩阵状态（Iris composite之前的正确值）
    private static Matrix4f savedProjectionMatrix = null;
    private static Matrix4f savedModelViewMatrix = null;
    private static Matrix4f savedPoseStackMatrix = null;
    /** AFTER_PARTICLES 时保存的插值时间，补画时直接复用 */
    private static float savedPartialTicks = 0.0F;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (ShaderModCompat.isRenderingShadows()) return;

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();

        if (shaderActive) {
            // 光影：composite 之前只保存矩阵，等 AFTER_LEVEL（composite 之后）再画。
            // 不能只看“体积渲染开关”：平面四边形回退（style 2）用的也是原版 ShaderInstance，
            // 同样会在 Iris 的 depth/color lock 下不可见。
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                // Iris composite之前：保存正确的矩阵状态
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
                savedPartialTicks = event.getPartialTick();
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // Iris composite之后：使用保存的矩阵渲染
                ShieldParticleRenderer.renderWithSavedMatrices(event.getPoseStack(), null, null, savedPartialTicks,
                        savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // 无光影 或 非体积渲染：正常渲染
            float partialTicks = event.getPartialTick();
            ShieldParticleRenderer.render(event.getPoseStack(), null, null, partialTicks);
        }
    }
}
