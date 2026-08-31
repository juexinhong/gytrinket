package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.config.ClientConfig;
import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.Optional;

public class ShieldParticleRenderer {

    private static final ResourceLocation[] SHIELD_TEXTURES = {
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_1.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_2.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_3.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_4.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_5.png")
    };

    // ===== 体积渲染专用常量 =====
    private static final float THICKNESS_HALF = 0.06F;
    private static final float BRIGHTNESS = 8.0F / 15.0F;
    private static final int RAY_COUNT = 72;
    private static final int ALPHA_THRESHOLD = 10;

    // 轮廓数据（UV空间）
    private static float[] outlineUVs;
    private static float[] outlineDu;
    private static float[] outlineDv;
    private static float outlineCenterU, outlineCenterV;
    private static float centerDu, centerDv;
    private static int outlinePointCount = 0;
    private static boolean outlineInitialized = false;
    private static boolean outlineInitFailed = false;

    private static float packNormal(float n) {
        return n * 0.5F + 0.5F;
    }

    public static void render(PoseStack poseStack,
                              net.minecraft.client.renderer.MultiBufferSource bufferSource,
                              net.minecraft.client.Camera camera,
                              float partialTicks) {
        // 阴影pass中跳过渲染，避免写入阴影贴图
        if (ShaderModCompat.isRenderingShadows()) {
            return;
        }

        if (ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get()) {
            // 始终使用自定义着色器渲染
            renderVolumetric(poseStack, bufferSource, camera, partialTicks);
        } else {
            renderVanilla(poseStack, bufferSource, camera, partialTicks);
        }
    }

    // ===== 原版透明度渲染 =====

    private static void renderVanilla(PoseStack poseStack,
                                      net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                      net.minecraft.client.Camera camera,
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

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();

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

            float size = calculateSize(particle.age);
            float alpha = calculateAlpha(particle.age);

            float dirX = (float) (originX - px);
            float dirY = (float) (originY - py);
            float dirZ = (float) (originZ - pz);
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length > 0) { dirX /= length; dirY /= length; dirZ /= length; }

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
                    float hDirX = dirX, hDirZ = dirZ;
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
            RenderSystem.setShaderTexture(0, SHIELD_TEXTURES[textureIndex]);

