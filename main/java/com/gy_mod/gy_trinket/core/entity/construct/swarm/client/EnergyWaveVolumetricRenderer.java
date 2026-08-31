package com.gy_mod.gy_trinket.core.entity.construct.swarm.client;

import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.WaveVisualData;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 能量波体积渲染器。
 * 使用自定义着色器进行Raymarching，渲染3D旋转体形状的能量波。
 * 渲染一个包围盒（长方体），片段着色器中沿光线步进计算SDF。
 * 无论相机从任何角度观察，都能呈现完整的3D能量波形态。
 */
public class EnergyWaveVolumetricRenderer {

    private static final float BLOOM_MAX_RATIO = 0.8f;

    /**
     * 渲染来自 EnergyWaveVisualManager 的 WaveVisualData 列表。
     */
    public static void renderWaves(RenderLevelStageEvent event, List<WaveVisualData> waveDataList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ShaderInstance shader = ModShaders.getEnergyWaveVolShader();
        if (shader == null) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        // 与护盾粒子（1.20.1 验证能显示）一致：translate(-camPos) + 世界坐标顶点预变换。
        // 顶点矩阵 = R * T(-camPos)，fsh 中 worldPos = InvModelViewMat × viewPos 还原为世界坐标，
        // WaveCenter 等 uniform 也使用世界坐标，全部自洽。
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (WaveVisualData wave : waveDataList) {
            if (wave.isExpired(currentTime)) continue;
            float progress = EnergyWaveVisualManager.getProgress(wave, currentTime, partialTick);
            EnergyWaveVisualManager.AnimationState anim = wave.isDynamic()
                    ? new EnergyWaveVisualManager.AnimationState(1.0f, 1.0f, 1.0f)
                    : EnergyWaveVisualManager.computeAnimation(progress, wave.durationTicks, wave.endScale);
            EnergyWaveVisualManager.WaveTransform t = wave.isDynamic()
                    ? EnergyWaveVisualManager.resolveDynamicTransform(wave, partialTick)
                    : EnergyWaveVisualManager.resolveTransform(wave, partialTick);

            renderWaveVolume(shader, poseStack, wave, anim, t, camPos, partialTick);
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 光影模式下使用保存的矩阵渲染能量波。
     * 在AFTER_LEVEL（Iris composite之后）调用，使用AFTER_PARTICLES时保存的矩阵。
     * 关键：AFTER_LEVEL时RenderSystem的矩阵已被Iris修改，必须恢复为composite之前的正确值。
     */
    public static void renderWavesWithSavedMatrices(RenderLevelStageEvent event,
                                                     List<WaveVisualData> waveDataList,
                                                     Matrix4f savedProjection,
                                                     Matrix4f savedModelView,
                                                     Matrix4f savedPoseStackMat) {
        if (savedProjection == null || savedModelView == null || savedPoseStackMat == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ShaderInstance shader = ModShaders.getEnergyWaveVolShader();
        if (shader == null) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();

        // 保存当前Iris composite后的矩阵
        Matrix4f irisProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        // 恢复composite之前的正确矩阵（与 1.1.1 反编译一致）
        RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        com.mojang.blaze3d.vertex.PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        // 1.1.1 原版：modelViewStack 顶 = savedModelView（相机旋转），
        // 顶点矩阵 = savedPoseStackMat（AFTER_PARTICLES 时的事件 poseStack）
        modelViewStack.last().pose().set(savedModelView);

        // 顶点矩阵 = savedPoseStackMat（相机旋转 R）
        // renderWaveVolumeWithMatrix 内部顶点使用 center + offset，center = position - camPos（相机相对），
        // 所以顶点矩阵 = R（无需额外 translate）
        Matrix4f vertexMatrix = new Matrix4f(savedPoseStackMat);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        for (WaveVisualData wave : waveDataList) {
            if (wave.isExpired(currentTime)) continue;
            float progress = EnergyWaveVisualManager.getProgress(wave, currentTime, partialTick);
            EnergyWaveVisualManager.AnimationState anim = wave.isDynamic()
                    ? new EnergyWaveVisualManager.AnimationState(1.0f, 1.0f, 1.0f)
                    : EnergyWaveVisualManager.computeAnimation(progress, wave.durationTicks, wave.endScale);
            EnergyWaveVisualManager.WaveTransform t = wave.isDynamic()
                    ? EnergyWaveVisualManager.resolveDynamicTransform(wave, partialTick)
                    : EnergyWaveVisualManager.resolveTransform(wave, partialTick);

            renderWaveVolumeWithMatrix(shader, vertexMatrix, wave, anim, t, camPos, partialTick);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // 恢复Iris的矩阵
        modelViewStack.popPose();
        RenderSystem.setProjectionMatrix(irisProjection, VertexSorting.DISTANCE_TO_ORIGIN);

        // 恢复渲染状态
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void renderWaveVolumeWithMatrix(ShaderInstance shader, Matrix4f vertexMatrix,
                                                    WaveVisualData wave,
                                                    EnergyWaveVisualManager.AnimationState anim,
                                                    EnergyWaveVisualManager.WaveTransform t,
                                                    Vec3 camPos, float partialTick) {
        Vec3 anchor = t.position().subtract(camPos);
        Vec3 forward = t.direction();

        Vec3 up = EnergyWaveVisualManager.findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        WaveSizes s = computeWaveSizes(wave, anim, partialTick);
        float centerHW = s.centerHW();
        float centerLen = s.centerLen();
        float colorHW = s.colorHW();
        float colorLen = s.colorLen();
        float outerHW = s.outerHW();
        float outerLen = s.outerLen();

        // 原点（发射点）为波后半圆的顶点（非圆心）：半圆圆心在 原点+外径 处，
        // 波从原点向前延伸，尖端（顶点）落在 原点+长度 处，与伤害范围终点重合。
        // 因此各层 SDF 长度 = 目标长度 - 半宽，尖端相对波心的长度为该值。
        float tipLen = Math.max(outerLen - outerHW, 0.001f);
        float colorTipLen = Math.max(colorLen - colorHW, 0.001f);
        float centerTipLen = Math.max(centerLen - centerHW, 0.001f);
        // 前移量随长度增加（0长度→0，20格→1），抵消波后泛光后移感并避免相机处于波内外中间态；
        // 尖端落在 锚点+前移量+长度 处
        float forwardShift = (float) EnergyWaveVisualManager.computeForwardShift(outerLen);
        Vec3 waveCenter = anchor.add(forward.scale(outerHW + forwardShift));

        float bloomMaxDist = outerHW * BLOOM_MAX_RATIO;
        float glowRadius = centerLen * 0.5f;

        float maxSpan = (outerHW + bloomMaxDist) * 1.2f;
        // 包围盒保留向后泛光（不裁剪）。整体后移由 waveCenter 前移补偿
        float maxBack = (outerHW + bloomMaxDist) * 1.2f;
        float maxForward = tipLen * 1.1f;

        if (maxSpan <= 0 || maxForward <= 0) {
            return;
        }

        setUniformSafe(shader, "MaxSpan", maxSpan);
        setUniformSafe(shader, "MaxBack", maxBack);
        setUniformSafe(shader, "MaxForward", maxForward);
        setUniformSafe(shader, "OuterHW", outerHW);
        setUniformSafe(shader, "OuterLen", tipLen);
        setUniformSafe(shader, "ColorHW", colorHW);
        setUniformSafe(shader, "ColorLen", colorTipLen);
        setUniformSafe(shader, "CenterHW", centerHW);
        setUniformSafe(shader, "CenterLen", centerTipLen);

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

        setUniformSafe(shader, "CamRight", (float) right.x, (float) right.y, (float) right.z);
        setUniformSafe(shader, "CamUp", (float) up.x, (float) up.y, (float) up.z);
        setUniformSafe(shader, "Forward", (float) forward.x, (float) forward.y, (float) forward.z);
        setUniformSafe(shader, "WaveCenter", (float) waveCenter.x, (float) waveCenter.y, (float) waveCenter.z);

        // 逆ModelView矩阵
        // 关键：shader 中 viewPos = ModelViewMat × vertex，vertex = vertexMatrix × box = R × box（相机相对）。
        // 要让 worldPos = InvModelViewMat × viewPos 还原为相机相对坐标（与 WaveCenter 等 uniform 一致），
        // 必须 InvModelViewMat = vertexMatrix.invert()（= R^-1）
        Matrix4f invModelView = new Matrix4f(vertexMatrix).invert();
        if (shader.getUniform("InvModelViewMat") != null) {
            shader.getUniform("InvModelViewMat").set(invModelView);
        }

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);

        renderBoundingBox(vertexMatrix, buffer, waveCenter, right, up, forward, maxSpan, maxBack, maxForward);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void renderWaveVolume(ShaderInstance shader, PoseStack poseStack,
                                          WaveVisualData wave,
                                          EnergyWaveVisualManager.AnimationState anim,
                                          EnergyWaveVisualManager.WaveTransform t,
                                          Vec3 camPos, float partialTick) {
        // 世界坐标锚点（renderWaves 已 poseStack.translate(-camPos)，
        // vertexMatrix = R*T(-camPos)，fsh 中 worldPos = InvModelViewMat × viewPos 还原为世界坐标）
        Vec3 anchor = t.position();
        Vec3 forward = t.direction();

        Vec3 up = EnergyWaveVisualManager.findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        // 当前尺寸 = 目标尺寸 × sizeMultiplier（动态波使用插值后的长度/宽度）
        WaveSizes s = computeWaveSizes(wave, anim, partialTick);
        float centerHW = s.centerHW();
        float centerLen = s.centerLen();
        float colorHW = s.colorHW();
        float colorLen = s.colorLen();
        float outerHW = s.outerHW();
        float outerLen = s.outerLen();

        // 原点（发射点）为波后半圆的顶点（非圆心）：半圆圆心在 原点+外径 处，
        // 波从原点向前延伸，尖端（顶点）落在 原点+长度 处，与伤害范围终点重合。
        // 因此各层 SDF 长度 = 目标长度 - 半宽，尖端相对波心的长度为该值。
        float tipLen = Math.max(outerLen - outerHW, 0.001f);
        float colorTipLen = Math.max(colorLen - colorHW, 0.001f);
        float centerTipLen = Math.max(centerLen - centerHW, 0.001f);
        // 前移量随长度增加（0长度→0，20格→1），抵消波后泛光后移感并避免相机处于波内外中间态；
        // 尖端落在 锚点+前移量+长度 处
        float forwardShift = (float) EnergyWaveVisualManager.computeForwardShift(outerLen);
        Vec3 waveCenter = anchor.add(forward.scale(outerHW + forwardShift));

        float bloomMaxDist = outerHW * BLOOM_MAX_RATIO;
        float glowRadius = centerLen * 0.5f;

        // 包围盒范围（额外20%余量供泛光自然衰减，避免硬切边）
        float maxSpan = (outerHW + bloomMaxDist) * 1.2f;
        // 保留向后泛光（不裁剪），整体后移由 waveCenter 前移补偿
        float maxBack = (outerHW + bloomMaxDist) * 1.2f;
        float maxForward = tipLen * 1.1f;

        if (maxSpan <= 0 || maxForward <= 0) return;

        // 设置着色器uniform
        setUniformSafe(shader, "MaxSpan", maxSpan);
        setUniformSafe(shader, "MaxBack", maxBack);
        setUniformSafe(shader, "MaxForward", maxForward);
        setUniformSafe(shader, "OuterHW", outerHW);
        setUniformSafe(shader, "OuterLen", tipLen);
        setUniformSafe(shader, "ColorHW", colorHW);
        setUniformSafe(shader, "ColorLen", colorTipLen);
        setUniformSafe(shader, "CenterHW", centerHW);
        setUniformSafe(shader, "CenterLen", centerTipLen);

        // 层颜色
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

        // 局部坐标系基向量（传给着色器用于光线变换）
        setUniformSafe(shader, "CamRight", (float) right.x, (float) right.y, (float) right.z);
        setUniformSafe(shader, "CamUp", (float) up.x, (float) up.y, (float) up.z);
        setUniformSafe(shader, "Forward", (float) forward.x, (float) forward.y, (float) forward.z);

        // 波中心位置（相机相对世界空间）：半圆圆心在 原点+外径 处
        setUniformSafe(shader, "WaveCenter", (float) waveCenter.x, (float) waveCenter.y, (float) waveCenter.z);

        // 逆ModelView矩阵（与 52af1a2/1.20.1 原生一致：用顶点矩阵 poseStack 的逆，
        // 使 fsh 中 worldPos = InvModelViewMat × viewPos 还原为相机相对坐标）
        Matrix4f poseStackMat = poseStack.last().pose();
        Matrix4f invModelView = new Matrix4f(poseStackMat).invert();
        if (shader.getUniform("InvModelViewMat") != null) {
            shader.getUniform("InvModelViewMat").set(invModelView);
        }

        // 渲染包围盒（长方体），6个面×2个三角形
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);

        renderBoundingBox(poseStackMat, buffer, waveCenter, right, up, forward, maxSpan, maxBack, maxForward);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(buffer.end());
    }

    /** 渲染一个轴对齐包围盒（长方体）作为raymarching的载体 */
    private static void renderBoundingBox(Matrix4f matrix, BufferBuilder buffer,
                                           Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                                           float maxSpan, float maxBack, float maxForward) {
        // 8个顶点：沿right/up/forward三个轴的偏移
        // 局部坐标范围：right∈[-maxSpan, maxSpan], up∈[-maxSpan, maxSpan], forward∈[-maxBack, maxForward]
        Vec3 rP = right.scale(maxSpan);
        Vec3 rN = right.scale(-maxSpan);
        Vec3 uP = up.scale(maxSpan);
        Vec3 uN = up.scale(-maxSpan);
        Vec3 fP = forward.scale(maxForward);
        Vec3 fN = forward.scale(-maxBack);

        // 8个顶点
        Vec3 v000 = center.add(rN).add(uN).add(fN);
        Vec3 v001 = center.add(rN).add(uN).add(fP);
        Vec3 v010 = center.add(rN).add(uP).add(fN);
        Vec3 v011 = center.add(rN).add(uP).add(fP);
        Vec3 v100 = center.add(rP).add(uN).add(fN);
        Vec3 v101 = center.add(rP).add(uN).add(fP);
        Vec3 v110 = center.add(rP).add(uP).add(fN);
        Vec3 v111 = center.add(rP).add(uP).add(fP);

        // UV坐标：将包围盒表面位置映射到[-1,1]范围用于着色器计算
        // 每个面的UV对应局部坐标中的两个轴

        // 前面 (forward+): z = maxForward, UV映射right和up
        renderQuadUV(matrix, buffer,
            v001, v101, v111, v011,
            -1, -1, 1, -1, 1, 1, -1, 1);

        // 后面 (forward-): z = -maxBack
        renderQuadUV(matrix, buffer,
            v100, v000, v010, v110,
            -1, -1, 1, -1, 1, 1, -1, 1);

        // 右面 (right+): x = maxSpan
        renderQuadUV(matrix, buffer,
            v101, v111, v110, v100,
            -1, -1, 1, -1, 1, 1, -1, 1);

        // 左面 (right-): x = -maxSpan
        renderQuadUV(matrix, buffer,
            v000, v001, v011, v010,
            -1, -1, 1, -1, 1, 1, -1, 1);

        // 上面 (up+): y = maxSpan
        renderQuadUV(matrix, buffer,
            v011, v111, v110, v010,
            -1, -1, 1, -1, 1, 1, -1, 1);

        // 下面 (up-): y = -maxSpan
        renderQuadUV(matrix, buffer,
            v000, v100, v101, v001,
            -1, -1, 1, -1, 1, 1, -1, 1);
    }

    /**
     * 渲染一个四边形（两个三角形），带自定义UV。
     * UV值在[-1,1]范围内，着色器中用于还原局部坐标。
     */
    private static void renderQuadUV(Matrix4f matrix, BufferBuilder buffer,
                                      Vec3 bl, Vec3 br, Vec3 tr, Vec3 tl,
                                      float uBL, float vBL, float uBR, float vBR,
                                      float uTR, float vTR, float uTL, float vTL) {
        // 三角形1: BL-BR-TR
        vertex(matrix, buffer, bl, uBL, vBL);
        vertex(matrix, buffer, br, uBR, vBR);
        vertex(matrix, buffer, tr, uTR, vTR);
        // 三角形2: BL-TR-TL
        vertex(matrix, buffer, bl, uBL, vBL);
        vertex(matrix, buffer, tr, uTR, vTR);
        vertex(matrix, buffer, tl, uTL, vTL);
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

    private static void setUniformSafe(ShaderInstance shader, String name, float v0, float v1, float v2) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(v0, v1, v2);
        }
    }

    /** 动态波渲染尺寸 */
    private record WaveSizes(float centerHW, float centerLen, float colorHW, float colorLen, float outerHW, float outerLen) {}

    /**
     * 计算渲染尺寸：动态波使用客户端缓动的长度/宽度（消除充能/消退时的长度抖动），普通波使用目标尺寸。
     */
    private static WaveSizes computeWaveSizes(WaveVisualData wave,
                                              EnergyWaveVisualManager.AnimationState anim,
                                              float partialTick) {
        float mult = anim.sizeMultiplier();
        if (wave.isDynamic()) {
            // 显示长度/宽度向目标缓动（每帧），消除长度随充能/消退突变造成的抖动
            if (!wave.displayInitialized) {
                wave.displayLen = wave.len;
                wave.displayWidth = wave.width;
                wave.displayInitialized = true;
            }
            wave.displayLen += (wave.len - wave.displayLen) * 0.35f;
            wave.displayWidth += (wave.width - wave.displayWidth) * 0.35f;
            float len = wave.displayLen;
            float hw = wave.displayWidth;
            // 尖端（外层）落在 原点+长度 处，与伤害范围终点重合；中心/颜色层为 0.6/0.8
            return new WaveSizes(
                    hw * 0.6f * mult, len * 0.6f * mult,
                    hw * 0.8f * mult, len * 0.8f * mult,
                    hw * mult, len * mult);
        }
        return new WaveSizes(
                wave.targetCenterHW * mult, wave.targetCenterLen * mult,
                wave.targetColorHW * mult, wave.targetColorLen * mult,
                wave.targetOuterHW * mult, wave.targetOuterLen * mult);
    }
}
