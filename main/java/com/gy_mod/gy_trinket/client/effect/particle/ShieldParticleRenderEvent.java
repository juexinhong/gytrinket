package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

/**
 * 护盾粒子渲染事件分发。
 * <p>
 * 光影模式下采用两阶段渲染（与1.20.1对齐）：
 * - AFTER_PARTICLES（Iris composite之前）：仅保存正确的矩阵，不渲染
 * - AFTER_LEVEL（Iris composite之后）：使用保存的矩阵渲染
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

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (ShaderModCompat.isRenderingShadows()) return;

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();
        boolean volumetricEnabled = ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get();

        if (shaderActive && volumetricEnabled) {
            // 光影 + 体积渲染：两阶段矩阵保存/恢复
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                // Iris composite之前：保存正确的矩阵状态
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // Iris composite之后：使用保存的矩阵渲染
                float partialTicks = event.getPartialTick();
                PoseStack poseStack = event.getPoseStack();

                ShieldParticleRenderer.renderWithSavedMatrices(poseStack, null, null, partialTicks,
                        savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // 无光影 或 非体积渲染：正常渲染
            float partialTicks = event.getPartialTick();
            PoseStack poseStack = event.getPoseStack();

            ShieldParticleRenderer.render(poseStack, null, null, partialTicks);
        }
    }
}
