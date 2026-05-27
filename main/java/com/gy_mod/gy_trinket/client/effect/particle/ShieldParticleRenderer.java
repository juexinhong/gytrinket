package com.gy_mod.gy_trinket.client.effect.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class ShieldParticleRenderer {
    
    private static final ResourceLocation[] SHIELD_TEXTURES = {
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_1.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_2.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_3.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_4.png"),
        new ResourceLocation("gytrinket", "textures/particle/shield_particle_1_5.png")
    };
    
    public static void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, net.minecraft.client.Camera camera, float partialTicks) {
        ShieldParticleRenderManager manager = ShieldParticleRenderManager.getInstance();
        
        if (manager.getParticles().isEmpty()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
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
            
            ResourceLocation texture = SHIELD_TEXTURES[textureIndex];
            
            double px = particle.x;
            double py = particle.y;
            double pz = particle.z;
            
            float size = calculateSize(particle.age);
            float alpha = calculateAlpha(particle.age);
            
            double originX = particle.originX;
            double originY = particle.originY;
            double originZ = particle.originZ;
            
            float dirX = (float)(originX - px);
            float dirY = (float)(originY - py);
            float dirZ = (float)(originZ - pz);
            
            float length = (float)Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length > 0) {
                dirX /= length;
                dirY /= length;
                dirZ /= length;
            }
            
            // 以原点为球心，用球坐标(θ,φ)计算粒子的经线方向(up)和纬线方向(right)
            // 粒子位置相对原点的向量
            float dx = (float)(px - originX);
            float dy = (float)(py - originY);
            float dz = (float)(pz - originZ);
            float r = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            float rightX, rightY, rightZ, upX, upY, upZ;
            
            if (r > 0.0001f) {
                // θ: 从+Y轴的极角 (0=北极, π=南极)
                float theta = (float)Math.acos(Math.max(-1.0, Math.min(1.0, dy / r)));
                // φ: XZ平面上的方位角
                float phi = (float)Math.atan2(dz, dx);
                
                float sinTheta = (float)Math.sin(theta);
                float cosTheta = (float)Math.cos(theta);
                float sinPhi = (float)Math.sin(phi);
                float cosPhi = (float)Math.cos(phi);
                
                // 纬线方向(right): 沿纬度圈向东，始终水平
                rightX = -sinPhi;
                rightY = 0;
                rightZ = cosPhi;
                
                // 经线方向(up): 沿经度圈向北极，球面切线方向
                upX = -cosTheta * cosPhi;
                upY = sinTheta;
                upZ = -cosTheta * sinPhi;
                
                // 极点附近(sinTheta≈0): up退化为水平向量，使用dir投影到水平面作为up的回退
                if (sinTheta < 0.01f) {
                    // 投影dir到水平面
                    float hDirX = dirX;
                    float hDirZ = dirZ;
                    float hLen = (float)Math.sqrt(hDirX * hDirX + hDirZ * hDirZ);
                    if (hLen > 0.001f) {
                        upX = hDirX / hLen;
                        upY = 0;
                        upZ = hDirZ / hLen;
                        rightX = -upZ;
                        rightY = 0;
                        rightZ = upX;
                    }
                }
            } else {
                rightX = 1; rightY = 0; rightZ = 0;
                upX = 0; upY = 1; upZ = 0;
            }
            
            float[] uvs = {0, 1, 0, 0, 1, 0, 1, 1};
            org.joml.Matrix4f matrix = poseStack.last().pose();
            
            float glowSize = size * 1.1F;
            float glowAlpha = alpha * 0.5F;
            
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, texture);
            
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            
            float[] glowXyz = calculateVertices(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, glowSize);
            
            for (int i = 0; i < 4; i++) {
                bufferBuilder.vertex(matrix, glowXyz[i * 3], glowXyz[i * 3 + 1], glowXyz[i * 3 + 2])
                             .uv(uvs[i * 2], uvs[i * 2 + 1])
                             .color(0.5F, 0.7F, 1.0F, glowAlpha)
                             .endVertex();
            }
            
            BufferUploader.drawWithShader(bufferBuilder.end());
            
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
    }
    
    private static float calculateAlpha(int age) {
        float alpha = 1.0F;
        if (age > 1) {
            alpha = 1.0F - (float)(age)  * 0.08F;
        }
        return Math.max(0.0F, alpha);
    }
    
    private static float[] calculateVertices(double px, double py, double pz,
                                             float rightX, float rightY, float rightZ,
                                             float upX, float upY, float upZ,
                                             float size) {
        float halfSize = size / 1.0F;
        float[] xyz = new float[12];

        xyz[0] = (float)(px + (-halfSize * rightX - halfSize * upX));
        xyz[1] = (float)(py + (-halfSize * rightY - halfSize * upY));
        xyz[2] = (float)(pz + (-halfSize * rightZ - halfSize * upZ));

        xyz[3] = (float)(px + (-halfSize * rightX + halfSize * upX));
        xyz[4] = (float)(py + (-halfSize * rightY + halfSize * upY));
        xyz[5] = (float)(pz + (-halfSize * rightZ + halfSize * upZ));

        xyz[6] = (float)(px + (halfSize * rightX + halfSize * upX));
        xyz[7] = (float)(py + (halfSize * rightY + halfSize * upY));
        xyz[8] = (float)(pz + (halfSize * rightZ + halfSize * upZ));

        xyz[9] = (float)(px + (halfSize * rightX - halfSize * upX));
        xyz[10] = (float)(py + (halfSize * rightY - halfSize * upY));
        xyz[11] = (float)(pz + (halfSize * rightZ - halfSize * upZ));

        return xyz;
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