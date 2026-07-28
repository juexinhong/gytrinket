package com.gy_mod.gy_trinket.core.entity.construct.swarm.client;

import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.WaveVisualData;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 蜂群能量波渲染管理器。
 * 使用矢量渲染抛物线公式 f = L*(1 - (s/W)²)，仅渲染 f ≥ 0 的区域。
 * a 为视觉宽度系数，b 为视觉长度系数，直接控制图形尺寸。
 * 双平面（水平+垂直交叉），三层结构：中心层(白色) + 颜色层(亮黄色) + 外焰层(橙色)。
 */
public class EnergyWaveRenderManager {

    /** 生长阶段：从0%到100%大小的过渡刻数 */
    private static final int GROWTH_TICKS = 3;
    /** 膨胀阶段：从100%到150%大小并降低透明度的刻数 */
    private static final int EXPAND_TICKS = 8;
    /** 总持续刻数 */
    private static final int TOTAL_DURATION_TICKS = GROWTH_TICKS + EXPAND_TICKS;
    private static final float SIZE_SCALE = 0.25f;
    /** 膨胀阶段最终变大50%：最终尺寸 = 初始尺寸 × 1.5 */
    private static final float END_SCALE = 1.3f;

    /** 中心层宽度：直接控制图形的视觉半宽 */
    private static final float WIDTH_COEFFICIENT = 1.2f;
    /** 中心层长度：直接控制图形的视觉长度（前向延伸） */
    private static final float LENGTH_COEFFICIENT = 14.0f;

    /** 尖端区（宽度50%内）每半边的采样段数 — 高密度 */
    private static final int TIP_SEGMENTS = 56;
    /** 基部区（宽度50%外）每半边的采样段数 — 低密度 */
    private static final int BASE_SEGMENTS = 12;
    /** 尖端区边界：占半宽的比例 */
    private static final float TIP_RATIO = 0.5f;
    /** 底部半圆的采样段数 */
    private static final int BASE_ARC_SEGMENTS = 16;
    /** 亮色处理层半径 = 中心层长度 × 此比例 */
    private static final float GLOW_RADIUS_RATIO = 0.5f;

    // 三层定义
    // 中心层：黄色，初始透明度100%
    private static final float CENTER_R = 0.9f, CENTER_G = 0.9f, CENTER_B = 0.0f, CENTER_ALPHA = 1.0f;
    private static final float CENTER_WIDTH_MULT = 0.8f, CENTER_LENGTH_MULT = 0.8f;
    // 颜色层：橘色，初始透明度80%
    private static final float COLOR_R = 0.9f, COLOR_G = 0.6f, COLOR_B = 0.0f, COLOR_ALPHA = 0.8f;
    private static final float COLOR_WIDTH_MULT = 1.1f, COLOR_LENGTH_MULT = 1.2f;
    // 外焰层：红橙色，初始透明度70%
    private static final float OUTER_R = 0.9f, OUTER_G = 0.35f, OUTER_B = 0.0f, OUTER_ALPHA = 0.7f;
    private static final float OUTER_WIDTH_MULT = 1.2f, OUTER_LENGTH_MULT = 1.5f;
    // 泛光层：距发射点越远泛光越弱，发射点处最大=外焰宽度的80%，尖端处为0
    private static final float BLOOM_MAX_RATIO = 0.8f; // 最大泛光距离 = 外焰宽度 × 此比例
    private static final float BLOOM_ALPHA = OUTER_ALPHA; // 泛光层初始透明度跟随外焰层

    private static final List<EnergyWaveData> waves = new CopyOnWriteArrayList<>();