            // 辉光层
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] glowXyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, glowSize);
            for (int i = 0; i < 4; i++) {
                bufferBuilder.vertex(matrix, glowXyz[i * 3], glowXyz[i * 3 + 1], glowXyz[i * 3 + 2])
                             .uv(uvs[i * 2], uvs[i * 2 + 1])
                             .color(0.5F, 0.7F, 1.0F, glowAlpha)
                             .endVertex();
            }
            BufferUploader.drawWithShader(bufferBuilder.end());

            // 主层
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
        float halfSize = size / 1.0F;
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

    // ===== 体积渲染（3D棱柱体 + 宝石着色器） =====

    /**
     * 光影模式：使用保存的矩阵（Iris composite之前捕获的）渲染
     * 关键：AFTER_LEVEL时事件的PoseStack已被修改（变成投影矩阵），
     * 必须使用保存的PoseStack矩阵（摄像机旋转）来定位顶点
     */
    public static void renderWithSavedMatrices(PoseStack poseStack,
                                                net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                                net.minecraft.client.Camera camera,
                                                float partialTicks,
                                                org.joml.Matrix4f savedProjection,
                                                org.joml.Matrix4f savedModelView,
                                                org.joml.Matrix4f savedPoseStackMat) {
        if (savedProjection == null || savedModelView == null || savedPoseStackMat == null) return;
        
        // 保存当前Iris composite后的矩阵
        org.joml.Matrix4f irisProjection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        org.joml.Matrix4f irisModelView = new org.joml.Matrix4f(RenderSystem.getModelViewStack().last().pose());
        
        // 恢复composite之前的正确矩阵
        RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.last().pose().set(savedModelView);
        
        // 使用保存的PoseStack矩阵渲染（而非事件的PoseStack）
        renderVolumetricWithPoseMatrix(savedPoseStackMat, bufferSource, camera, partialTicks);
        
        // 恢复Iris的矩阵
        modelViewStack.popPose();
        RenderSystem.setProjectionMatrix(irisProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        
        // 恢复渲染状态
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
    
    /**
     * 使用指定的PoseStack矩阵渲染体积护盾（不依赖事件的PoseStack）
     */
    private static void renderVolumetricWithPoseMatrix(org.joml.Matrix4f poseStackMat,
                                                        net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                                        net.minecraft.client.Camera camera,
                                                        float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        ShaderInstance glassShader = ModShaders.getShieldGlassShader();
        if (glassShader == null) return;

        ensureOutlineInitialized();
        if (outlinePointCount < 3) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // 用保存的摄像机旋转矩阵 + translate(-cameraPos) 构建顶点变换矩阵
        Matrix4f matrix = new Matrix4f(poseStackMat);
        matrix.translate((float)-cameraPos.x, (float)-cameraPos.y, (float)-cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        if (glassShader.getUniform("Brightness") != null) {
            glassShader.getUniform("Brightness").set(BRIGHTNESS);
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(() -> glassShader);

        for (ShieldParticleData particle : manager.getParticles()) {
            int textureIndex = Math.min(particle.age - 1, SHIELD_TEXTURES.length - 1);
            if (textureIndex < 0) continue;

            Entity entity = mc.level.getEntity(particle.entityId);
            if (entity == null) continue;

            RenderSystem.setShaderTexture(0, SHIELD_TEXTURES[textureIndex]);

            ParticleGeo geo = computeGeometry(particle, entity, partialTicks);

            // 计算从粒子中心指向摄像机的方向（世界空间），用于面剔除
            float centerX = (geo.fcx + geo.bcx) * 0.5F;
            float centerY = (geo.fcy + geo.bcy) * 0.5F;
            float centerZ = (geo.fcz + geo.bcz) * 0.5F;
            float toCameraX = (float) (cameraPos.x - centerX);
            float toCameraY = (float) (cameraPos.y - centerY);
            float toCameraZ = (float) (cameraPos.z - centerZ);
            float toCameraLen = (float) Math.sqrt(toCameraX * toCameraX + toCameraY * toCameraY + toCameraZ * toCameraZ);
            if (toCameraLen > 0.001f) { toCameraX /= toCameraLen; toCameraY /= toCameraLen; toCameraZ /= toCameraLen; }

            renderPrism(bufferBuilder, matrix, geo, toCameraX, toCameraY, toCameraZ);
            BufferUploader.drawWithShader(bufferBuilder.end());
        }
    }

    private static void renderVolumetric(PoseStack poseStack,
                                         net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                         net.minecraft.client.Camera camera,
                                         float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        ShaderInstance glassShader = ModShaders.getShieldGlassShader();
        if (glassShader == null) {
            return;
        }

        ensureOutlineInitialized();
        if (outlinePointCount < 3) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        if (glassShader.getUniform("Brightness") != null) {
            glassShader.getUniform("Brightness").set(BRIGHTNESS);
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(() -> glassShader);

        for (ShieldParticleData particle : manager.getParticles()) {
            int textureIndex = Math.min(particle.age - 1, SHIELD_TEXTURES.length - 1);
            if (textureIndex < 0) continue;

            Entity entity = mc.level.getEntity(particle.entityId);
            if (entity == null) continue;

            RenderSystem.setShaderTexture(0, SHIELD_TEXTURES[textureIndex]);

            ParticleGeo geo = computeGeometry(particle, entity, partialTicks);

            // 计算从粒子中心指向摄像机的方向（世界空间），用于面剔除
            float centerX = (geo.fcx + geo.bcx) * 0.5F;
            float centerY = (geo.fcy + geo.bcy) * 0.5F;
            float centerZ = (geo.fcz + geo.bcz) * 0.5F;
            float toCameraX = (float) (cameraPos.x - centerX);
            float toCameraY = (float) (cameraPos.y - centerY);
            float toCameraZ = (float) (cameraPos.z - centerZ);
            float toCameraLen = (float) Math.sqrt(toCameraX * toCameraX + toCameraY * toCameraY + toCameraZ * toCameraZ);
            if (toCameraLen > 0.001f) { toCameraX /= toCameraLen; toCameraY /= toCameraLen; toCameraZ /= toCameraLen; }

            renderPrism(bufferBuilder, matrix, geo, toCameraX, toCameraY, toCameraZ);
            BufferUploader.drawWithShader(bufferBuilder.end());
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void ensureOutlineInitialized() {
        if (outlineInitialized || outlineInitFailed) return;

        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resourceOpt = rm.getResource(SHIELD_TEXTURES[0]);
            if (!resourceOpt.isPresent()) {
                outlineInitFailed = true;
                return;
            }

            Resource resource = resourceOpt.get();
            try (InputStream is = resource.open();
                 NativeImage image = NativeImage.read(is)) {

                int width = image.getWidth();
                int height = image.getHeight();

                float cx = 0, cy = 0;
                int count = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        int alpha = (pixel >> 24) & 0xFF;
                        if (alpha > ALPHA_THRESHOLD) {
                            cx += x;
                            cy += y;
                            count++;
                        }
                    }
                }

                if (count == 0) { outlineInitFailed = true; return; }

                cx /= count;
                cy /= count;

                float[][] rawOutline = new float[RAY_COUNT][2];
                int validRays = 0;
                double maxDist = Math.sqrt(width * width + height * height);

                for (int r = 0; r < RAY_COUNT; r++) {
                    double angle = 2.0 * Math.PI * r / RAY_COUNT;
                    double dxCos = Math.cos(angle);
                    double dySin = Math.sin(angle);

                    float lastU = (float) (cx / width);
                    float lastV = (float) (cy / height);
                    boolean foundBoundary = false;

                    for (double d = 0.5; d < maxDist; d += 0.5) {
                        int px = (int) (cx + dxCos * d);
                        int py = (int) (cy + dySin * d);

                        if (px < 0 || px >= width || py < 0 || py >= height) break;

                        int pixel = image.getPixelRGBA(px, py);
                        int alpha = (pixel >> 24) & 0xFF;
                        if (alpha > ALPHA_THRESHOLD) {
                            lastU = (float) px / width;
                            lastV = (float) py / height;
                            foundBoundary = true;
                        } else {
                            break;
                        }
                    }

                    if (foundBoundary) {
                        rawOutline[validRays][0] = lastU;
                        rawOutline[validRays][1] = lastV;
                        validRays++;
                    }
                }

                if (validRays < 3) { outlineInitFailed = true; return; }

                outlinePointCount = validRays;
                outlineUVs = new float[validRays * 2];
                outlineDu = new float[validRays];
                outlineDv = new float[validRays];

                for (int i = 0; i < validRays; i++) {
                    float u = rawOutline[i][0];
                    float v = rawOutline[i][1];
                    outlineUVs[i * 2] = u;
                    outlineUVs[i * 2 + 1] = v;
                    outlineDu[i] = 2 * u - 1;
                    outlineDv[i] = 1 - 2 * v;
                }

                outlineCenterU = (float) (cx / width);
                outlineCenterV = (float) (cy / height);
                centerDu = 2 * outlineCenterU - 1;
                centerDv = 1 - 2 * outlineCenterV;

                outlineInitialized = true;
            }
        } catch (Exception e) {
            outlineInitFailed = true;
        }
    }

    /**
     * 渲染3D棱柱粒子
     * @param viewDirX 从粒子中心指向摄像机的方向向量（世界空间，归一化）
     * @param viewDirY
     * @param viewDirZ
     */
    private static void renderPrism(BufferBuilder buf, Matrix4f matrix, ParticleGeo geo,
                                     float viewDirX, float viewDirY, float viewDirZ) {
        int n = outlinePointCount;
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);

        float alpha = geo.alpha;
        float nFx = packNormal(geo.dirX), nFy = packNormal(geo.dirY), nFz = packNormal(geo.dirZ);
        float nBx = packNormal(-geo.dirX), nBy = packNormal(-geo.dirY), nBz = packNormal(-geo.dirZ);

        // 方向向量 dot 视线方向：>0 表示正面朝向摄像机，<0 表示背面朝向摄像机
        float facingDot = geo.dirX * viewDirX + geo.dirY * viewDirY + geo.dirZ * viewDirZ;
        boolean renderFront = facingDot > 0;
        boolean renderBack = facingDot < 0;

        float fcx = geo.fcx, fcy = geo.fcy, fcz = geo.fcz;

        // 仅渲染朝向摄像机的面，避免正反面重叠导致亮度累加过曝
        if (renderFront) {
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                buf.vertex(matrix, fcx, fcy, fcz).uv(outlineCenterU, outlineCenterV).color(nFx, nFy, nFz, alpha).endVertex();

                float px0 = fcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
                float py0 = fcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
                float pz0 = fcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
                buf.vertex(matrix, px0, py0, pz0).uv(outlineUVs[i * 2], outlineUVs[i * 2 + 1]).color(nFx, nFy, nFz, alpha).endVertex();

                float px1 = fcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
                float py1 = fcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
                float pz1 = fcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
                buf.vertex(matrix, px1, py1, pz1).uv(outlineUVs[next * 2], outlineUVs[next * 2 + 1]).color(nFx, nFy, nFz, alpha).endVertex();
            }
        }

        float bcx = geo.bcx, bcy = geo.bcy, bcz = geo.bcz;

        if (renderBack) {
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                buf.vertex(matrix, bcx, bcy, bcz).uv(outlineCenterU, outlineCenterV).color(nBx, nBy, nBz, alpha).endVertex();

                float px1 = bcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
                float py1 = bcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
                float pz1 = bcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
                buf.vertex(matrix, px1, py1, pz1).uv(outlineUVs[next * 2], outlineUVs[next * 2 + 1]).color(nBx, nBy, nBz, alpha).endVertex();

                float px0 = bcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
                float py0 = bcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
                float pz0 = bcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
                buf.vertex(matrix, px0, py0, pz0).uv(outlineUVs[i * 2], outlineUVs[i * 2 + 1]).color(nBx, nBy, nBz, alpha).endVertex();
            }
        }

        // === 侧面：每条轮廓边一个四边形（两个三角形）===
        // 仅渲染朝向摄像机的侧面，避免侧面与正面/背面重叠
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;

            // 计算此侧面的法线 = cross(edgeDir, dir)
            float edgeDu = outlineDu[next] - outlineDu[i];
            float edgeDv = outlineDv[next] - outlineDv[i];
            float eX = edgeDu * geo.rightX + edgeDv * geo.upX;
            float eY = edgeDu * geo.rightY + edgeDv * geo.upY;
            float eZ = edgeDu * geo.rightZ + edgeDv * geo.upZ;

            float sX = eY * geo.dirZ - eZ * geo.dirY;
            float sY = eZ * geo.dirX - eX * geo.dirZ;
            float sZ = eX * geo.dirY - eY * geo.dirX;
            float sLen = (float) Math.sqrt(sX * sX + sY * sY + sZ * sZ);
            if (sLen > 0.0001f) { sX /= sLen; sY /= sLen; sZ /= sLen; }

            // 确保法线朝外（从棱柱中心指向侧面中点方向）
            float midDu = (outlineDu[i] + outlineDu[next]) * 0.5F;
            float midDv = (outlineDv[i] + outlineDv[next]) * 0.5F;
            float oX = midDu * geo.rightX + midDv * geo.upX;
            float oY = midDu * geo.rightY + midDv * geo.upY;
            float oZ = midDu * geo.rightZ + midDv * geo.upZ;
            if (sX * oX + sY * oY + sZ * oZ < 0) { sX = -sX; sY = -sY; sZ = -sZ; }

            // 侧面剔除：仅渲染法线朝向摄像机的侧面
            float sideFacingDot = sX * viewDirX + sY * viewDirY + sZ * viewDirZ;
            if (sideFacingDot <= 0) continue;

            float nSx = packNormal(sX), nSy = packNormal(sY), nSz = packNormal(sZ);

            // 正面顶点
            float fpx0 = fcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
            float fpy0 = fcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
            float fpz0 = fcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
            float fpx1 = fcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
            float fpy1 = fcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
            float fpz1 = fcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
            // 背面顶点
            float bpx0 = bcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
            float bpy0 = bcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
            float bpz0 = bcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
            float bpx1 = bcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
            float bpy1 = bcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
            float bpz1 = bcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;

            float u0 = outlineUVs[i * 2], v0 = outlineUVs[i * 2 + 1];
            float u1 = outlineUVs[next * 2], v1 = outlineUVs[next * 2 + 1];

            // 三角形 1: front[i], front[next], back[i]
            buf.vertex(matrix, fpx0, fpy0, fpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, fpx1, fpy1, fpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx0, bpy0, bpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
            // 三角形 2: front[next], back[next], back[i]
            buf.vertex(matrix, fpx1, fpy1, fpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx1, bpy1, bpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx0, bpy0, bpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
        }
    }

    /**
     * 粒子几何数据
     */
    private static class ParticleGeo {
        // 正面中心
        float fcx, fcy, fcz;
        // 背面中心
        float bcx, bcy, bcz;
        // 方向向量（正面法线）
        float dirX, dirY, dirZ;
        // 右向量和上向量（定义面平面）
        float rightX, rightY, rightZ;
        float upX, upY, upZ;
        // 粒子尺寸
        float size;
        // 透明度
        float alpha;
    }

    /**
     * 计算粒子的3D棱柱几何
     */
    private static ParticleGeo computeGeometry(ShieldParticleData particle, Entity entity, float partialTicks) {
        ParticleGeo geo = new ParticleGeo();

        double entityX = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
        double entityY = entity.yOld + (entity.getY() - entity.yOld) * partialTicks;
        double entityZ = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;

        double originX = entityX + particle.originOffsetX;
        double originY = entityY + particle.originOffsetY;
        double originZ = entityZ + particle.originOffsetZ;

        double px = originX + particle.offsetX;
        double py = originY + particle.offsetY;
        double pz = originZ + particle.offsetZ;

        geo.size = calculateSize(particle.age);
        geo.alpha = calculateAlpha(particle.age);

        // 粒子到球心方向（归一化，作为正面法线）
        float dirX = (float) (originX - px);
        float dirY = (float) (originY - py);
        float dirZ = (float) (originZ - pz);
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0) { dirX /= length; dirY /= length; dirZ /= length; }
        geo.dirX = dirX; geo.dirY = dirY; geo.dirZ = dirZ;

        // 计算 right 和 up 向量
        float dx = (float) (px - originX);
        float dy = (float) (py - originY);
        float dz = (float) (pz - originZ);
        float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (r > 0.0001f) {
            float theta = (float) Math.acos(Math.max(-1.0, Math.min(1.0, dy / r)));
            float phi = (float) Math.atan2(dz, dx);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            geo.rightX = -sinPhi; geo.rightY = 0; geo.rightZ = cosPhi;
            geo.upX = -cosTheta * cosPhi; geo.upY = sinTheta; geo.upZ = -cosTheta * sinPhi;

            if (sinTheta < 0.01f) {
                float hDirX = dirX, hDirZ = dirZ;
                float hLen = (float) Math.sqrt(hDirX * hDirX + hDirZ * hDirZ);
                if (hLen > 0.001f) {
                    geo.upX = hDirX / hLen; geo.upY = 0; geo.upZ = hDirZ / hLen;
                    geo.rightX = -geo.upZ; geo.rightY = 0; geo.rightZ = geo.upX;
                }
            }
        } else {
            geo.rightX = 1; geo.rightY = 0; geo.rightZ = 0;
            geo.upX = 0; geo.upY = 1; geo.upZ = 0;
        }

        // 正面中心
        geo.fcx = (float) px + dirX * THICKNESS_HALF;
        geo.fcy = (float) py + dirY * THICKNESS_HALF;
        geo.fcz = (float) pz + dirZ * THICKNESS_HALF;
        // 背面中心
        geo.bcx = (float) px - dirX * THICKNESS_HALF;
        geo.bcy = (float) py - dirY * THICKNESS_HALF;
        geo.bcz = (float) pz - dirZ * THICKNESS_HALF;

        return geo;
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