package com.gytrinket.gytrinket.core.attack_mode.electric_discharge.client;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.client.shader.ModShaders;
import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.LightningRenderData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 闪电体积渲染器（逐段渲染）。
 * <p>
 * 每条闪电段（节点到节点）作为独立胶囊体渲染，延申/消退在 Java 端按根距计算。
 * 端帽上的描边在着色器内被抑制，避免在连接处出现圆形分割。
 */
public class LightningRenderManager {
    private static final List<LightningRenderData> lightningDataList = new CopyOnWriteArrayList<>();

    // ===== 动画常量 =====
    /** 闪电前端（延申中）的亮度 */
    private static final float HEAD_BRIGHTNESS = 15.0f;
    /** 消退结束时的最低亮度（从15消退至10） */
    private static final float MIN_BRIGHTNESS = 10.0f;
    /** 消退终点蓝色 */
    private static final float[] BLUE_COLOR = {0.35f, 0.65f, 1.0f};
    /** 辉光范围（格），用于包围盒外扩和着色器辉光衰减 */
    private static final float GLOW_RANGE = 0.18f;

    // ===== 光影兼容：保存的矩阵状态 =====
    private static Matrix4f savedProjectionMatrix = null;
    private static Matrix4f savedModelViewMatrix = null;
    private static Matrix4f savedPoseStackMatrix = null;

    public static void addLightning(List<ElectricDischargeManager.LightningSegment> segments) {
        addLightning(segments, 6, -1.0f);
    }

    /**
     * 添加闪电线段到渲染列表。
     * <p>
     * duration 为基础持续时间（tick，默认 6 刻）；主干每增加一段，总时间增加 0.3 刻，
     * 最后向下取整。分支的段数不计入时间（分支随主干时间分配，不额外延长闪电）。
     */
    public static void addLightning(List<ElectricDischargeManager.LightningSegment> segments, int duration, float maxWidth) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

        double totalLength = 0;
        for (ElectricDischargeManager.LightningSegment segment : segments) {
            totalLength += segment.start().distanceTo(segment.end());
        }

        // 主干段数：从第一段开始连续相连的段（遇到断点即为主干结束，之后的段为分支）
        int mainChainSegments = 1;
        if (!segments.isEmpty()) {
            for (int i = 1; i < segments.size(); i++) {
                if (segments.get(i).start().distanceTo(segments.get(i - 1).end()) < 1e-6) {
                    mainChainSegments++;
                } else {
                    break;
                }
            }
        }

