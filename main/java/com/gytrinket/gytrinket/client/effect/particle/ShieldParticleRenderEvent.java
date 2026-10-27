package com.gytrinket.gytrinket.client.effect.particle;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.mojang.math.Matrix4f;

/**
 * 护盾粒子渲染事件分发。
 * <p>
 * 光影模式下采用两阶段渲染（与 1.20.1 对齐）：
 * - AFTER_PARTICLES（Iris composite 之前）：仅保存正确的矩阵，不渲染
 * - 渲染放到 Iris composite 之后：1.20.1 用 {@code Stage.AFTER_LEVEL}，但 1.19.2 没有这个
 *   Stage，等价时机是 {@link RenderLevelLastEvent}（由 GameRenderer 在
 *   {@code LevelRenderer.renderLevel} 返回后派发，晚于 Iris 的 finalizeLevelRendering）。
 * <p>
 * 原因：AFTER_PARTICLES 阶段 Iris 的 shouldOverrideShaders()=true，
 * 未知着色器 apply() 的 TAIL 会调用 DepthColorStorage.disableDepthColor() 禁用 colorMask，
 * 导致颜色无法写入。composite 之后 shouldOverrideShaders()=false，colorMask 不受影响。
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
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevelLast);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (ShaderModCompat.isRenderingShadows()) return;

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();

        if (shaderActive) {
            // 光影：composite 之前只保存矩阵，等 RenderLevelLastEvent（composite 之后）再画。
            // 注意不能只看"体积渲染开关"：平面四边形回退（style 2）用的也是原版 ShaderInstance，
            // 同样会在 Iris 的 depth/color lock 下不可见。
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
                savedPartialTicks = event.getPartialTick();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // 无光影：正常渲染
            float partialTicks = event.getPartialTick();
            ShieldParticleRenderer.render(event.getPoseStack(), null, null, partialTicks);
        }
    }

    /**
     * 1.19.2 的"composite 之后"渲染入口（对应 1.20.1 的 AFTER_LEVEL）。
     * <p>
     * 该事件由 GameRenderer 在 LevelRenderer.renderLevel 返回后派发：
     * Iris 已在 renderLevel 的 RETURN 处执行 finalizeLevelRendering（composite），
     * 因此此时 shouldOverrideShaders()=false，自定义 ShaderInstance 可以正常写颜色。
     */
    public static void onRenderLevelLast(RenderLevelLastEvent event) {
        if (!ShaderModCompat.isShaderPackInUse()) return;
        if (savedProjectionMatrix == null || savedModelViewMatrix == null || savedPoseStackMatrix == null) return;

        ShieldParticleRenderer.renderWithSavedMatrices(event.getPoseStack(), null, null, savedPartialTicks,
                savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
    }
}
