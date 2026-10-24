package com.gytrinket.gytrinket.client.effect.particle;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
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
    /** AFTER_PARTICLES 时保存的插值时间，补画时直接复用，省得再查 API */
    private static float savedPartialTicks = 0.0F;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
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
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
                savedPartialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // 孤岛维度 + 光影：天空会在 renderLevel 末尾补画，会盖住这里画的粒子，
                // 所以本帧跳过，交给 renderAfterSky() 在天空之后补画
                if (com.gytrinket.gytrinket.client.world.IslandSky.willPaintAfterLevel()) {
                    return;
                }
                // Iris composite之后：使用保存的矩阵渲染
                float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                PoseStack poseStack = event.getPoseStack();

                ShieldParticleRenderer.renderWithSavedMatrices(poseStack, null, event.getCamera(), partialTicks,
                        savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // 无光影 或 非体积渲染：正常渲染
            float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            PoseStack poseStack = event.getPoseStack();

            ShieldParticleRenderer.render(poseStack, null, event.getCamera(), partialTicks);
        }
    }

    /**
     * 孤岛天空画完之后再补一遍粒子。
     * <p>
     * 为什么需要：孤岛天空是在 {@code GameRenderer.renderLevel} 的 TAIL 补画的
     * （为了压过光影包的 composite），它会盖住 {@code AFTER_LEVEL} 阶段画的一切。
     * 所以那个维度里开了光影时，粒子要等天空画完再补一次。
     */
    public static void renderAfterSky() {
        if (ShaderModCompat.isRenderingShadows()) return;
        if (!ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get()) return;
        if (!com.gytrinket.gytrinket.client.world.IslandSky.willPaintAfterLevel()) return;
        if (savedProjectionMatrix == null || savedModelViewMatrix == null || savedPoseStackMatrix == null) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        ShieldParticleRenderer.renderWithSavedMatrices(new PoseStack(), null,
                mc.gameRenderer.getMainCamera(), savedPartialTicks,
                savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
    }
}