        // 总时间 = 基础时间 + 主干每增加一段加 0.3 刻，向下取整
        double durationD = Math.max(1, duration) + 0.3 * (mainChainSegments - 1);
        int totalDuration = (int) Math.floor(durationD);
        lightningDataList.add(new LightningRenderData(segments, currentTime, totalDuration, totalLength, maxWidth));
    }

    /**
     * 渲染事件处理器：自动分发到光影/非光影渲染路径。
     */
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (ShaderModCompat.isRenderingShadows()) return;

        long currentTime = mc.level.getGameTime();
        lightningDataList.removeIf(data -> data.isExpired(currentTime));
        if (lightningDataList.isEmpty()) return;

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();

        if (shaderActive) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                renderWithSavedMatrices(event);
            }
        } else {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                render(event);
            }
        }
    }

    /**
     * 非光影模式：在 AFTER_PARTICLES 直接渲染。
     */
    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = ModShaders.getLightningVolShader();
        if (shader == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Matrix4f vertexMatrix = poseStack.last().pose();
        Matrix4f invModelView = new Matrix4f(RenderSystem.getModelViewStack()).invert();
        long currentTime = mc.level.getGameTime();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        for (LightningRenderData data : lightningDataList) {
            renderData(shader, vertexMatrix, invModelView, data, camPos, currentTime);
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 光影模式：在 AFTER_LEVEL 使用保存的矩阵渲染。
     */
    private static void renderWithSavedMatrices(RenderLevelStageEvent event) {
        if (savedProjectionMatrix == null || savedModelViewMatrix == null || savedPoseStackMatrix == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = ModShaders.getLightningVolShader();
        if (shader == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        long currentTime = mc.level.getGameTime();

        Matrix4f irisProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setProjectionMatrix(savedProjectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        Matrix4f vertexMatrix = new Matrix4f(savedModelViewMatrix);
        Matrix4f invModelView = new Matrix4f(vertexMatrix).invert();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        for (LightningRenderData data : lightningDataList) {
            renderData(shader, vertexMatrix, invModelView, data, camPos, currentTime);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        modelViewStack.popMatrix();
        RenderSystem.setProjectionMatrix(irisProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 渲染一条闪电的所有分段：按渐进式延申生命周期计算可见状态，逐段绘制胶囊体。
     */
    private static void renderData(ShaderInstance shader, Matrix4f vertexMatrix, Matrix4f invModelView,
                                   LightningRenderData data, Vec3 camPos, long currentTime) {
        List<ElectricDischargeManager.LightningSegment> segments = data.getSegments();
        if (segments.isEmpty()) return;

        double totalLength = data.getTotalLength();
        if (totalLength <= 0) return;

        Vec3 origin = segments.get(0).start();

        int extendTicks = data.getExtendTicks();
        int fadeTicks = data.getFadeTicks();

        float maxWidth;
        float minWidth;
        float maxWidthOverride = data.getMaxWidthOverride();
        if (maxWidthOverride > 0) {
            maxWidth = maxWidthOverride;
            minWidth = maxWidth * 0.05f;
        } else {
            double defaultLength = 6.0;
            double scaleRatio = Math.max(0.3, 1 + (totalLength / defaultLength - 1) * 0.25);
            maxWidth = 0.08f * (float) scaleRatio;
            minWidth = 0.01f * (float) scaleRatio;
        }

        // 收集可见段，按透明度升序绘制：重叠处最后绘制的是最不透明者，避免透明度叠加产生亮斑
        List<RenderJob> jobs = new ArrayList<>();

        // 将平铺线段分组为连续链（主路径 + 各分支）
        List<List<ElectricDischargeManager.LightningSegment>> chains = new ArrayList<>();
        List<ElectricDischargeManager.LightningSegment> currentChain = new ArrayList<>();
        Vec3 lastEnd = null;
        for (ElectricDischargeManager.LightningSegment seg : segments) {
            if (lastEnd != null && seg.start().distanceTo(lastEnd) > 1e-6) {
                chains.add(currentChain);
                currentChain = new ArrayList<>();
            }
            currentChain.add(seg);
            lastEnd = seg.end();
        }
        if (!currentChain.isEmpty()) {
            chains.add(currentChain);
        }

        for (List<ElectricDischargeManager.LightningSegment> chain : chains) {
            double chainLen = 0;
            for (ElectricDischargeManager.LightningSegment seg : chain) {
                chainLen += seg.start().distanceTo(seg.end());
            }
            if (chainLen <= 1e-4) continue;

            Vec3 chainStart = chain.get(0).start();
            double chainStartDist = origin.distanceTo(chainStart);
            float chainStartFrac = (float) Math.min(1.0, chainStartDist / totalLength);
            // 链起点半径 = 主路径在该位置（分叉点）的全局半径，保证与主干连续
            float chainStartRadius = minWidth + (maxWidth - minWidth) * (1.0f - (float) Math.sqrt(chainStartFrac));

            double cumLen = 0;

            for (ElectricDischargeManager.LightningSegment segment : chain) {
                Vec3 start = segment.start();
                Vec3 end = segment.end();

                double distanceFromOrigin = origin.distanceTo(start);
                double segLen = start.distanceTo(end);
                if (segLen <= 1e-4) continue;

                float segStartFrac = (float) Math.min(1.0, distanceFromOrigin / totalLength);
                float segEndFrac = (float) Math.min(1.0, (distanceFromOrigin + segLen) / totalLength);

                float segStartTime = data.getStartTime() + extendTicks * segStartFrac;
                float segEndTime = data.getStartTime() + extendTicks * segEndFrac;

                // 尚未延申到该段：不渲染
                if (currentTime < segStartTime) {
                    cumLen += segLen;
                    continue;
                }

                // 每段一旦点亮，渲染完整圆柱体
                float fadeT = 0.0f;
                if (currentTime >= segEndTime) {
                    fadeT = (float) (currentTime - segEndTime) / fadeTicks;
                    fadeT = Mth.clamp(fadeT, 0.0f, 1.0f);
                    if (fadeT >= 1.0f) {
                        cumLen += segLen;
                        continue;
                    }
                }

                float alpha = 1.0f - fadeT;
                float brightness = Mth.lerp(fadeT, HEAD_BRIGHTNESS, MIN_BRIGHTNESS);
                float red, green, blue;
                if (fadeT <= 0.0f) {
                    red = 1.0f;
                    green = 1.0f;
                    blue = 1.0f;
                } else {
                    red = Mth.lerp(fadeT, 1.0f, BLUE_COLOR[0]);
                    green = Mth.lerp(fadeT, 1.0f, BLUE_COLOR[1]);
                    blue = Mth.lerp(fadeT, 1.0f, BLUE_COLOR[2]);
                }

                // 半径：链内局部收窄（链起点粗、末端细）。
                // 段数越少的分支，收窄越陡峭，尖端越细。
                float localStart = (float) Math.min(1.0, cumLen / chainLen);
                float localEnd = (float) Math.min(1.0, (cumLen + segLen) / chainLen);
                float startRadius = minWidth + (chainStartRadius - minWidth) * (1.0f - (float) Math.sqrt(localStart));
                float endRadius = minWidth + (chainStartRadius - minWidth) * (1.0f - (float) Math.sqrt(localEnd));

                jobs.add(new RenderJob(alpha, start, end, startRadius, endRadius, red, green, blue, brightness));

                cumLen += segLen;
            }
        }

        jobs.sort((a, b) -> Float.compare(a.alpha, b.alpha));

        // 两趟渲染：先画泛光底层（0），再画不透明的核心层（1）覆盖其上。
        // 这样弯曲连接处由核心覆盖，泛光不产生连接描边。
        for (RenderJob job : jobs) {
            renderSegment(shader, vertexMatrix, invModelView, job.start, job.end,
                    job.startRadius, job.endRadius, job.red, job.green, job.blue, job.alpha, job.brightness, camPos, 0.0f);
        }
        for (RenderJob job : jobs) {
            renderSegment(shader, vertexMatrix, invModelView, job.start, job.end,
                    job.startRadius, job.endRadius, job.red, job.green, job.blue, job.alpha, job.brightness, camPos, 1.0f);
        }
    }

    /** 单个闪电段的渲染任务（按透明度升序排序，重叠处最不透明者胜出） */
    private static final class RenderJob {
        final float alpha;
        final Vec3 start;
        final Vec3 end;
        final float startRadius;
        final float endRadius;
        final float red, green, blue, brightness;

        RenderJob(float alpha, Vec3 start, Vec3 end, float startRadius, float endRadius,
                  float red, float green, float blue, float brightness) {
            this.alpha = alpha;
            this.start = start;
            this.end = end;
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.brightness = brightness;
        }
    }

    /**
     * 为单个闪电段构建包围盒并设置着色器 uniform，绘制一个 3D 胶囊体。
     */
    private static void renderSegment(ShaderInstance shader, Matrix4f vertexMatrix, Matrix4f invModelView,
                                      Vec3 start, Vec3 end, float startRadius, float endRadius,
                                      float red, float green, float blue, float alpha, float brightness,
                                      Vec3 camPos, float renderMode) {
        Vec3 a = start.subtract(camPos);
        Vec3 b = end.subtract(camPos);
        Vec3 ab = b.subtract(a);
        float len = (float) ab.length();
        if (len < 1e-3f) return;

        Vec3 axis = ab.normalize();

        Vec3 up = findUp(axis);
        Vec3 right = axis.cross(up).normalize();
        up = right.cross(axis).normalize();

        float maxRadius = Math.max(startRadius, endRadius);
        float boxRad = maxRadius + GLOW_RANGE;
        float halfLen = len * 0.5f + boxRad + 0.06f;
        Vec3 center = a.add(b).scale(0.5);

        setUniformSafe(shader, "SegStart", (float) a.x, (float) a.y, (float) a.z);
        setUniformSafe(shader, "SegEnd", (float) b.x, (float) b.y, (float) b.z);
        setUniformSafe(shader, "SegAxis", (float) axis.x, (float) axis.y, (float) axis.z);
        setUniformSafe(shader, "RadiusStart", startRadius);
        setUniformSafe(shader, "RadiusEnd", endRadius);
        setUniformSafe(shader, "CylColor", red, green, blue, alpha);
        setUniformSafe(shader, "Brightness", brightness);
        setUniformSafe(shader, "BoxRad", boxRad);
        setUniformSafe(shader, "RenderMode", renderMode);
        if (shader.getUniform("InvModelViewMat") != null) {
            shader.getUniform("InvModelViewMat").set(invModelView);
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        renderBox(vertexMatrix, buffer, center, right, up, axis, boxRad, boxRad, halfLen);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /**
     * 渲染一个由局部基向量 (u, v, w) 定义的有向包围盒（12个三角形）。
     */
    private static void renderBox(Matrix4f matrix, BufferBuilder buffer,
                                  Vec3 center, Vec3 u, Vec3 v, Vec3 w,
                                  float hu, float hv, float hw) {
        Vec3 uP = u.scale(hu), uN = u.scale(-hu);
        Vec3 vP = v.scale(hv), vN = v.scale(-hv);
        Vec3 wP = w.scale(hw), wN = w.scale(-hw);

        Vec3 v000 = center.add(uN).add(vN).add(wN);
        Vec3 v001 = center.add(uN).add(vN).add(wP);
        Vec3 v010 = center.add(uN).add(vP).add(wN);
        Vec3 v011 = center.add(uN).add(vP).add(wP);
        Vec3 v100 = center.add(uP).add(vN).add(wN);
        Vec3 v101 = center.add(uP).add(vN).add(wP);
        Vec3 v110 = center.add(uP).add(vP).add(wN);
        Vec3 v111 = center.add(uP).add(vP).add(wP);

        quad(matrix, buffer, v001, v101, v111, v011);
        quad(matrix, buffer, v100, v000, v010, v110);
        quad(matrix, buffer, v101, v111, v110, v100);
        quad(matrix, buffer, v000, v001, v011, v010);
        quad(matrix, buffer, v011, v111, v110, v010);
        quad(matrix, buffer, v000, v100, v101, v001);
    }

    private static void quad(Matrix4f matrix, BufferBuilder buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        vertex(matrix, buffer, a);
        vertex(matrix, buffer, b);
        vertex(matrix, buffer, c);
        vertex(matrix, buffer, a);
        vertex(matrix, buffer, c);
        vertex(matrix, buffer, d);
    }

    private static void vertex(Matrix4f matrix, BufferBuilder buffer, Vec3 pos) {
        buffer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).setUv(0.0f, 0.0f);
    }

    private static Vec3 findUp(Vec3 axis) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(axis.dot(up)) > 0.99) {
            up = new Vec3(1, 0, 0);
        }
        return up.subtract(axis.scale(axis.dot(up))).normalize();
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
}
