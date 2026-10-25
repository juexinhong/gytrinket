package com.gy_mod.gy_trinket.client.effect.particle;

import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.Optional;

/**
 * 「贴图转立体」的历史护盾粒子渲染器（style = 1）。
 * <p>
 * 恢复自 1.1.6 的原始实现：读粒子贴图 alpha 的轮廓，从形心出发打 72 条射线找到边界，
 * 再把这条轮廓沿法线挤出 {@link #THICKNESS_HALF} 的厚度；正反两面用贴图、侧壁用逐边法线，
 * 配合旧的 glass 着色器（顶点色里塞法线 + 菲涅尔）得到玻璃质感。
 */
public final class ShieldParticleTexturedRenderer {

    static final ResourceLocation[] SHIELD_TEXTURES = {
            new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_1.png"),
            new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_2.png"),
            new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_3.png"),
            new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_4.png"),
            new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_5.png")
    };

    /** 棱柱半厚（格） */
    private static final float THICKNESS_HALF = 0.06F;
    /** 旧着色器的亮度 uniform（历史值） */
    private static final float BRIGHTNESS = 0.53333336F;
    /** 轮廓采样射线数 */
    private static final int RAY_COUNT = 72;
    /** 贴图 alpha 阈值 */
    private static final int ALPHA_THRESHOLD = 10;

    private static float[] outlineUVs;
    private static float[] outlineDu;
    private static float[] outlineDv;
    private static float outlineCenterU;
    private static float outlineCenterV;
    private static int outlinePointCount;
    private static boolean outlineInitialized;
    private static boolean outlineInitFailed;

    private ShieldParticleTexturedRenderer() {
    }

    // ===================== 入口 =====================

    public static void render(PoseStack poseStack, Camera camera, float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = camera != null ? camera : mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = cam.getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        draw(poseStack.last().pose(), cameraPos, partialTicks);
        poseStack.popPose();
    }

    /** 供光影／保存矩阵路径使用：直接给相机坐标系下的矩阵 */
    public static void renderWithMatrix(Matrix4f matrix, Vec3 cameraPos, float partialTicks) {
        draw(matrix, cameraPos, partialTicks);
    }

    // ===================== 绘制 =====================

