package com.gy_mod.gy_trinket.client.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.mixin.ParticleEngineAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 充能横扫自定义渲染器
 * <p>
 * 使用原版横扫粒子材质（粒子图集）渲染自定义的旋转、缩放横扫效果。
 * 粒子方向完全由玩家视线方向决定，不面向相机。
 * 粒子为垂直平面，垂直于玩家的水平视线方向。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChargedSweepRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("GyTrinket-Debug");

    private static final List<ChargedSweepRenderData> ACTIVE_SWEEPS = new ArrayList<>();

    private ChargedSweepRenderer() {}

    public static void addSweep(ChargedSweepRenderData data) {
        LOGGER.info("addSweep: pos=({},{},{}) scale={} yaw={}", data.x(), data.y(), data.z(), data.scale(), data.yaw());
        ACTIVE_SWEEPS.add(data);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (ACTIVE_SWEEPS.isEmpty()) {
            return;
        }

        LOGGER.info("rendering {} sweeps", ACTIVE_SWEEPS.size());

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // 获取横扫粒子的 SpriteSet
        SpriteSet sweepSprites = getSweepSpriteSet(mc);
        if (sweepSprites == null) {
            ACTIVE_SWEEPS.clear();
            return;
        }

        long currentTick = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();

        // 设置渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();

        Iterator<ChargedSweepRenderData> it = ACTIVE_SWEEPS.iterator();
        while (it.hasNext()) {
            ChargedSweepRenderData data = it.next();
            int age = (int) (currentTick - data.creationTime());
            if (age >= data.lifetime()) {
                it.remove();
                continue;
            }

            // 获取当前帧的精灵（确保 age 在有效范围内）
            int spriteAge = Math.max(0, Math.min(age, data.lifetime() - 1));
            TextureAtlasSprite sprite = sweepSprites.get(spriteAge, data.lifetime());
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            // 计算位置（相对于相机）
            float px = (float) (data.x() - camPos.x);
            float py = (float) (data.y() - camPos.y);
            float pz = (float) (data.z() - camPos.z);

            // 计算透明度（随年龄衰减）
            float ageRatio = (age + partialTick) / data.lifetime();
            float alpha = 1.0F - ageRatio;

            // 基于玩家完整视线方向（yaw + pitch）构建平面
            float yaw = data.yaw();
            float pitch = data.pitch();

            // right 向量：垂直于视线方向的水平向量
            float rightX = Mth.cos(yaw);
            float rightY = 0;
            float rightZ = Mth.sin(yaw);

            // forward 向量：沿玩家完整视线方向（包含俯仰角）
            float cosPitch = Mth.cos(pitch);
            float forwardX = -Mth.sin(yaw) * cosPitch;
            float forwardY = -Mth.sin(pitch);
            float forwardZ = Mth.cos(yaw) * cosPitch;

            // 缩放后的半尺寸
            float halfW = data.scale() * 0.5F;
            float halfH = data.scale() * 0.5F;

            // 构建四个顶点
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            // 左后
            bufferBuilder.vertex(matrix, px + (-halfW * rightX - halfH * forwardX),
                    py + (-halfW * rightY - halfH * forwardY),
                    pz + (-halfW * rightZ - halfH * forwardZ))
                    .uv(u0, v1).color(1.0F, 1.0F, 1.0F, alpha).endVertex();

            // 右后
            bufferBuilder.vertex(matrix, px + (halfW * rightX - halfH * forwardX),
                    py + (halfW * rightY - halfH * forwardY),
                    pz + (halfW * rightZ - halfH * forwardZ))
                    .uv(u1, v1).color(1.0F, 1.0F, 1.0F, alpha).endVertex();

            // 右前
            bufferBuilder.vertex(matrix, px + (halfW * rightX + halfH * forwardX),
                    py + (halfW * rightY + halfH * forwardY),
                    pz + (halfW * rightZ + halfH * forwardZ))
                    .uv(u1, v0).color(1.0F, 1.0F, 1.0F, alpha).endVertex();

            // 左前
            bufferBuilder.vertex(matrix, px + (-halfW * rightX + halfH * forwardX),
                    py + (-halfW * rightY + halfH * forwardY),
                    pz + (-halfW * rightZ + halfH * forwardZ))
                    .uv(u0, v0).color(1.0F, 1.0F, 1.0F, alpha).endVertex();

            BufferUploader.drawWithShader(bufferBuilder.end());
        }

        poseStack.popPose();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static SpriteSet getSweepSpriteSet(Minecraft mc) {
        try {
            Map<ResourceLocation, SpriteSet> spriteSets = ((ParticleEngineAccessor) mc.particleEngine).gytrinket$getSpriteSets();
            ResourceLocation sweepId = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getKey(ParticleTypes.SWEEP_ATTACK);
            return spriteSets.get(sweepId);
        } catch (Exception e) {
            return null;
        }
    }
}
