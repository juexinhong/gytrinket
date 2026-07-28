package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.config.ClientConfig;
import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

public class ShieldParticleRenderEvent {
    
    // 在AFTER_PARTICLES时保存的矩阵状态（Iris composite之前的正确值）
    private static Matrix4f savedProjectionMatrix = null;
    private static Matrix4f savedModelViewMatrix = null;
    private static Matrix4f savedPoseStackMatrix = null;
    
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
    }
    
    public static void onRenderLevel(RenderLevelStageEvent event) {
        boolean shaderActive = ShaderModCompat.isShaderPackInUse();
        boolean volumetricEnabled = ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get();
        
        if (shaderActive && volumetricEnabled) {
            // 光影 + 体积渲染：需要特殊矩阵处理
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                // Iris composite之前：保存正确的矩阵状态
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // Iris composite之后：使用保存的矩阵渲染
                PoseStack poseStack = event.getPoseStack();
                float partialTicks = event.getPartialTick();
                ShieldParticleRenderer.renderWithSavedMatrices(poseStack, null, null, partialTicks,
                    savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // 无光影 或 非体积渲染：正常渲染
            PoseStack poseStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            ShieldParticleRenderer.render(poseStack, null, null, partialTicks);
        }
    }
}
