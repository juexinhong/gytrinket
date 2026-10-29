package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.gy_mod.gy_trinket.config.ClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 立体护盾粒子渲染器（1.20.1 移植版）。
 * <p>
 * 三套形象，靠 {@code ClientConfig.SHIELD_PARTICLE_STYLE} 切换：
 * <ul>
 *   <li>0 = 三渲二水晶：14 面刻面六边形宝石（{@link ShieldShardMesh}）；</li>
 *   <li>1 = 旧立体宝石：历史贴图棱柱（{@link ShieldParticleTexturedRenderer}）；</li>
 *   <li>2 = 平面四边形：原版透明度渲染（最省性能）。</li>
 * </ul>
 * 体积渲染每帧只有三次 draw call：光晕、本体、高光。
 * <p>
 * 与 1.21.1 的差异只在渲染 API：
 * {@code Tesselator.getInstance().getBuilder() + begin(...) + end()}、
 * 顶点链写 {@code vertex().uv().color().normal().endVertex()}、
 * {@code RenderSystem.getModelViewStack()} 是 {@link PoseStack} 且要 {@code applyModelViewMatrix()}。
 */
public class ShieldParticleRenderer {

    static final ResourceLocation[] SHIELD_TEXTURES = {
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_1.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_2.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_3.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_4.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_5.png")
    };

    // ===== 外观参数 =====
    /** 粒子最大半径（格），与旧版一致 */
    private static final float SHARD_MAX_SIZE = 0.16F;
    /** 出现时的过冲量 */
    private static final float POP_OVERSHOOT = 1.15F;

    /**
     * 极点带阈值：sinTheta 小于它（≈ 离极点 8.6° 以内）就直接用固定朝向。
     */
    private static final float POLE_SIN_THETA = 0.15F;

    public static void render(PoseStack poseStack,
                              MultiBufferSource bufferSource,
                              Camera camera,
                              float partialTicks) {
        if (ShaderModCompat.isRenderingShadows()) {
            return;
        }

        if (ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get()) {
            int style = ClientConfig.SHIELD_PARTICLE_STYLE.get();
            if (style == 2) {
                renderVanilla(poseStack, camera, partialTicks);
            } else if (style == 1) {
                // 旧版「贴图转立体」：历史棱柱渲染器
                ShieldParticleTexturedRenderer.render(poseStack, camera, partialTicks);
            } else {
                renderVolumetric(poseStack, camera, partialTicks, style);
            }
        } else {
            renderVanilla(poseStack, camera, partialTicks);
        }
    }

    /**
     * 出现 / 消散 / 让位 的透明度包络 —— **三套形象共用这一份**。
     */
    static float particleAlpha(ShieldParticleData particle, float partialTicks) {
        float age = particle.age + partialTicks;
        float lifeT = age / particle.lifetime;
        if (lifeT < 0.0F) {
            lifeT = 0.0F;
        }
        float appear = Math.min(1.0F, lifeT / 0.14F);
        float fadeIn = appear * appear * (3.0F - 2.0F * appear);
        float fadeOut = 1.0F - smoothstep((lifeT - 0.50F) / 0.50F);

        float leaving = 1.0F;
        if (particle.yielding) {
            float startAge = Math.max((float) particle.yieldMarkAge,
                    (float) ShieldParticleRenderManager.APPEAR_TICKS);
            float t = (age - startAge) / (float) ShieldParticleRenderManager.FADE_TICKS;
            t = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
            leaving = 1.0F - t;
        }
        return 0.95F * fadeIn * fadeOut * leaving;
    }

    // ===== 原版透明度渲染（平面四边形回退） =====

    private static void renderVanilla(PoseStack poseStack,
                                      Camera camera,
                                      float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cameraPos = (camera != null ? camera : mc.gameRenderer.getMainCamera()).getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        renderVanillaQuads(poseStack.last().pose(), partialTicks);
        poseStack.popPose();
    }

