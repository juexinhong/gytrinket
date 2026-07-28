package com.gy_mod.gy_trinket.core.entity.construct.swarm.client;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.WaveVisualData;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 蜂群能量波着色器渲染器。
 * 使用GPU着色器计算抛物线形状，替代CPU矢量渲染。
 * 核心思路：渲染覆盖能量波包围盒的四边形，片段着色器中用SDF计算形状。
 */
public class EnergyWaveShaderRenderer {

    private static final int GROWTH_TICKS = 3;
    private static final int EXPAND_TICKS = 8;
    private static final int TOTAL_DURATION_TICKS = GROWTH_TICKS + EXPAND_TICKS;
    private static final float SIZE_SCALE = 0.25f;
    private static final float END_SCALE = 3.5f;

    private static final float WIDTH_COEFFICIENT = 1.2f;
    private static final float LENGTH_COEFFICIENT = 14.0f;

    private static final float CENTER_R = 0.9f, CENTER_G = 0.9f, CENTER_B = 0.0f, CENTER_ALPHA = 1.0f;
    private static final float CENTER_WIDTH_MULT = 0.8f, CENTER_LENGTH_MULT = 0.8f;
    private static final float COLOR_R = 0.9f, COLOR_G = 0.6f, COLOR_B = 0.0f, COLOR_ALPHA = 0.8f;
    private static final float COLOR_WIDTH_MULT = 1.1f, COLOR_LENGTH_MULT = 1.2f;
    private static final float OUTER_R = 0.9f, OUTER_G = 0.35f, OUTER_B = 0.0f, OUTER_ALPHA = 0.7f;
    private static final float OUTER_WIDTH_MULT = 1.2f, OUTER_LENGTH_MULT = 1.5f;
    private static final float BLOOM_MAX_RATIO = 0.8f;
    private static final float BLOOM_ALPHA = OUTER_ALPHA;
    private static final float GLOW_RADIUS_RATIO = 0.5f;

    private static final List<EnergyWaveData> waves = new CopyOnWriteArrayList<>();