    private static void draw(Matrix4f matrix, Vec3 cameraPos, float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        if (manager.getParticles().isEmpty()) return;

        ShaderInstance shader = ModShaders.getShieldGlassLegacyShader();
        if (shader == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ensureOutlineInitialized();
        if (outlinePointCount < 3) return;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        if (shader.getUniform("Brightness") != null) {
            shader.getUniform("Brightness").set(BRIGHTNESS);
        }
        RenderSystem.setShader(() -> shader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();

        for (ShieldParticleData particle : manager.getParticles()) {
            int textureIndex = Math.min(particle.age - 1, SHIELD_TEXTURES.length - 1);
            if (textureIndex < 0) continue;

            Entity entity = mc.level.getEntity(particle.entityId);
            if (entity == null) continue;

            RenderSystem.setShaderTexture(0, SHIELD_TEXTURES[textureIndex]);

            ParticleGeo geo = computeGeometry(particle, entity, partialTicks);
            float centerX = (geo.fcx + geo.bcx) * 0.5F;
            float centerY = (geo.fcy + geo.bcy) * 0.5F;
            float centerZ = (geo.fcz + geo.bcz) * 0.5F;
            float toCameraX = (float) (cameraPos.x - centerX);
            float toCameraY = (float) (cameraPos.y - centerY);
            float toCameraZ = (float) (cameraPos.z - centerZ);
            float toCameraLen = (float) Math.sqrt(toCameraX * toCameraX + toCameraY * toCameraY + toCameraZ * toCameraZ);
            if (toCameraLen > 0.001F) {
                toCameraX /= toCameraLen;
                toCameraY /= toCameraLen;
                toCameraZ /= toCameraLen;
            }

            renderPrism(bufferBuilder, matrix, geo, toCameraX, toCameraY, toCameraZ);
            BufferUploader.drawWithShader(bufferBuilder.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 贴图 alpha 轮廓：形心 + 72 条射线打到边界 */
    private static void ensureOutlineInitialized() {
        if (outlineInitialized || outlineInitFailed) return;

        InputStream is = null;
        NativeImage image = null;
        try {
            Optional<Resource> resourceOpt = Minecraft.getInstance().getResourceManager().getResource(SHIELD_TEXTURES[0]);
            if (resourceOpt.isEmpty()) {
                outlineInitFailed = true;
                return;
            }
            Resource resource = resourceOpt.get();
            is = resource.open();
            image = NativeImage.read(is);

            int width = image.getWidth();
            int height = image.getHeight();

            float cx = 0;
            float cy = 0;
            int count = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = image.getPixelRGBA(x, y) >>> 24;
                    if (alpha <= ALPHA_THRESHOLD) continue;
                    cx += x;
                    cy += y;
                    count++;
                }
            }
            if (count == 0) {
                outlineInitFailed = true;
                return;
            }
            cx /= count;
            cy /= count;

            float[][] rawOutline = new float[RAY_COUNT][2];
            int validRays = 0;
            float maxDist = (float) Math.sqrt((float) width * width + (float) height * height);

            for (int r = 0; r < RAY_COUNT; r++) {
                double angle = Math.PI * 2.0 * r / RAY_COUNT;
                float dxCos = (float) Math.cos(angle);
                float dySin = (float) Math.sin(angle);
                float lastU = cx / width;
                float lastV = cy / height;
                boolean foundBoundary = false;

                for (float d = 0.5F; d < maxDist; d += 0.5F) {
                    int px = (int) (cx + dxCos * d);
                    int py = (int) (cy + dySin * d);
                    if (px < 0 || px >= width || py < 0 || py >= height) break;
                    int alpha = image.getPixelRGBA(px, py) >>> 24;
                    if (alpha <= ALPHA_THRESHOLD) break;
                    lastU = (float) px / width;
                    lastV = (float) py / height;
                    foundBoundary = true;
                }
                if (foundBoundary) {
                    rawOutline[validRays][0] = lastU;
                    rawOutline[validRays][1] = lastV;
                    validRays++;
                }
            }

            if (validRays < 3) {
                outlineInitFailed = true;
                return;
            }

            outlinePointCount = validRays;
            outlineUVs = new float[validRays * 2];
            outlineDu = new float[validRays];
            outlineDv = new float[validRays];
            for (int i = 0; i < validRays; i++) {
                float u = rawOutline[i][0];
                float v = rawOutline[i][1];
                outlineUVs[i * 2] = u;
                outlineUVs[i * 2 + 1] = v;
                outlineDu[i] = 2.0F * u - 1.0F;
                outlineDv[i] = 1.0F - 2.0F * v;
            }
            outlineCenterU = cx / width;
            outlineCenterV = cy / height;
            outlineInitialized = true;
        } catch (Exception e) {
            outlineInitFailed = true;
        } finally {
            try {
                if (image != null) image.close();
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ===================== 几何 =====================

    /** 正反两面 + 侧面：正面是扇形，侧面每条边一个四边形 */
    private static void renderPrism(BufferBuilder buf, Matrix4f matrix, ParticleGeo geo,
                                    float viewDirX, float viewDirY, float viewDirZ) {
        int n = outlinePointCount;
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);

        float alpha = geo.alpha;
        float nFx = packNormal(geo.dirX);
        float nFy = packNormal(geo.dirY);
        float nFz = packNormal(geo.dirZ);
        float nBx = packNormal(-geo.dirX);
        float nBy = packNormal(-geo.dirY);
        float nBz = packNormal(-geo.dirZ);

        float facingDot = geo.dirX * viewDirX + geo.dirY * viewDirY + geo.dirZ * viewDirZ;
        boolean renderFront = facingDot > 0.0F;
        boolean renderBack = facingDot < 0.0F;

        float fcx = geo.fcx;
        float fcy = geo.fcy;
        float fcz = geo.fcz;
        float bcx = geo.bcx;
        float bcy = geo.bcy;
        float bcz = geo.bcz;

        if (renderFront) {
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                buf.vertex(matrix, fcx, fcy, fcz)
                        .uv(outlineCenterU, outlineCenterV)
                        .color(nFx, nFy, nFz, alpha)
                        .endVertex();
                float px0 = fcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
                float py0 = fcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
                float pz0 = fcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
                buf.vertex(matrix, px0, py0, pz0)
                        .uv(outlineUVs[i * 2], outlineUVs[i * 2 + 1])
                        .color(nFx, nFy, nFz, alpha)
                        .endVertex();
                float px1 = fcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
                float py1 = fcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
                float pz1 = fcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
                buf.vertex(matrix, px1, py1, pz1)
                        .uv(outlineUVs[next * 2], outlineUVs[next * 2 + 1])
                        .color(nFx, nFy, nFz, alpha)
                        .endVertex();
            }
        }

        if (renderBack) {
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                buf.vertex(matrix, bcx, bcy, bcz)
                        .uv(outlineCenterU, outlineCenterV)
                        .color(nBx, nBy, nBz, alpha)
                        .endVertex();
                float px1 = bcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
                float py1 = bcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
                float pz1 = bcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
                buf.vertex(matrix, px1, py1, pz1)
                        .uv(outlineUVs[next * 2], outlineUVs[next * 2 + 1])
                        .color(nBx, nBy, nBz, alpha)
                        .endVertex();
                float px0 = bcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
                float py0 = bcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
                float pz0 = bcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
                buf.vertex(matrix, px0, py0, pz0)
                        .uv(outlineUVs[i * 2], outlineUVs[i * 2 + 1])
                        .color(nBx, nBy, nBz, alpha)
                        .endVertex();
            }
        }

        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            float edgeDu = outlineDu[next] - outlineDu[i];
            float edgeDv = outlineDv[next] - outlineDv[i];
            float eX = edgeDu * geo.rightX + edgeDv * geo.upX;
            float eY = edgeDu * geo.rightY + edgeDv * geo.upY;
            float eZ = edgeDu * geo.rightZ + edgeDv * geo.upZ;
            float sX = eY * geo.dirZ - eZ * geo.dirY;
            float sY = eZ * geo.dirX - eX * geo.dirZ;
            float sZ = eX * geo.dirY - eY * geo.dirX;
            float sLen = (float) Math.sqrt(sX * sX + sY * sY + sZ * sZ);
            if (sLen > 1.0E-4F) {
                sX /= sLen;
                sY /= sLen;
                sZ /= sLen;
            }
            float midDu = (outlineDu[i] + outlineDu[next]) * 0.5F;
            float midDv = (outlineDv[i] + outlineDv[next]) * 0.5F;
            float oX = midDu * geo.rightX + midDv * geo.upX;
            float oY = midDu * geo.rightY + midDv * geo.upY;
            float oZ = midDu * geo.rightZ + midDv * geo.upZ;
            if (sX * oX + sY * oY + sZ * oZ < 0.0F) {
                sX = -sX;
                sY = -sY;
                sZ = -sZ;
            }
            if (sX * viewDirX + sY * viewDirY + sZ * viewDirZ <= 0.0F) continue;

            float nSx = packNormal(sX);
            float nSy = packNormal(sY);
            float nSz = packNormal(sZ);
            float u0 = outlineUVs[i * 2];
            float v0 = outlineUVs[i * 2 + 1];
            float u1 = outlineUVs[next * 2];
            float v1 = outlineUVs[next * 2 + 1];

            float fpx0 = fcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
            float fpy0 = fcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
            float fpz0 = fcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
            float fpx1 = fcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
            float fpy1 = fcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
            float fpz1 = fcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;
            float bpx0 = bcx + outlineDu[i] * geo.size * geo.rightX + outlineDv[i] * geo.size * geo.upX;
            float bpy0 = bcy + outlineDu[i] * geo.size * geo.rightY + outlineDv[i] * geo.size * geo.upY;
            float bpz0 = bcz + outlineDu[i] * geo.size * geo.rightZ + outlineDv[i] * geo.size * geo.upZ;
            float bpx1 = bcx + outlineDu[next] * geo.size * geo.rightX + outlineDv[next] * geo.size * geo.upX;
            float bpy1 = bcy + outlineDu[next] * geo.size * geo.rightY + outlineDv[next] * geo.size * geo.upY;
            float bpz1 = bcz + outlineDu[next] * geo.size * geo.rightZ + outlineDv[next] * geo.size * geo.upZ;

            buf.vertex(matrix, fpx0, fpy0, fpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, fpx1, fpy1, fpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx0, bpy0, bpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, fpx1, fpy1, fpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx1, bpy1, bpz1).uv(u1, v1).color(nSx, nSy, nSz, alpha).endVertex();
            buf.vertex(matrix, bpx0, bpy0, bpz0).uv(u0, v0).color(nSx, nSy, nSz, alpha).endVertex();
        }
    }

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
        // 出现/消散/让位的透明度与另外两套形象共用同一份包络：
        // 旧版的 calculateAlpha 在 age≈13 就归零，且不响应 yielding，
        // 连续受击时会让位淡出变成硬切。
        geo.alpha = ShieldParticleRenderer.particleAlpha(particle, partialTicks);

        float dirX = (float) (originX - px);
        float dirY = (float) (originY - py);
        float dirZ = (float) (originZ - pz);
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len > 0.0F) {
            dirX /= len;
            dirY /= len;
            dirZ /= len;
        }
        geo.dirX = dirX;
        geo.dirY = dirY;
        geo.dirZ = dirZ;

        float dx = (float) (px - originX);
        float dy = (float) (py - originY);
        float dz = (float) (pz - originZ);
        float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (r > 1.0E-4F) {
            float theta = (float) Math.acos(Math.max(-1.0, Math.min(1.0, dy / r)));
            float phi = (float) Math.atan2(dz, dx);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            geo.rightX = -sinPhi;
            geo.rightY = 0.0F;
            geo.rightZ = cosPhi;
            geo.upX = -cosTheta * cosPhi;
            geo.upY = sinTheta;
            geo.upZ = -cosTheta * sinPhi;

            if (sinTheta < 0.01F) {
                float hDirX = dirX;
                float hDirZ = dirZ;
                float hLen = (float) Math.sqrt(hDirX * hDirX + hDirZ * hDirZ);
                if (hLen > 0.001F) {
                    geo.upX = hDirX / hLen;
                    geo.upY = 0.0F;
                    geo.upZ = hDirZ / hLen;
                    geo.rightX = -geo.upZ;
                    geo.rightY = 0.0F;
                    geo.rightZ = geo.upX;
                }
            }
        } else {
            geo.rightX = 1.0F;
            geo.rightY = 0.0F;
            geo.rightZ = 0.0F;
            geo.upX = 0.0F;
            geo.upY = 1.0F;
            geo.upZ = 0.0F;
        }

        geo.fcx = (float) px + dirX * THICKNESS_HALF;
        geo.fcy = (float) py + dirY * THICKNESS_HALF;
        geo.fcz = (float) pz + dirZ * THICKNESS_HALF;
        geo.bcx = (float) px - dirX * THICKNESS_HALF;
        geo.bcy = (float) py - dirY * THICKNESS_HALF;
        geo.bcz = (float) pz - dirZ * THICKNESS_HALF;
        return geo;
    }

    /** 历史透明度：只按年龄线性衰减，没有出现/让位动画 */
    private static float calculateAlpha(int age) {
        float alpha = 1.0F;
        if (age > 1) {
            alpha = 1.0F - (float) age * 0.08F;
        }
        return Math.max(0.0F, alpha);
    }

    private static float calculateSize(int age) {
        if (age <= 1) return 0.034F;
        if (age <= 2) return 0.065F;
        if (age <= 3) return 0.096F;
        if (age <= 4) return 0.128F;
        return 0.16F;
    }

    private static float packNormal(float n) {
        return n * 0.5F + 0.5F;
    }

    private static final class ParticleGeo {
        float fcx, fcy, fcz;
        float bcx, bcy, bcz;
        float dirX, dirY, dirZ;
        float rightX, rightY, rightZ;
        float upX, upY, upZ;
        float size;
        float alpha;
    }
}