    public static void addWave(int entityId, double x, double y, double z, double dirX, double dirY, double dirZ, boolean isRepair) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        waves.add(new EnergyWaveData(entityId, x, y, z, dirX, dirY, dirZ, isRepair, currentTime));
    }

    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentTime = mc.level.getGameTime();
        waves.removeIf(w -> w.isExpired(currentTime));
        if (waves.isEmpty()) return;

        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (EnergyWaveData wave : waves) {
            float progress = wave.getProgress(currentTime, partialTick);
            renderWaveVector(matrix, buffer, wave, progress, partialTick, camPos);
        }

        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * 渲染来自 EnergyWaveVisualManager 的 WaveVisualData 列表。
     * 供 EnergyWaveVisualManager 在光影降级模式下调用。
     */
    public static void renderWaves(RenderLevelStageEvent event, List<WaveVisualData> waveDataList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (WaveVisualData wave : waveDataList) {
            if (wave.isExpired(currentTime)) continue;
            float progress = EnergyWaveVisualManager.getProgress(wave, currentTime, partialTick);
            EnergyWaveVisualManager.AnimationState anim = EnergyWaveVisualManager.computeAnimation(progress);
            EnergyWaveVisualManager.WaveTransform t = EnergyWaveVisualManager.resolveTransform(wave, partialTick);
            renderWaveFromVisualData(matrix, buffer, wave, anim, t, camPos);
        }

        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * 根据 WaveVisualData 和动画状态渲染单个能量波（三层结构 + 泛光层）。
     * 使用 WaveVisualData 中的目标尺寸 × 动画 sizeMultiplier 得到当前尺寸。
     */
    private static void renderWaveFromVisualData(Matrix4f matrix, BufferBuilder buffer,
                                                  WaveVisualData wave,
                                                  EnergyWaveVisualManager.AnimationState anim,
                                                  EnergyWaveVisualManager.WaveTransform t,
                                                  Vec3 camPos) {
        Vec3 center = t.position().subtract(camPos);
        Vec3 forward = t.direction();

        Vec3 up = EnergyWaveVisualManager.findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        // 当前尺寸 = 目标尺寸 × sizeMultiplier
        float centerHW = wave.targetCenterHW * anim.sizeMultiplier();
        float centerLen = wave.targetCenterLen * anim.sizeMultiplier();
        float colorHW = wave.targetColorHW * anim.sizeMultiplier();
        float colorLen = wave.targetColorLen * anim.sizeMultiplier();
        float outerHW = wave.targetOuterHW * anim.sizeMultiplier();
        float outerLen = wave.targetOuterLen * anim.sizeMultiplier();

        // 亮色处理参数
        float glowRadius = centerLen * GLOW_RADIUS_RATIO;
        Vec3 glowCenter = center.add(forward.scale(-centerHW));

        // 泛光层参数
        float maxBloomDist = outerHW * BLOOM_MAX_RATIO;

        float darken = anim.darkenFactor();
        float fadeAlpha = anim.fadeAlpha();

        // 从外到内渲染，确保内层覆盖外层
        // 第四层：泛光层（最外，最先渲染）
        renderBloomLayer(matrix, buffer, center, right, up, forward,
            outerHW, outerLen, maxBloomDist,
            wave.bloomR * darken, wave.bloomG * darken, wave.bloomB * darken, wave.bloomAlpha * fadeAlpha);

        // 第三层：外焰层（不应用亮度处理）
        renderParabolaPlaneNoGlow(matrix, buffer, center, right, forward,
            outerHW, outerLen,
            wave.outerR * darken, wave.outerG * darken, wave.outerB * darken, wave.outerAlpha * fadeAlpha);
        renderParabolaPlaneNoGlow(matrix, buffer, center, up, forward,
            outerHW, outerLen,
            wave.outerR * darken, wave.outerG * darken, wave.outerB * darken, wave.outerAlpha * fadeAlpha);

        // 第二层：颜色层
        renderParabolaPlane(matrix, buffer, center, right, forward,
            colorHW, colorLen,
            wave.colorR * darken, wave.colorG * darken, wave.colorB * darken, wave.colorAlpha * fadeAlpha,
            glowCenter, glowRadius);
        renderParabolaPlane(matrix, buffer, center, up, forward,
            colorHW, colorLen,
            wave.colorR * darken, wave.colorG * darken, wave.colorB * darken, wave.colorAlpha * fadeAlpha,
            glowCenter, glowRadius);

        // 第一层：中心层（最内，最后渲染）
        renderParabolaPlane(matrix, buffer, center, right, forward,
            centerHW, centerLen,
            wave.centerR * darken, wave.centerG * darken, wave.centerB * darken, wave.centerAlpha * fadeAlpha,
            glowCenter, glowRadius);
        renderParabolaPlane(matrix, buffer, center, up, forward,
            centerHW, centerLen,
            wave.centerR * darken, wave.centerG * darken, wave.centerB * darken, wave.centerAlpha * fadeAlpha,
            glowCenter, glowRadius);
    }

    /** 获取蜂群的渲染位置（世界坐标，partialTick插值）和朝向 */
    private static WaveTransform resolveTransform(EnergyWaveData wave, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && wave.entityId >= 0) {
            Entity entity = mc.level.getEntity(wave.entityId);
            if (entity instanceof SwarmConstructEntity swarm && swarm.isAlive()) {
                Vec3 dir = swarm.getLookAngle().normalize();
                // 半身高 + 朝向方向0.3格
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

    /**
     * 矢量渲染能量波（三层结构 + 亮色处理层）。
     * 两个阶段：
     *   生长阶段（0~GROWTH_TICKS）：从0%大小到100%大小，透明度不变
     *   膨胀阶段（GROWTH_TICKS~TOTAL）：从100%到150%大小，透明度从100%降到0%
     * 原点固定为发射点，无前移。
     */
    private static void renderWaveVector(Matrix4f matrix, BufferBuilder buffer, EnergyWaveData wave, float totalProgress, float partialTick, Vec3 camPos) {
        // 计算生长阶段和膨胀阶段的进度
        float growthProgress = Math.min(totalProgress * TOTAL_DURATION_TICKS / GROWTH_TICKS, 1.0f); // 0~1 in GROWTH_TICKS
        float expandProgress = totalProgress > (float) GROWTH_TICKS / TOTAL_DURATION_TICKS
            ? (totalProgress - (float) GROWTH_TICKS / TOTAL_DURATION_TICKS) / ((float) EXPAND_TICKS / TOTAL_DURATION_TICKS)
            : 0.0f; // 0~1 in EXPAND_TICKS
        expandProgress = Math.min(expandProgress, 1.0f);

        // 生长阶段：尺寸从0到SIZE_SCALE；膨胀阶段：尺寸从SIZE_SCALE到SIZE_SCALE*END_SCALE
        float growthScale = SIZE_SCALE * growthProgress;
        float expandScale = SIZE_SCALE * (1.0f + (END_SCALE - 1.0f) * expandProgress);
        float currentScale = growthProgress < 1.0f ? growthScale : expandScale;

        // 透明度：生长阶段不变，膨胀阶段从1.0降到0.0
        float fadeAlpha = expandProgress > 0 ? 1.0f - expandProgress : 1.0f;
        // 亮度：生长阶段不变，膨胀阶段从1.0降到0.5（变暗50%）
        float darkenFactor = expandProgress > 0 ? 1.0f - expandProgress * 0.5f : 1.0f;

        // 原点固定为发射点，无前移
        WaveTransform t = resolveTransform(wave, partialTick);
        Vec3 center = t.position.subtract(camPos);
        Vec3 forward = t.direction;

        Vec3 up = findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        float baseA = WIDTH_COEFFICIENT;
        float baseB = LENGTH_COEFFICIENT;

        // 亮色处理层参数：中心在半圆弧最远点，半径=中心层长度×10%
        float glowHalfWidth = baseA * currentScale * 0.1f * CENTER_WIDTH_MULT;
        float glowRadius = baseB * currentScale * 0.1f * CENTER_LENGTH_MULT * GLOW_RADIUS_RATIO;
        Vec3 glowCenter = center.add(forward.scale(-glowHalfWidth));

        // 从外到内渲染，确保内层覆盖外层
        // 第四层：泛光层（最外，最先渲染）— 外焰形状的模糊扩展，距发射点越远泛光越弱
        float hwOuter = baseA * currentScale * 0.1f * OUTER_WIDTH_MULT;
        float lenOuter = baseB * currentScale * 0.1f * OUTER_LENGTH_MULT;
        float maxBloomDist = hwOuter * BLOOM_MAX_RATIO; // 最大泛光距离
        renderBloomLayer(matrix, buffer, center, right, up, forward,
            hwOuter, lenOuter, maxBloomDist,
            OUTER_R * darkenFactor, OUTER_G * darkenFactor, OUTER_B * darkenFactor, BLOOM_ALPHA * fadeAlpha);

        // 第三层：外焰层（不应用亮度处理）
        renderWaveLayerNoGlow(matrix, buffer, center, right, up, forward, currentScale,
            baseA, baseB, OUTER_WIDTH_MULT, OUTER_LENGTH_MULT,
            OUTER_R * darkenFactor, OUTER_G * darkenFactor, OUTER_B * darkenFactor, OUTER_ALPHA * fadeAlpha);

        // 第二层：颜色层
        renderWaveLayer(matrix, buffer, center, right, up, forward, currentScale,
            baseA, baseB, COLOR_WIDTH_MULT, COLOR_LENGTH_MULT,
            COLOR_R * darkenFactor, COLOR_G * darkenFactor, COLOR_B * darkenFactor, COLOR_ALPHA * fadeAlpha, glowCenter, glowRadius);

        // 第一层：中心层（最内，最后渲染）
        renderWaveLayer(matrix, buffer, center, right, up, forward, currentScale,
            baseA, baseB, CENTER_WIDTH_MULT, CENTER_LENGTH_MULT,
            CENTER_R * darkenFactor, CENTER_G * darkenFactor, CENTER_B * darkenFactor, CENTER_ALPHA * fadeAlpha, glowCenter, glowRadius);
    }

    /** 渲染泛光层（两个互相垂直的平面）— 外焰形状的模糊扩展外壳 */
    private static void renderBloomLayer(Matrix4f matrix, BufferBuilder buffer,
                                          Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                                          float hwOuter, float lenOuter, float maxBloomDist,
                                          float r, float g, float bl, float baseAlpha) {
        if (hwOuter <= 0 || lenOuter <= 0 || maxBloomDist <= 0) return;
        // 水平平面
        renderBloomPlane(matrix, buffer, center, right, forward, hwOuter, lenOuter, maxBloomDist, r, g, bl, baseAlpha);
        // 垂直平面
        renderBloomPlane(matrix, buffer, center, up, forward, hwOuter, lenOuter, maxBloomDist, r, g, bl, baseAlpha);
    }

    /**
     * 渲染泛光平面：沿外焰曲线的法线方向扩展，alpha从baseAlpha渐变到0。
     * 泛光距离随距发射点的距离递减：发射点处=maxBloomDist，尖端处=0。
     * 底部半圆的泛光距离按距发射点的实际距离计算。
     */
    private static void renderBloomPlane(Matrix4f matrix, BufferBuilder buffer,
                                          Vec3 center, Vec3 spanAxis, Vec3 forwardAxis,
                                          float hwOuter, float lenOuter, float maxBloomDist,
                                          float r, float g, float bl, float baseAlpha) {
        // 第一部分：抛物线曲线外壳
        // 采样外焰曲线点，沿法线方向扩展
        float tipBound = hwOuter * TIP_RATIO;
        float[] sValues = new float[BASE_SEGMENTS * 2 + TIP_SEGMENTS * 2 + 3];
        int idx = 0;
        for (int i = 0; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = -hwOuter + (hwOuter - tipBound) * (float) i / BASE_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = -tipBound + tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = tipBound + (hwOuter - tipBound) * (float) i / BASE_SEGMENTS;
        }
        int totalPoints = idx;

        // 计算外焰曲线点和泛光外扩点
        Vec3[] innerPoints = new Vec3[totalPoints];
        Vec3[] outerPoints = new Vec3[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            float s = sValues[i];
            float normalizedS = s / hwOuter;
            float f = lenOuter * (1 - (float)Math.pow(Math.abs(normalizedS), 3.6f));
            Vec3 curvePoint = center.add(spanAxis.scale(s)).add(forwardAxis.scale(f));
            innerPoints[i] = curvePoint;

            // 计算外焰曲线的法线方向（向外）
            // df/ds = -3.6 * lenOuter / hwOuter * sign(s) * |normalizedS|^2.6
            float dfds = -3.6f * lenOuter / hwOuter * Math.signum(s) * (float)Math.pow(Math.abs(normalizedS), 2.6f);
            // 外法线 = normalize(-dfds, 1)，即沿 spanAxis 和 forwardAxis 分解
            float nx = -dfds; // spanAxis 方向
            float ny = 1.0f;  // forwardAxis 方向
            float nLen = (float)Math.sqrt(nx * nx + ny * ny);
            nx /= nLen;
            ny /= nLen;

            // 泛光距离：距发射点越远越小
            // distFromOrigin = 曲线点到center的距离
            double distFromOrigin = curvePoint.distanceTo(center);
            float t = lenOuter > 0 ? (float)Math.min(distFromOrigin / lenOuter, 1.0f) : 0;
            float bloomDist = maxBloomDist * (1.0f - t);

            // 泛光外扩点 = 曲线点 + bloomDist * 法线方向
            if (bloomDist > 0.001f) {
                outerPoints[i] = curvePoint.add(spanAxis.scale(nx * bloomDist)).add(forwardAxis.scale(ny * bloomDist));
            } else {
                outerPoints[i] = curvePoint; // 尖端处泛光为0
            }
        }

        // 渲染曲线外壳条带
        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 ip0 = innerPoints[i], ip1 = innerPoints[i + 1];
            Vec3 op0 = outerPoints[i], op1 = outerPoints[i + 1];

            if (op0.distanceTo(ip0) < 0.001f && op1.distanceTo(ip1) < 0.001f) continue;

            // 正面：内边alpha=baseAlpha，外边alpha=0
            buffer.vertex(matrix, (float)ip0.x, (float)ip0.y, (float)ip0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)ip1.x, (float)ip1.y, (float)ip1.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)op1.x, (float)op1.y, (float)op1.z).color(r, g, bl, 0).endVertex();

            buffer.vertex(matrix, (float)ip0.x, (float)ip0.y, (float)ip0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)op1.x, (float)op1.y, (float)op1.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)op0.x, (float)op0.y, (float)op0.z).color(r, g, bl, 0).endVertex();

            // 反面
            buffer.vertex(matrix, (float)ip0.x, (float)ip0.y, (float)ip0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)op1.x, (float)op1.y, (float)op1.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)ip1.x, (float)ip1.y, (float)ip1.z).color(r, g, bl, baseAlpha).endVertex();

            buffer.vertex(matrix, (float)ip0.x, (float)ip0.y, (float)ip0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)op0.x, (float)op0.y, (float)op0.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)op1.x, (float)op1.y, (float)op1.z).color(r, g, bl, 0).endVertex();
        }

        // 第二部分：底部半圆外壳
        // 半圆上的点距发射点(center)的距离 = hwOuter，比值 = hwOuter/lenOuter
        float semicircleT = lenOuter > 0 ? hwOuter / lenOuter : 0;
        float semicircleBloomDist = maxBloomDist * (1.0f - semicircleT);
        int arcPoints = BASE_ARC_SEGMENTS + 1;
        for (int i = 0; i < arcPoints - 1; i++) {
            float angle0 = (float) Math.PI * i / BASE_ARC_SEGMENTS;
            float angle1 = (float) Math.PI * (i + 1) / BASE_ARC_SEGMENTS;

            // 外焰半圆点（内边界）
            Vec3 arcInner0 = center.add(spanAxis.scale(hwOuter * Mth.cos(angle0))).add(forwardAxis.scale(-hwOuter * Mth.sin(angle0)));
            Vec3 arcInner1 = center.add(spanAxis.scale(hwOuter * Mth.cos(angle1))).add(forwardAxis.scale(-hwOuter * Mth.sin(angle1)));

            // 泛光半圆点（外边界）：径向外扩 semicircleBloomDist
            float rOuter = hwOuter + semicircleBloomDist;
            Vec3 arcOuter0 = center.add(spanAxis.scale(rOuter * Mth.cos(angle0))).add(forwardAxis.scale(-rOuter * Mth.sin(angle0)));
            Vec3 arcOuter1 = center.add(spanAxis.scale(rOuter * Mth.cos(angle1))).add(forwardAxis.scale(-rOuter * Mth.sin(angle1)));

            // 正面
            buffer.vertex(matrix, (float)arcInner0.x, (float)arcInner0.y, (float)arcInner0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)arcInner1.x, (float)arcInner1.y, (float)arcInner1.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)arcOuter1.x, (float)arcOuter1.y, (float)arcOuter1.z).color(r, g, bl, 0).endVertex();

            buffer.vertex(matrix, (float)arcInner0.x, (float)arcInner0.y, (float)arcInner0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)arcOuter1.x, (float)arcOuter1.y, (float)arcOuter1.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)arcOuter0.x, (float)arcOuter0.y, (float)arcOuter0.z).color(r, g, bl, 0).endVertex();

            // 反面
            buffer.vertex(matrix, (float)arcInner0.x, (float)arcInner0.y, (float)arcInner0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)arcOuter1.x, (float)arcOuter1.y, (float)arcOuter1.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)arcInner1.x, (float)arcInner1.y, (float)arcInner1.z).color(r, g, bl, baseAlpha).endVertex();

            buffer.vertex(matrix, (float)arcInner0.x, (float)arcInner0.y, (float)arcInner0.z).color(r, g, bl, baseAlpha).endVertex();
            buffer.vertex(matrix, (float)arcOuter0.x, (float)arcOuter0.y, (float)arcOuter0.z).color(r, g, bl, 0).endVertex();
            buffer.vertex(matrix, (float)arcOuter1.x, (float)arcOuter1.y, (float)arcOuter1.z).color(r, g, bl, 0).endVertex();
        }
    }

    /** 渲染单层能量波（两个互相垂直的平面），不应用亮度处理 */
    private static void renderWaveLayerNoGlow(Matrix4f matrix, BufferBuilder buffer,
                                         Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                                         float scale, float a, float b,
                                         float widthMult, float lengthMult,
                                         float r, float g, float bl, float alpha) {
        float halfWidth = a * scale * 0.1f * widthMult;
        float length = b * scale * 0.1f * lengthMult;

        // 水平平面
        renderParabolaPlaneNoGlow(matrix, buffer, center, right, forward, halfWidth, length, r, g, bl, alpha);
        // 垂直平面
        renderParabolaPlaneNoGlow(matrix, buffer, center, up, forward, halfWidth, length, r, g, bl, alpha);
    }

    /** 渲染单层能量波（两个互相垂直的平面），widthMult/lengthMult以中心层视觉尺寸为基准 */
    private static void renderWaveLayer(Matrix4f matrix, BufferBuilder buffer,
                                         Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                                         float scale, float a, float b,
                                         float widthMult, float lengthMult,
                                         float r, float g, float bl, float alpha,
                                         Vec3 glowCenter, float glowRadius) {
        float halfWidth = a * scale * 0.1f * widthMult;
        float length = b * scale * 0.1f * lengthMult;

        // 水平平面
        renderParabolaPlane(matrix, buffer, center, right, forward, halfWidth, length, r, g, bl, alpha, glowCenter, glowRadius);
        // 垂直平面
        renderParabolaPlane(matrix, buffer, center, up, forward, halfWidth, length, r, g, bl, alpha, glowCenter, glowRadius);
    }

    /**
     * 渲染一个平面的实心抛物面图形（含底部半圆包裹）。
     * 公式 f = length * (1 - (s/halfWidth)²)，填充所有 f ≥ 0 的区域。
     * 动态采样：尖端区（宽度50%内）高密度采样，基部区（宽度50%外）低密度采样。
     * 亮色处理：顶点颜色根据距离glowCenter的远近向白色混合，仅在能量波表面显示发光效果。
     */
    private static void renderParabolaPlane(Matrix4f matrix, BufferBuilder buffer,
                                             Vec3 center, Vec3 spanAxis, Vec3 forwardAxis,
                                             float halfWidth, float length,
                                             float r, float g, float bl, float alpha,
                                             Vec3 glowCenter, float glowRadius) {
        if (halfWidth <= 0 || length <= 0) return;

        // 顶点 (s=0, f=length)
        Vec3 apex = center.add(forwardAxis.scale(length));

        // 动态采样：尖端区（|s| < tipBound）高密度，基部区（|s| >= tipBound）低密度
        float tipBound = halfWidth * TIP_RATIO;
        float[] sValues = new float[BASE_SEGMENTS * 2 + TIP_SEGMENTS * 2 + 3];
        int idx = 0;
        for (int i = 0; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = -halfWidth + (halfWidth - tipBound) * (float) i / BASE_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = -tipBound + tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = tipBound + (halfWidth - tipBound) * (float) i / BASE_SEGMENTS;
        }
        int totalPoints = idx;

        // 计算抛物线曲线点
        Vec3[] curvePoints = new Vec3[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            float s = sValues[i];
            float normalizedS = s / halfWidth;
            float f = length * (1 - (float)Math.pow(Math.abs(normalizedS), 3.6));
            curvePoints[i] = center.add(spanAxis.scale(s)).add(forwardAxis.scale(f));
        }

        // 第一部分：三角形扇（双面）
        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 p0 = curvePoints[i];
            Vec3 p1 = curvePoints[i + 1];
            emitTriangleGlow(matrix, buffer, apex, p0, p1, r, g, bl, alpha, glowCenter, glowRadius);
            emitTriangleGlow(matrix, buffer, apex, p1, p0, r, g, bl, alpha, glowCenter, glowRadius);
        }

        // 第二部分：底部条带（双面）
        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 botLeft  = center.add(spanAxis.scale(sValues[i]));
            Vec3 botRight = center.add(spanAxis.scale(sValues[i + 1]));
            Vec3 topLeft  = curvePoints[i];
            Vec3 topRight = curvePoints[i + 1];

            emitQuadGlow(matrix, buffer, botLeft, botRight, topRight, topLeft, r, g, bl, alpha, glowCenter, glowRadius);
        }

        // 第三部分：底部半圆包裹
        int arcPoints = BASE_ARC_SEGMENTS + 1;
        Vec3[] arcCurvePoints = new Vec3[arcPoints];
        for (int i = 0; i < arcPoints; i++) {
            float angle = (float) Math.PI * i / BASE_ARC_SEGMENTS;
            float s = halfWidth * Mth.cos(angle);
            float backOffset = halfWidth * Mth.sin(angle);
            arcCurvePoints[i] = center.add(spanAxis.scale(s)).add(forwardAxis.scale(-backOffset));
        }

        for (int i = 0; i < arcPoints - 1; i++) {
            emitTriangleGlow(matrix, buffer, center, arcCurvePoints[i], arcCurvePoints[i + 1], r, g, bl, alpha, glowCenter, glowRadius);
            emitTriangleGlow(matrix, buffer, center, arcCurvePoints[i + 1], arcCurvePoints[i], r, g, bl, alpha, glowCenter, glowRadius);
        }
    }

    /** 渲染一个平面的实心抛物面图形（含底部半圆包裹），不应用亮度处理 */
    private static void renderParabolaPlaneNoGlow(Matrix4f matrix, BufferBuilder buffer,
                                             Vec3 center, Vec3 spanAxis, Vec3 forwardAxis,
                                             float halfWidth, float length,
                                             float r, float g, float bl, float alpha) {
        if (halfWidth <= 0 || length <= 0) return;

        Vec3 apex = center.add(forwardAxis.scale(length));

        float tipBound = halfWidth * TIP_RATIO;
        float[] sValues = new float[BASE_SEGMENTS * 2 + TIP_SEGMENTS * 2 + 3];
        int idx = 0;
        for (int i = 0; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = -halfWidth + (halfWidth - tipBound) * (float) i / BASE_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = -tipBound + tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= TIP_SEGMENTS; i++) {
            sValues[idx++] = tipBound * (float) i / TIP_SEGMENTS;
        }
        for (int i = 1; i <= BASE_SEGMENTS; i++) {
            sValues[idx++] = tipBound + (halfWidth - tipBound) * (float) i / BASE_SEGMENTS;
        }
        int totalPoints = idx;

        Vec3[] curvePoints = new Vec3[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            float s = sValues[i];
            float normalizedS = s / halfWidth;
            float f = length * (1 - (float)Math.pow(Math.abs(normalizedS), 3.6));
            curvePoints[i] = center.add(spanAxis.scale(s)).add(forwardAxis.scale(f));
        }

        // 第一部分：三角形扇（双面）
        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 p0 = curvePoints[i];
            Vec3 p1 = curvePoints[i + 1];
            emitTriangleNoGlow(matrix, buffer, apex, p0, p1, r, g, bl, alpha);
            emitTriangleNoGlow(matrix, buffer, apex, p1, p0, r, g, bl, alpha);
        }

        // 第二部分：底部条带（双面）
        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 botLeft  = center.add(spanAxis.scale(sValues[i]));
            Vec3 botRight = center.add(spanAxis.scale(sValues[i + 1]));
            Vec3 topLeft  = curvePoints[i];
            Vec3 topRight = curvePoints[i + 1];
            emitQuadNoGlow(matrix, buffer, botLeft, botRight, topRight, topLeft, r, g, bl, alpha);
        }

        // 第三部分：底部半圆包裹（双面）
        int arcPoints = BASE_ARC_SEGMENTS + 1;
        Vec3[] arcCurvePoints = new Vec3[arcPoints];
        for (int i = 0; i < arcPoints; i++) {
            float angle = (float) Math.PI * i / BASE_ARC_SEGMENTS;
            float s = halfWidth * Mth.cos(angle);
            float backOffset = halfWidth * Mth.sin(angle);
            arcCurvePoints[i] = center.add(spanAxis.scale(s)).add(forwardAxis.scale(-backOffset));
        }

        for (int i = 0; i < arcPoints - 1; i++) {
            emitTriangleNoGlow(matrix, buffer, center, arcCurvePoints[i], arcCurvePoints[i + 1], r, g, bl, alpha);
            emitTriangleNoGlow(matrix, buffer, center, arcCurvePoints[i + 1], arcCurvePoints[i], r, g, bl, alpha);
        }
    }

    /** 渲染一个三角形（单面），不应用亮度处理 */
    private static void emitTriangleNoGlow(Matrix4f matrix, BufferBuilder buffer,
                                          Vec3 v0, Vec3 v1, Vec3 v2,
                                          float r, float g, float b, float a) {
        buffer.vertex(matrix, (float)v0.x, (float)v0.y, (float)v0.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(r, g, b, a).endVertex();
    }

    /** 渲染一个四边形（双面），不应用亮度处理 */
    private static void emitQuadNoGlow(Matrix4f matrix, BufferBuilder buffer,
                                      Vec3 bl, Vec3 br, Vec3 tr, Vec3 tl,
                                      float r, float g, float b, float a) {
        // 正面
        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).color(r, g, b, a).endVertex();

        // 反面
        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(r, g, b, a).endVertex();
    }

    /** 计算顶点的亮色混合因子：距离glowCenter越近越亮(返回0~1) */
    private static float glowBlend(Vec3 pos, Vec3 glowCenter, float glowRadius) {
        if (glowRadius <= 0) return 0;
        double dist = pos.distanceTo(glowCenter);
        if (dist >= glowRadius) return 0;
        return (float) (1.0 - dist / glowRadius); // 线性衰减
    }

    /** 计算混合后的颜色 */
    private static float[] applyGlow(float r, float g, float b, float glow) {
        // 向白色混合：color = baseColor + (1 - baseColor) * glow * 0.9
        float strength = glow * 1.0f;
        return new float[] {
            Math.min(1f, r + (1f - r) * strength),
            Math.min(1f, g + (1f - g) * strength),
            Math.min(1f, b + (1f - b) * strength)
        };
    }

    /** 渲染一个三角形（单面），带亮色处理 */
    private static void emitTriangleGlow(Matrix4f matrix, BufferBuilder buffer,
                                          Vec3 v0, Vec3 v1, Vec3 v2,
                                          float r, float g, float b, float a,
                                          Vec3 glowCenter, float glowRadius) {
        float g0 = glowBlend(v0, glowCenter, glowRadius);
        float g1 = glowBlend(v1, glowCenter, glowRadius);
        float g2 = glowBlend(v2, glowCenter, glowRadius);
        float[] c0 = applyGlow(r, g, b, g0);
        float[] c1 = applyGlow(r, g, b, g1);
        float[] c2 = applyGlow(r, g, b, g2);
        buffer.vertex(matrix, (float)v0.x, (float)v0.y, (float)v0.z).color(c0[0], c0[1], c0[2], a).endVertex();
        buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(c1[0], c1[1], c1[2], a).endVertex();
        buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(c2[0], c2[1], c2[2], a).endVertex();
    }

    /** 渲染一个四边形（双面），带亮色处理 */
    private static void emitQuadGlow(Matrix4f matrix, BufferBuilder buffer,
                                      Vec3 bl, Vec3 br, Vec3 tr, Vec3 tl,
                                      float r, float g, float b, float a,
                                      Vec3 glowCenter, float glowRadius) {
        float gBL = glowBlend(bl, glowCenter, glowRadius);
        float gBR = glowBlend(br, glowCenter, glowRadius);
        float gTR = glowBlend(tr, glowCenter, glowRadius);
        float gTL = glowBlend(tl, glowCenter, glowRadius);
        float[] cBL = applyGlow(r, g, b, gBL);
        float[] cBR = applyGlow(r, g, b, gBR);
        float[] cTR = applyGlow(r, g, b, gTR);
        float[] cTL = applyGlow(r, g, b, gTL);

        // 正面
        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(cBL[0], cBL[1], cBL[2], a).endVertex();
        buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).color(cBR[0], cBR[1], cBR[2], a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(cTR[0], cTR[1], cTR[2], a).endVertex();

        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(cBL[0], cBL[1], cBL[2], a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(cTR[0], cTR[1], cTR[2], a).endVertex();
        buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).color(cTL[0], cTL[1], cTL[2], a).endVertex();

        // 反面
        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(cBL[0], cBL[1], cBL[2], a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(cTR[0], cTR[1], cTR[2], a).endVertex();
        buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).color(cBR[0], cBR[1], cBR[2], a).endVertex();

        buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(cBL[0], cBL[1], cBL[2], a).endVertex();
        buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).color(cTL[0], cTL[1], cTL[2], a).endVertex();
        buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(cTR[0], cTR[1], cTR[2], a).endVertex();
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