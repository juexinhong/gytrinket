package com.gytrinket.gytrinket.client.effect.particle;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.client.shader.ModShaders;
import com.gytrinket.gytrinket.config.ClientConfig;
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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 立体护盾粒子渲染器。
 * <p>
 * 外形保持旧版：贴图轮廓挤出的六边形棱柱（带 0.06 格厚度）。
 * 材质换成「三渲二水晶」质感（参考 BV1izTWzbEsV《blender三渲二水晶-风格化》成片）：
 * <ol>
 *   <li>垂直色带：壳底深蓝 → 中段蓝 → 上段青 → 壳顶近白；</li>
 *   <li>三渲二光照：光照量化成 3 档，逐三角面法线让每档正好落在一个切面上；</li>
 *   <li>轮廓辉光 + 刻面棱线 + 裂纹（Voronoi）+ 加色 Bloom 光晕。</li>
 * </ol>
 * 每帧只有三次 draw call：光晕、本体、高光。
 */
public class ShieldParticleRenderer {

    static final ResourceLocation[] SHIELD_TEXTURES = {
        ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/particle/shield_particle_1_1.png"),
        ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/particle/shield_particle_1_2.png"),
        ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/particle/shield_particle_1_3.png"),
        ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/particle/shield_particle_1_4.png"),
        ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/particle/shield_particle_1_5.png")
    };

    // ===== 外观参数 =====
    /** 粒子最大半径（格），与旧版一致 */
    private static final float SHARD_MAX_SIZE = 0.16F;
    /** 出现时的过冲量 */
    private static final float POP_OVERSHOOT = 1.15F;

    /**
     * 极点带阈值：sinTheta 小于它（≈ 离极点 8.6° 以内）就直接用固定朝向。
     * 原版是 0.01（≈0.6°），几乎等于没处理；这里放宽到能真正盖住极点附近。
     */
    private static final float POLE_SIN_THETA = 0.15F;

    public static void render(PoseStack poseStack,
                              net.minecraft.client.renderer.MultiBufferSource bufferSource,
                              Camera camera,
                              float partialTicks) {
        if (ShaderModCompat.isRenderingShadows()) {
            return;
        }

        if (ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get()) {
            int style = ClientConfig.SHIELD_PARTICLE_STYLE.get();
            if (style == 2) {
                renderVanilla(poseStack, camera, partialTicks);
            } else {
                renderVolumetric(poseStack, camera, partialTicks, style);
            }
        } else {
            renderVanilla(poseStack, camera, partialTicks);
        }
    }

    /**
     * 出现 / 消散 / 让位 的透明度包络 —— **三套形象共用这一份**。
     * <p>
     * 让位规则：被标记后先等出现动画走完（{@link ShieldParticleRenderManager#APPEAR_TICKS}），
     * 再在 {@link ShieldParticleRenderManager#FADE_TICKS} 刻内压到 0。
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
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

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
            Matrix4f matrix = poseStack.last().pose();

            float glowSize = size * 1.1F;
            float glowAlpha = alpha * 0.5F;

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, texture);

            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] glowXyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, glowSize);
            int glowPackedColor = ShieldShardMesh.packColor(0.5F, 0.7F, 1.0F, glowAlpha);
            for (int i = 0; i < 4; i++) {
                bufferBuilder.addVertex(matrix, glowXyz[i * 3], glowXyz[i * 3 + 1], glowXyz[i * 3 + 2])
                             .setUv(uvs[i * 2], uvs[i * 2 + 1])
                             .setColor(glowPackedColor);
            }
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

            bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] xyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, size);
            int packedColor = ShieldShardMesh.packColor(1.0F, 1.0F, 1.0F, alpha);
            for (int i = 0; i < 4; i++) {
                bufferBuilder.addVertex(matrix, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                             .setUv(uvs[i * 2], uvs[i * 2 + 1])
                             .setColor(packedColor);
            }
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }

        poseStack.popPose();

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
     */
    public static void renderWithSavedMatrices(PoseStack poseStack,
                                               net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                               Camera camera,
                                               float partialTicks,
                                               Matrix4f savedProjection,
                                               Matrix4f savedModelView,
                                               Matrix4f savedPoseStackMat) {
        if (savedProjection == null || savedModelView == null || savedPoseStackMat == null) return;

        Matrix4f irisProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        Minecraft mc = Minecraft.getInstance();
        Camera cam = camera != null ? camera : mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = cam.getPosition();

        Matrix4f matrix = new Matrix4f(savedModelView);
        matrix.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        drawParticles(matrix, cam, partialTicks, 0);

        modelViewStack.popMatrix();
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

        ShaderInstance shader = ModShaders.getShieldGlassShader();
        if (shader == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Uniform uMode = shader.getUniform("PassMode");
        Uniform uTime = shader.getUniform("GemTime");
        Uniform uViewRot = shader.getUniform("ViewRot");
        if (uViewRot != null) {
            Quaternionf viewRotation = new Quaternionf(camera.rotation()).conjugate(new Quaternionf());
            uViewRot.set(new Matrix4f().rotation(viewRotation));
        }
        if (uTime != null) {
            uTime.set(animationTime(mc, partialTicks));
        }
        // 形象：0 = 三渲二水晶，1 = 旧立体宝石（同一套几何，换材质）
        Uniform uStyle = shader.getUniform("StyleMode");
        if (uStyle != null) {
            uStyle.set(style >= 1 ? 1.0F : 0.0F);
        }

        Quaternionf camRotation = new Quaternionf(camera.rotation());
        Vector3f camRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camRotation);
        Vector3f camUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camRotation);
        Vector3f camForward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(camRotation);
        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

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
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (uMode != null) {
            uMode.set(1.0F);
        }
        BufferBuilder haloBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            ShieldShardMesh.buildHalo(haloBuffer, matrix, pose.x, pose.y, pose.z,
                    camRight, camUp, pose.dir,
                    pose.size, pose.alpha, pose.seedA, pose.seedB, pose.heightT);
        }
        BufferUploader.drawWithShader(haloBuffer.buildOrThrow());

        // ---- Pass 2: 晶体本体（半透明，由远到近叠加）----
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (uMode != null) {
            uMode.set(0.0F);
        }
        BufferBuilder bodyBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            emitShard(style, bodyBuffer, matrix, pose);
        }
        BufferUploader.drawWithShader(bodyBuffer.buildOrThrow());

        // ---- Pass 3: 加色高光（切面反射 / 倒角棱线 / 虹彩）----
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (uMode != null) {
            uMode.set(2.0F);
        }
        BufferBuilder highlightBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Pose pose : poses) {
            emitShard(style, highlightBuffer, matrix, pose);
        }
        BufferUploader.drawWithShader(highlightBuffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
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
     * 平面基向量与旧版完全一致（由球面法线推导），保证外形不变。
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
        // 整层壳共享同一条色带（壳顶近白、壳底深蓝），而不是每片各画各的。
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
        // 原版就是标准经纬框架：up 由球面角 (theta, phi) 直接算出，
        // 赤道附近非常正，越靠近两极越不稳（sinTheta → 0）。
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
            // 之前用"方位角的极限方向"，虽然不含相机，但会随粒子自身的 φ 变，
            // 极点那几颗之间仍会互相错开。这里直接钉死成世界 +Z（再正交化一下），
            // 不带任何方位角/相机信息：落进极点带的粒子朝向完全一致、完全固定。
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