    public static void addWave(int entityId, double x, double y, double z, double dirX, double dirY, double dirZ, boolean isRepair) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        waves.add(new EnergyWaveData(entityId, x, y, z, dirX, dirY, dirZ, isRepair, currentTime));
        // 同步到矢量渲染器，以便光影模式切换时不会丢失波数据
        EnergyWaveRenderManager.addWave(entityId, x, y, z, dirX, dirY, dirZ, isRepair);
    }

    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        // 光影模式下使用矢量渲染器（自定义着色器与光影不兼容）
        if (ShaderModCompat.isShaderPackInUse()) {
            EnergyWaveRenderManager.onRenderLevelLast(event);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ShaderInstance shader = ModShaders.getEnergyWaveShader();
        if (shader == null) return;

        long currentTime = mc.level.getGameTime();
        waves.removeIf(w -> w.isExpired(currentTime));
        if (waves.isEmpty()) return;

        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        for (EnergyWaveData wave : waves) {
            float progress = wave.getProgress(currentTime, partialTick);
            renderWaveShader(shader, matrix, wave, progress, partialTick, camPos);
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void renderWaves(RenderLevelStageEvent event, List<WaveVisualData> waveDataList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ShaderInstance shader = ModShaders.getEnergyWaveShader();
        if (shader == null) return;

        long currentTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        for (WaveVisualData wave : waveDataList) {
            if (wave.isExpired(currentTime)) continue;
            float progress = EnergyWaveVisualManager.getProgress(wave, currentTime, partialTick);
            EnergyWaveVisualManager.AnimationState anim = EnergyWaveVisualManager.computeAnimation(progress);
            EnergyWaveVisualManager.WaveTransform t = EnergyWaveVisualManager.resolveTransform(wave, partialTick);
            renderWaveShaderFromVisualData(shader, matrix, wave, anim, t, camPos);
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderWaveShaderFromVisualData(ShaderInstance shader, Matrix4f matrix,
                                                        WaveVisualData wave,
                                                        EnergyWaveVisualManager.AnimationState anim,
                                                        EnergyWaveVisualManager.WaveTransform t,
                                                        Vec3 camPos) {
        Vec3 center = t.position().subtract(camPos);
        Vec3 forward = t.direction();

        Vec3 up = EnergyWaveVisualManager.findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        // Compute current dimensions from target values × anim.sizeMultiplier
        float centerHW = wave.targetCenterHW * anim.sizeMultiplier();
        float centerLen = wave.targetCenterLen * anim.sizeMultiplier();
        float colorHW = wave.targetColorHW * anim.sizeMultiplier();
        float colorLen = wave.targetColorLen * anim.sizeMultiplier();
        float outerHW = wave.targetOuterHW * anim.sizeMultiplier();
        float outerLen = wave.targetOuterLen * anim.sizeMultiplier();

        float bloomMaxDist = outerHW * BLOOM_MAX_RATIO;
        float glowRadius = centerLen * GLOW_RADIUS_RATIO;

        // Bounding box extents
        float maxSpan = outerHW + bloomMaxDist;
        float maxBack = outerHW + bloomMaxDist;
        float maxForward = outerLen;

        if (maxSpan <= 0 || maxForward <= 0) return;

        // Set shader uniforms
        setUniformSafe(shader, "MaxSpan", maxSpan);
        setUniformSafe(shader, "MaxBack", maxBack);
        setUniformSafe(shader, "MaxForward", maxForward);
        setUniformSafe(shader, "OuterHW", outerHW);
        setUniformSafe(shader, "OuterLen", outerLen);
        setUniformSafe(shader, "ColorHW", colorHW);
        setUniformSafe(shader, "ColorLen", colorLen);
        setUniformSafe(shader, "CenterHW", centerHW);
        setUniformSafe(shader, "CenterLen", centerLen);

        // Layer colors (with darken/fade applied)
        setUniformSafe(shader, "CenterColor",
            wave.centerR * anim.darkenFactor(), wave.centerG * anim.darkenFactor(),
            wave.centerB * anim.darkenFactor(), wave.centerAlpha * anim.fadeAlpha());
        setUniformSafe(shader, "ColorLayerColor",
            wave.colorR * anim.darkenFactor(), wave.colorG * anim.darkenFactor(),
            wave.colorB * anim.darkenFactor(), wave.colorAlpha * anim.fadeAlpha());
        setUniformSafe(shader, "OuterLayerColor",
            wave.outerR * anim.darkenFactor(), wave.outerG * anim.darkenFactor(),
            wave.outerB * anim.darkenFactor(), wave.outerAlpha * anim.fadeAlpha());
        setUniformSafe(shader, "BloomColor",
            wave.bloomR * anim.darkenFactor(), wave.bloomG * anim.darkenFactor(),
            wave.bloomB * anim.darkenFactor(), wave.bloomAlpha * anim.fadeAlpha());

        setUniformSafe(shader, "BloomMaxDist", bloomMaxDist);
        setUniformSafe(shader, "GlowRadius", glowRadius);
        setUniformSafe(shader, "GlowStrength", 1.0f);

        // Render two perpendicular quads
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);

        // Horizontal plane (right x forward)
        renderQuad(matrix, buffer, center, right, forward, maxSpan, maxBack, maxForward);

        // Vertical plane (up x forward)
        renderQuad(matrix, buffer, center, up, forward, maxSpan, maxBack, maxForward);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static WaveTransform resolveTransform(EnergyWaveData wave, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && wave.entityId >= 0) {
            Entity entity = mc.level.getEntity(wave.entityId);
            if (entity instanceof SwarmConstructEntity swarm && swarm.isAlive()) {
                Vec3 dir = swarm.getLookAngle().normalize();
                double x = Mth.lerp(partialTick, swarm.xOld, swarm.getX()) + dir.x * 0.3;
                double y = Mth.lerp(partialTick, swarm.yOld, swarm.getY()) + swarm.getBbHeight() * 0.4 + dir.y * 0.3;
                double z = Mth.lerp(partialTick, swarm.zOld, swarm.getZ()) + dir.z * 0.3;
                return new WaveTransform(new Vec3(x, y, z), dir);
            }
        }
        return new WaveTransform(
            new Vec3(wave.x, wave.y, wave.z),
            new Vec3(wave.dirX, wave.dirY, wave.dirZ).normalize()
        );
    }

    private static void renderWaveShader(ShaderInstance shader, Matrix4f matrix,
                                          EnergyWaveData wave, float totalProgress,
                                          float partialTick, Vec3 camPos) {
        float growthProgress = Math.min(totalProgress * TOTAL_DURATION_TICKS / GROWTH_TICKS, 1.0f);
        float expandProgress = totalProgress > (float) GROWTH_TICKS / TOTAL_DURATION_TICKS
            ? (totalProgress - (float) GROWTH_TICKS / TOTAL_DURATION_TICKS) / ((float) EXPAND_TICKS / TOTAL_DURATION_TICKS)
            : 0.0f;
        expandProgress = Math.min(expandProgress, 1.0f);

        float growthScale = SIZE_SCALE * growthProgress;
        float expandScale = SIZE_SCALE * (1.0f + (END_SCALE - 1.0f) * expandProgress);
        float currentScale = growthProgress < 1.0f ? growthScale : expandScale;

        float fadeAlpha = expandProgress > 0 ? 1.0f - expandProgress : 1.0f;
        float darkenFactor = expandProgress > 0 ? 1.0f - expandProgress * 0.5f : 1.0f;

        WaveTransform t = resolveTransform(wave, partialTick);
        Vec3 center = t.position.subtract(camPos);
        Vec3 forward = t.direction;

        Vec3 up = findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        float baseA = WIDTH_COEFFICIENT;
        float baseB = LENGTH_COEFFICIENT;

        // Pre-compute layer dimensions
        float centerHW = baseA * currentScale * 0.1f * CENTER_WIDTH_MULT;
        float centerLen = baseB * currentScale * 0.1f * CENTER_LENGTH_MULT;
        float colorHW = baseA * currentScale * 0.1f * COLOR_WIDTH_MULT;
        float colorLen = baseB * currentScale * 0.1f * COLOR_LENGTH_MULT;
        float outerHW = baseA * currentScale * 0.1f * OUTER_WIDTH_MULT;
        float outerLen = baseB * currentScale * 0.1f * OUTER_LENGTH_MULT;
        float bloomMaxDist = outerHW * BLOOM_MAX_RATIO;
        float glowRadius = centerLen * GLOW_RADIUS_RATIO;

        // Bounding box extents
        float maxSpan = outerHW + bloomMaxDist; // half-width of the quad
        float maxBack = outerHW + bloomMaxDist; // extent below baseline (semicircle + bloom)
        float maxForward = outerLen; // extent above baseline

        if (maxSpan <= 0 || maxForward <= 0) return;

        // Set shader uniforms
        setUniformSafe(shader, "MaxSpan", maxSpan);
        setUniformSafe(shader, "MaxBack", maxBack);
        setUniformSafe(shader, "MaxForward", maxForward);
        setUniformSafe(shader, "OuterHW", outerHW);
        setUniformSafe(shader, "OuterLen", outerLen);
        setUniformSafe(shader, "ColorHW", colorHW);
        setUniformSafe(shader, "ColorLen", colorLen);
        setUniformSafe(shader, "CenterHW", centerHW);
        setUniformSafe(shader, "CenterLen", centerLen);

        // Layer colors (pre-multiplied with darken and fade)
        setUniformSafe(shader, "CenterColor",
            CENTER_R * darkenFactor, CENTER_G * darkenFactor, CENTER_B * darkenFactor, CENTER_ALPHA * fadeAlpha);
        setUniformSafe(shader, "ColorLayerColor",
            COLOR_R * darkenFactor, COLOR_G * darkenFactor, COLOR_B * darkenFactor, COLOR_ALPHA * fadeAlpha);
        setUniformSafe(shader, "OuterLayerColor",
            OUTER_R * darkenFactor, OUTER_G * darkenFactor, OUTER_B * darkenFactor, OUTER_ALPHA * fadeAlpha);
        setUniformSafe(shader, "BloomColor",
            OUTER_R * darkenFactor, OUTER_G * darkenFactor, OUTER_B * darkenFactor, BLOOM_ALPHA * fadeAlpha);

        setUniformSafe(shader, "BloomMaxDist", bloomMaxDist);
        setUniformSafe(shader, "GlowRadius", glowRadius);
        setUniformSafe(shader, "GlowStrength", 1.0f);

        // Render two perpendicular quads
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);

        // Horizontal plane (right x forward)
        renderQuad(matrix, buffer, center, right, forward, maxSpan, maxBack, maxForward);

        // Vertical plane (up x forward)
        renderQuad(matrix, buffer, center, up, forward, maxSpan, maxBack, maxForward);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(buffer.end());
    }

    /** Render a single quad covering the bounding box */
    private static void renderQuad(Matrix4f matrix, BufferBuilder buffer,
                                    Vec3 center, Vec3 spanAxis, Vec3 forwardAxis,
                                    float maxSpan, float maxBack, float maxForward) {
        // Four corners of the quad in world space (camera-relative)
        // BL = center + spanAxis*(-maxSpan) + forwardAxis*(-maxBack)  → UV (0,0)
        // BR = center + spanAxis*(+maxSpan) + forwardAxis*(-maxBack)  → UV (1,0)
        // TR = center + spanAxis*(+maxSpan) + forwardAxis*(+maxForward) → UV (1,1)
        // TL = center + spanAxis*(-maxSpan) + forwardAxis*(+maxForward) → UV (0,1)
        Vec3 bl = center.add(spanAxis.scale(-maxSpan)).add(forwardAxis.scale(-maxBack));
        Vec3 br = center.add(spanAxis.scale(maxSpan)).add(forwardAxis.scale(-maxBack));
        Vec3 tr = center.add(spanAxis.scale(maxSpan)).add(forwardAxis.scale(maxForward));
        Vec3 tl = center.add(spanAxis.scale(-maxSpan)).add(forwardAxis.scale(maxForward));

        // Two triangles: BL-BR-TR, BL-TR-TL
        // Triangle 1
        vertex(matrix, buffer, bl, 0, 0);
        vertex(matrix, buffer, br, 1, 0);
        vertex(matrix, buffer, tr, 1, 1);
        // Triangle 2
        vertex(matrix, buffer, bl, 0, 0);
        vertex(matrix, buffer, tr, 1, 1);
        vertex(matrix, buffer, tl, 0, 1);
    }

    private static void vertex(Matrix4f matrix, BufferBuilder buffer, Vec3 pos, float u, float v) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).uv(u, v).endVertex();
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float v0, float v1, float v2, float v3) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(v0, v1, v2, v3);
        }
    }

    private static Vec3 findUp(Vec3 forward) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(forward.dot(up)) > 0.99) {
            up = new Vec3(1, 0, 0);
        }
        return up.subtract(forward.scale(forward.dot(up))).normalize();
    }

    private record WaveTransform(Vec3 position, Vec3 direction) {}

    private static class EnergyWaveData {
        final int entityId;
        final double x, y, z;
        final double dirX, dirY, dirZ;
        final boolean isRepair;
        final long startTime;

        EnergyWaveData(int entityId, double x, double y, double z, double dirX, double dirY, double dirZ, boolean isRepair, long startTime) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.isRepair = isRepair;
            this.startTime = startTime;
        }

        boolean isExpired(long currentTime) {
            return currentTime - startTime >= TOTAL_DURATION_TICKS;
        }

        float getProgress(long currentTime, float partialTick) {
            return Math.min((currentTime - startTime + partialTick) / (float) TOTAL_DURATION_TICKS, 1.0f);
        }
    }
}