    /**
     * 平面四边形本体。传入的矩阵已经包含「相机旋转 × 平移(-相机位置)」：
     * 非光影路径由 PoseStack 传入，光影路径传入 AFTER_PARTICLES 保存的矩阵。
     */
    private static void renderVanillaQuads(Matrix4f matrix, float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        for (ShieldParticleData particle : manager.getParticles()) {
            int textureIndex = Math.min(particle.age - 1, SHIELD_TEXTURES.length - 1);
            if (textureIndex < 0) continue;

            Entity entity = mc.level.getEntity(particle.entityId);
            if (entity == null) continue;

            double entityX = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
            double entityY = entity.yOld + (entity.getY() - entity.yOld) * partialTicks;
            double entityZ = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;

            double originX = entityX + particle.originOffsetX;
            double originY = entityY + particle.originOffsetY;
            double originZ = entityZ + particle.originOffsetZ;

            double px = originX + particle.offsetX;
            double py = originY + particle.offsetY;
            double pz = originZ + particle.offsetZ;

            ResourceLocation texture = SHIELD_TEXTURES[textureIndex];

            float size = calculateSize(particle.age);
            float alpha = particleAlpha(particle, partialTicks);
            if (alpha <= 0.004F) continue;

            float dx = (float) (px - originX);
            float dy = (float) (py - originY);
            float dz = (float) (pz - originZ);
            float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            float rightX, rightY, rightZ, upX, upY, upZ;

            if (r > 0.0001f) {
                float theta = (float) Math.acos(Math.max(-1.0, Math.min(1.0, dy / r)));
                float phi = (float) Math.atan2(dz, dx);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                rightX = -sinPhi; rightY = 0; rightZ = cosPhi;
                upX = -cosTheta * cosPhi; upY = sinTheta; upZ = -cosTheta * sinPhi;

                if (sinTheta < 0.01f) {
                    float hDirX = (float) (originX - px), hDirZ = (float) (originZ - pz);
                    float hLen = (float) Math.sqrt(hDirX * hDirX + hDirZ * hDirZ);
                    if (hLen > 0.001f) {
                        upX = hDirX / hLen; upY = 0; upZ = hDirZ / hLen;
                        rightX = -upZ; rightY = 0; rightZ = upX;
                    }
                }
            } else {
                rightX = 1; rightY = 0; rightZ = 0;
                upX = 0; upY = 1; upZ = 0;
            }

            float[] uvs = {0, 1, 0, 0, 1, 0, 1, 1};

            float glowSize = size * 1.1F;
            float glowAlpha = alpha * 0.5F;

            RenderSystem.setShaderTexture(0, texture);

            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] glowXyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, glowSize);
            for (int i = 0; i < 4; i++) {
                bufferBuilder.vertex(matrix, glowXyz[i * 3], glowXyz[i * 3 + 1], glowXyz[i * 3 + 2])
                             .uv(uvs[i * 2], uvs[i * 2 + 1])
                             .color(0.5F, 0.7F, 1.0F, glowAlpha)
                             .endVertex();
            }
            BufferUploader.drawWithShader(bufferBuilder.end());

            bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] xyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, size);
            for (int i = 0; i < 4; i++) {
                bufferBuilder.vertex(matrix, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                             .uv(uvs[i * 2], uvs[i * 2 + 1])
                             .color(1.0F, 1.0F, 1.0F, alpha)
                             .endVertex();
            }
            BufferUploader.drawWithShader(bufferBuilder.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static float[] calculateVertices(double px, double py, double pz,
                                             float rightX, float rightY, float rightZ,
                                             float upX, float upY, float upZ,
                                             float size) {
        float halfSize = size;
        float[] xyz = new float[12];

        xyz[0] = (float) (px + (-halfSize * rightX - halfSize * upX));
        xyz[1] = (float) (py + (-halfSize * rightY - halfSize * upY));
        xyz[2] = (float) (pz + (-halfSize * rightZ - halfSize * upZ));

        xyz[3] = (float) (px + (-halfSize * rightX + halfSize * upX));
        xyz[4] = (float) (py + (-halfSize * rightY + halfSize * upY));
        xyz[5] = (float) (pz + (-halfSize * rightZ + halfSize * upZ));

        xyz[6] = (float) (px + (halfSize * rightX + halfSize * upX));
        xyz[7] = (float) (py + (halfSize * rightY + halfSize * upY));
        xyz[8] = (float) (pz + (halfSize * rightZ + halfSize * upZ));

        xyz[9] = (float) (px + (halfSize * rightX - halfSize * upX));
        xyz[10] = (float) (py + (halfSize * rightY - halfSize * upY));
        xyz[11] = (float) (pz + (halfSize * rightZ - halfSize * upZ));

        return xyz;
    }

    // ===== 体积渲染（六边形晶体 + 光晕） =====

    /**
     * 光影模式：使用保存的矩阵（Iris composite 之前捕获的）渲染。
     * <p>
     * <b>顶点矩阵必须用事件 PoseStack（相机旋转 R），不能用 savedModelView。</b>
     * 1.20.1 的 {@code LevelRenderer.renderLevel} 只把相机旋转放在传入的 PoseStack 里，
     * {@code RenderSystem.getModelViewStack()} 在 AFTER_PARTICLES 仍是单位阵（直到画云时才
     * {@code push} 相机旋转）。照搬 1.21.1（那边的 modelview 栈就是相机旋转）会让顶点矩阵
     * 变成 I：粒子簇不随镜头旋转、像贴在屏幕上——这就是“光影下旋转错误”。
     * 顶点 = {@code R × T(-camPos)}，与 1.19.2 的 renderWithSavedMatrices 同一套修法。
     */
    public static void renderWithSavedMatrices(PoseStack poseStack,
                                               MultiBufferSource bufferSource,
                                               Camera camera,
                                               float partialTicks,
                                               Matrix4f savedProjection,
                                               Matrix4f savedModelView,
                                               Matrix4f savedPoseStackMat) {
        if (savedProjection == null || savedModelView == null || savedPoseStackMat == null) return;

        Matrix4f irisProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.last().pose().identity();
        RenderSystem.applyModelViewMatrix();

        Minecraft mc = Minecraft.getInstance();
        Camera cam = camera != null ? camera : mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = cam.getPosition();

        // JOML 的 Matrix4f.translate 是后乘（M×T），等价于 1.19.2 的 multiplyWithTranslation。
        Matrix4f matrix = new Matrix4f(savedPoseStackMat);
        matrix.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        boolean volumetric = ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get();
        int style = ClientConfig.SHIELD_PARTICLE_STYLE.get();
        if (!volumetric || style == 2) {
            // 平面四边形：与无光影路径的 render() 分流一致，用同一份矩阵保证位置/动画相同
            renderVanillaQuads(matrix, partialTicks);
        } else if (style == 1) {
            ShieldParticleTexturedRenderer.renderWithMatrix(matrix, cameraPos, partialTicks);
        } else {
            drawParticles(matrix, cam, partialTicks, 0);
        }

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(irisProjection, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    private static void renderVolumetric(PoseStack poseStack, Camera camera, float partialTicks, int style) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = camera != null ? camera : mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = cam.getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        drawParticles(poseStack.last().pose(), cam, partialTicks, style);
        poseStack.popPose();
    }

    private static void drawParticles(Matrix4f matrix, Camera camera, float partialTicks, int style) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        // 1.20.1 的 ShaderInstance.apply() 会按 JSON 里的 blend 覆盖 RenderSystem 的混合状态，
        // 而自定义 uniform（PassMode / ViewRot）在这条管线里不可靠，所以三个 Pass 各用一个
        // ShaderInstance：同一套 vsh/fsh，只靠 JSON 里的 blend + PassMode 初值区分。
        //
        // ⚠ 本体（shield_glass.json）的 blend 必须写成 separateBlend（显式给 srcalpha/dstalpha，
        // 因子与 alpha 混合等价）。原因：BlendMode 有全局 lastApplied 缓存，值相等时 apply()
        // 直接 return 不下发 GL 状态；本体的 (srcalpha, 1-srcalpha) 与原生 shader 值相等，
        // 会被跳过、实际沿用上一个 Pass（光晕）的加色混合 → 实机白天整簇发白、没有暗面。
        // separate 模式不等于任何原生模式，每次都会重新下发。**不要"精简"回单项写法。**
        ShaderInstance bodyShader = ModShaders.getShieldGlassShader();
        ShaderInstance addShader = ModShaders.getShieldGlassAddShader();
        ShaderInstance hiShader = ModShaders.getShieldGlassHiShader();
        if (bodyShader == null || addShader == null || hiShader == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Matrix4f viewRotMatrix = computeViewRotation();
        float gemTime = animationTime(mc, partialTicks);
        float styleUniform = style >= 1 ? 1.0F : 0.0F;
        setCommonUniforms(bodyShader, viewRotMatrix, gemTime, styleUniform);
        setCommonUniforms(addShader, viewRotMatrix, gemTime, styleUniform);
        setCommonUniforms(hiShader, viewRotMatrix, gemTime, styleUniform);

        Quaternionf camRotation = new Quaternionf(camera.rotation());
        Vector3f camRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camRotation);
        Vector3f camUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camRotation);
        Vector3f camForward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(camRotation);
        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // ---- 收集本帧所有粒子姿态，并按到摄像机的距离由远到近排序 ----
        // 半透明叠加必须"远的先画、近的后画"，否则背面的晶体会盖住正面的。
        java.util.List<Pose> poses = new java.util.ArrayList<>();
        for (ShieldParticleData particle : manager.getParticles()) {
            Entity entity = mc.level.getEntity(particle.entityId);
            if (entity == null) continue;
            Pose pose = new Pose();
            if (!computePose(particle, entity, partialTicks, cameraPos, camUp, camForward, pose)) continue;
            float ddx = (float) (cameraPos.x - pose.x);
            float ddy = (float) (cameraPos.y - pose.y);
            float ddz = (float) (cameraPos.z - pose.z);
            pose.distSq = ddx * ddx + ddy * ddy + ddz * ddz;
            poses.add(pose);
        }
        if (poses.isEmpty()) return;
        poses.sort((a, b) -> Float.compare(b.distSq, a.distSq));

        // ---- Pass 1: 加色光晕 ----
        setPassMode(addShader, 1.0F);
        RenderSystem.setShader(() -> addShader);
        // 1.20.1 的 BlendMode 有全局 lastApplied 缓存，且只有 opaque 属性变化时才更新缓存；
        // 光晕/高光 JSON 的 blend 值相同，第二份 apply() 会被跳过、实际沿用上一个 Pass 的混合。
        // 三个 Pass 的顺序是 光晕(加色)→本体(alpha)→高光(加色)，下一帧光晕第一个 apply 若被跳过，
        // 就会继承上一帧本体留下的 alpha 混合 → 光晕被画成黑斑（亮背景下尤其明显）。
        // 与 1.21.1 一样在每个 Pass 前显式设置，保证 GL 状态与 JSON 一致。
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder haloBuffer = Tesselator.getInstance().getBuilder();
        haloBuffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            ShieldShardMesh.buildHalo(haloBuffer, matrix, pose.x, pose.y, pose.z,
                    camRight, camUp, pose.dir,
                    pose.size, pose.alpha, pose.seedA, pose.seedB, pose.heightT);
        }
        BufferUploader.drawWithShader(haloBuffer.end());

        // ---- Pass 2: 晶体本体（半透明，由远到近叠加）----
        setPassMode(bodyShader, 0.0F);
        RenderSystem.setShader(() -> bodyShader);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder bodyBuffer = Tesselator.getInstance().getBuilder();
        bodyBuffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            emitShard(style, bodyBuffer, matrix, pose);
        }
        BufferUploader.drawWithShader(bodyBuffer.end());

        // ---- Pass 3: 加色高光（切面反射 / 倒角棱线 / 虹彩）----
        setPassMode(hiShader, 2.0F);
        RenderSystem.setShader(() -> hiShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder highlightBuffer = Tesselator.getInstance().getBuilder();
        highlightBuffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            emitShard(style, highlightBuffer, matrix, pose);
        }
        BufferUploader.drawWithShader(highlightBuffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 视图旋转矩阵（世界 → 视图）。
     * <p>
     * 1.20.1 的 {@code Camera.rotation()} 本地前向是 +Z（1.21.1 是 -Z），直接
     * {@code new Matrix4f().rotation(rotation.conjugate())} 会差 180° 偏航；而着色器里
     * {@code mat3(ViewRot) * N} 要求的是「和顶点实际经历的视图变换同源」的旋转。
     * <p>
     * 这里取 {@link RenderSystem#getInverseViewRotationMatrix()}（GameRenderer 每帧写进去的
     * 「世界渲染视图 pose 的 3x3 逆」，含 roll）转置回来，与 1.19.2 的修法一致，不可能对不上。
     */
    private static final Matrix4f VIEW_ROT = new Matrix4f();

    private static Matrix4f computeViewRotation() {
        Matrix3f inv = RenderSystem.getInverseViewRotationMatrix();
        // 转置：VIEW_ROT(r,c) = inv(c,r)。JOML 的 set(...) 参数是行主序。
        VIEW_ROT.set(
                inv.m00(), inv.m10(), inv.m20(), 0.0F,
                inv.m01(), inv.m11(), inv.m21(), 0.0F,
                inv.m02(), inv.m12(), inv.m22(), 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        return VIEW_ROT;
    }

    /** 三个 Pass 共用：视角旋转矩阵 / 动画时间 / 形象 */
    private static void setCommonUniforms(ShaderInstance shader, Matrix4f viewRot, float gemTime, float styleUniform) {
        Uniform u;
        if ((u = shader.getUniform("ViewRot")) != null) {
            u.set(viewRot);
        }
        if ((u = shader.getUniform("GemTime")) != null) {
            u.set(gemTime);
        }
        if ((u = shader.getUniform("StyleMode")) != null) {
            u.set(styleUniform);
        }
    }

    private static void setPassMode(ShaderInstance shader, float mode) {
        Uniform u = shader.getUniform("PassMode");
        if (u != null) {
            u.set(mode);
        }
    }

    /** 按形象选网格：0 = 新（14 面水晶），1 = 旧版立体宝石 */
    private static void emitShard(int style, BufferBuilder buffer, Matrix4f matrix, Pose pose) {
        if (style >= 1) {
            ShieldShardMeshLegacy.buildShard(buffer, matrix, pose.x, pose.y, pose.z,
                    pose.right, pose.up, pose.dir,
                    pose.size, pose.alpha, pose.seedA, pose.seedB, pose.heightT,
                    pose.viewX, pose.viewY, pose.viewZ);
        } else {
            ShieldShardMesh.buildShard(buffer, matrix, pose.x, pose.y, pose.z,
                    pose.right, pose.up, pose.dir,
                    pose.size, pose.alpha, pose.seedA, pose.seedB, pose.heightT,
                    pose.viewX, pose.viewY, pose.viewZ);
        }
    }

    private static float animationTime(Minecraft mc, float partialTicks) {
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        float seconds = (gameTime + partialTicks) * 0.05F;
        return seconds % 1200.0F;
    }

    /** 每颗粒子的几何/动画状态 */
    private static final class Pose {
        float x, y, z;
        float viewX, viewY, viewZ;
        final Vector3f right = new Vector3f();
        final Vector3f up = new Vector3f();
        final Vector3f dir = new Vector3f();
        float size, alpha, heightT, seedA, seedB;
        /** 到摄像机的距离平方，用于由远到近排序 */
        float distSq;
    }

    /**
     * 计算粒子当前的姿态：位置、六边形平面基向量、尺寸与透明度。
     */
    private static boolean computePose(ShieldParticleData particle, Entity entity, float partialTicks,
                                       Vec3 cameraPos, Vector3f camUp, Vector3f camForward, Pose out) {
        float age = particle.age + partialTicks;
        float lifeT = age / particle.lifetime;
        if (lifeT >= 1.0F) {
            return false;
        }
        if (lifeT < 0.0F) {
            lifeT = 0.0F;
        }

        // 弹出曲线：easeOutBack
        float appear = Math.min(1.0F, lifeT / 0.14F);
        float a1 = appear - 1.0F;
        float c1 = POP_OVERSHOOT;
        float c3 = c1 + 1.0F;
        float pop = 1.0F + c3 * a1 * a1 * a1 + c1 * a1 * a1;
        if (pop < 0.0F) {
            pop = 0.0F;
        }

        // 透明度：出现 / 消散 / 让位，统一走 particleAlpha（三套形象共用）
        out.alpha = particleAlpha(particle, partialTicks);
        if (out.alpha <= 0.004F) {
            return false;
        }

        float sizeJitter = 0.92F + particle.seedB * 0.16F;
        out.size = SHARD_MAX_SIZE * pop * sizeJitter;
        out.seedA = particle.seedA;
        out.seedB = particle.seedB;
        // 三渲二水晶的垂直色带：粒子生成在半径 1 的球面上，offsetY 就是它的"海拔"。
        float altitude = 0.5F + 0.5F * (float) particle.offsetY;
        out.heightT = altitude < 0.0F ? 0.0F : (altitude > 1.0F ? 1.0F : altitude);

        double entityX = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
        double entityY = entity.yOld + (entity.getY() - entity.yOld) * partialTicks;
        double entityZ = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;

        out.x = (float) (entityX + particle.originOffsetX + particle.offsetX);
        out.y = (float) (entityY + particle.originOffsetY + particle.offsetY);
        out.z = (float) (entityZ + particle.originOffsetZ + particle.offsetZ);

        // 指向摄像机的方向（用于正反面/侧面剔除）
        float vx = (float) (cameraPos.x - out.x);
        float vy = (float) (cameraPos.y - out.y);
        float vz = (float) (cameraPos.z - out.z);
        float vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vlen > 1.0E-4F) {
            out.viewX = vx / vlen;
            out.viewY = vy / vlen;
            out.viewZ = vz / vlen;
        } else {
            out.viewX = 0.0F;
            out.viewY = 0.0F;
            out.viewZ = 1.0F;
        }

        // 球面法线（粒子指向球心方向的反向），与旧版一致
        float dx = (float) (particle.offsetX);
        float dy = (float) (particle.offsetY);
        float dz = (float) (particle.offsetZ);
        float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float dirX, dirY, dirZ;
        if (r > 1.0E-4F) {
            dirX = dx / r;
            dirY = dy / r;
            dirZ = dz / r;
        } else {
            dirX = 0.0F;
            dirY = 1.0F;
            dirZ = 0.0F;
        }
        // 球面排列：法线 = 粒子相对球心的方向
        out.dir.set(dirX, dirY, dirZ);

        // ---- 面内基向量：恢复 git 原版的球面公式 ----
        Vector3f dir = new Vector3f(dirX, dirY, dirZ);
        out.dir.set(dirX, dirY, dirZ);
        float upX, upY, upZ;

        if (r > 1.0E-4F) {
            float theta = (float) Math.acos(Math.max(-1.0, Math.min(1.0, dirY)));
            float phi = (float) Math.atan2(dirZ, dirX);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            upX = -cosTheta * cosPhi;
            upY = sinTheta;
            upZ = -cosTheta * sinPhi;

            // ---- 极点：整段用同一个常量朝向，什么都不依赖 ----
            if (sinTheta < POLE_SIN_THETA) {
                upX = 0.0F;
                upY = 0.0F;
                upZ = 1.0F;
            }
        } else {
            // dir 退化：固定朝向
            upX = 0.0F;
            upY = 0.0F;
            upZ = 1.0F;
        }

        Vector3f up = new Vector3f(upX, upY, upZ);
        up.fma(-dir.dot(up), dir);                          // 严格垂直于法线
        if (up.lengthSquared() < 1.0E-6F) {
            up.set(0.0F, 0.0F, 1.0F);
            up.fma(-dir.dot(up), dir);
        }
        up.normalize();

        out.up.set(up);
        dir.cross(up, out.right);
        out.right.normalize();
        return true;
    }

    private static float smoothstep(float t) {
        float x = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float calculateAlpha(int age) {
        float alpha = 1.0F;
        if (age > 1) {
            alpha = 1.0F - (float) (age) * 0.08F;
        }
        return Math.max(0.0F, alpha);
    }

    private static float calculateSize(int age) {
        if (age <= 1) {
            return 0.034F;
        } else if (age <= 2) {
            return 0.065F;
        } else if (age <= 3) {
            return 0.096F;
        } else if (age <= 4) {
            return 0.128F;
        } else {
            return 0.16F;
        }
    }
}
