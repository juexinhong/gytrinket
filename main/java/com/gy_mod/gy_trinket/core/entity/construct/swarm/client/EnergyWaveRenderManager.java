package com.gy_mod.gy_trinket.core.entity.construct.swarm.client;

import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 蜂群能量波渲染管理器。
 * 使用材质渲染双平面（水平+垂直交叉）能量波。
 * 参考SiphonRenderer的渲染方式，确保深度和位置正确。
 */
public class EnergyWaveRenderManager {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        com.gy_mod.gy_trinket.gytrinket.MODID, "textures/entity/swarm_attack.png"
    );

    private static final int DURATION_TICKS = 8;
    private static final float SIZE_SCALE = 0.25f;
    private static final float FORWARD_DRIFT = 0.3f;
    private static final float END_SCALE = 2.0f;

    private static final List<EnergyWaveData> waves = new ArrayList<>();

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

        // 第一遍：渲染材质
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        poseStack.pushPose();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (EnergyWaveData wave : waves) {
            float progress = wave.getProgress(currentTime);
            renderWaveTextured(matrix, buffer, wave, progress, partialTick, camPos);
        }

        BufferUploader.drawWithShader(buffer.end());

        // 第二遍：白色覆盖层（从纯白覆盖逐渐取消），使用材质alpha过滤透明区域
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (EnergyWaveData wave : waves) {
            float progress = wave.getProgress(currentTime);
            renderWaveWhiteOverlay(matrix, buffer, wave, progress, partialTick, camPos);
        }

        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** 获取蜂群的渲染位置（世界坐标，partialTick插值）和朝向 */
    private static WaveTransform resolveTransform(EnergyWaveData wave, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && wave.entityId >= 0) {
            Entity entity = mc.level.getEntity(wave.entityId);
            if (entity instanceof SwarmConstructEntity swarm && swarm.isAlive()) {
                Vec3 dir = swarm.getLookAngle().normalize();
                double x = Mth.lerp(partialTick, swarm.xOld, swarm.getX()) + dir.x * 0.6;
                double y = Mth.lerp(partialTick, swarm.yOld, swarm.getY()) + swarm.getBbHeight() * 0.3 + dir.y * 0.2;
                double z = Mth.lerp(partialTick, swarm.zOld, swarm.getZ()) + dir.z * 0.6;
                return new WaveTransform(new Vec3(x, y, z), dir);
            }
        }
        return new WaveTransform(
            new Vec3(wave.x, wave.y, wave.z),
            new Vec3(wave.dirX, wave.dirY, wave.dirZ).normalize()
        );
    }

    /** 第一遍：渲染材质本体 */
    private static void renderWaveTextured(Matrix4f matrix, BufferBuilder buffer, EnergyWaveData wave, float progress, float partialTick, Vec3 camPos) {
        float alpha = 1.0f - progress;
        float scale = SIZE_SCALE * (1.0f + (END_SCALE - 1.0f) * progress);
        float forwardOffset = FORWARD_DRIFT * SIZE_SCALE * progress;

        WaveTransform t = resolveTransform(wave, partialTick);
        Vec3 center = t.position.add(t.direction.scale(forwardOffset)).subtract(camPos);
        Vec3 forward = t.direction;

        Vec3 up = findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        float halfSize = scale;

        // 水平平面的四个角（沿right和forward展开）
        Vec3 hbl = center.add(right.scale(-halfSize)).add(forward.scale(-halfSize));
        Vec3 hbr = center.add(right.scale(halfSize)).add(forward.scale(-halfSize));
        Vec3 htl = center.add(right.scale(-halfSize)).add(forward.scale(halfSize));
        Vec3 htr = center.add(right.scale(halfSize)).add(forward.scale(halfSize));

        // 水平平面（双面）
        buffer.vertex(matrix, (float)hbl.x, (float)hbl.y, (float)hbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)hbr.x, (float)hbr.y, (float)hbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)htr.x, (float)htr.y, (float)htr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)htl.x, (float)htl.y, (float)htl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();

        buffer.vertex(matrix, (float)hbl.x, (float)hbl.y, (float)hbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)htl.x, (float)htl.y, (float)htl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)htr.x, (float)htr.y, (float)htr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)hbr.x, (float)hbr.y, (float)hbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();

        // 垂直平面的四个角（沿up和forward展开）
        Vec3 vbl = center.add(up.scale(-halfSize)).add(forward.scale(-halfSize));
        Vec3 vbr = center.add(up.scale(halfSize)).add(forward.scale(-halfSize));
        Vec3 vtl = center.add(up.scale(-halfSize)).add(forward.scale(halfSize));
        Vec3 vtr = center.add(up.scale(halfSize)).add(forward.scale(halfSize));

        // 垂直平面（双面）
        buffer.vertex(matrix, (float)vbl.x, (float)vbl.y, (float)vbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vbr.x, (float)vbr.y, (float)vbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vtr.x, (float)vtr.y, (float)vtr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vtl.x, (float)vtl.y, (float)vtl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();

        buffer.vertex(matrix, (float)vbl.x, (float)vbl.y, (float)vbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vtl.x, (float)vtl.y, (float)vtl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vtr.x, (float)vtr.y, (float)vtr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        buffer.vertex(matrix, (float)vbr.x, (float)vbr.y, (float)vbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
    }

    /** 第二遍：白色覆盖层，从纯白覆盖逐渐取消，使用材质alpha过滤透明区域 */
    private static void renderWaveWhiteOverlay(Matrix4f matrix, BufferBuilder buffer, EnergyWaveData wave, float progress, float partialTick, Vec3 camPos) {
        float overlayAlpha = (1.0f - progress) * (1.0f - progress);
        float scale = SIZE_SCALE * (1.0f + (END_SCALE - 1.0f) * progress);
        float forwardOffset = FORWARD_DRIFT * SIZE_SCALE * progress;

        WaveTransform t = resolveTransform(wave, partialTick);
        Vec3 center = t.position.add(t.direction.scale(forwardOffset)).subtract(camPos);
        Vec3 forward = t.direction;

        Vec3 up = findUp(forward);
        Vec3 right = forward.cross(up).normalize();
        up = right.cross(forward).normalize();

        float halfSize = scale;

        // 水平平面（双面）
        Vec3 hbl = center.add(right.scale(-halfSize)).add(forward.scale(-halfSize));
        Vec3 hbr = center.add(right.scale(halfSize)).add(forward.scale(-halfSize));
        Vec3 htl = center.add(right.scale(-halfSize)).add(forward.scale(halfSize));
        Vec3 htr = center.add(right.scale(halfSize)).add(forward.scale(halfSize));

        buffer.vertex(matrix, (float)hbl.x, (float)hbl.y, (float)hbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)hbr.x, (float)hbr.y, (float)hbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)htr.x, (float)htr.y, (float)htr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)htl.x, (float)htl.y, (float)htl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();

        buffer.vertex(matrix, (float)hbl.x, (float)hbl.y, (float)hbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)htl.x, (float)htl.y, (float)htl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)htr.x, (float)htr.y, (float)htr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)hbr.x, (float)hbr.y, (float)hbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();

        // 垂直平面（双面）
        Vec3 vbl = center.add(up.scale(-halfSize)).add(forward.scale(-halfSize));
        Vec3 vbr = center.add(up.scale(halfSize)).add(forward.scale(-halfSize));
        Vec3 vtl = center.add(up.scale(-halfSize)).add(forward.scale(halfSize));
        Vec3 vtr = center.add(up.scale(halfSize)).add(forward.scale(halfSize));

        buffer.vertex(matrix, (float)vbl.x, (float)vbl.y, (float)vbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vbr.x, (float)vbr.y, (float)vbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vtr.x, (float)vtr.y, (float)vtr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vtl.x, (float)vtl.y, (float)vtl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();

        buffer.vertex(matrix, (float)vbl.x, (float)vbl.y, (float)vbl.z).uv(0, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vtl.x, (float)vtl.y, (float)vtl.z).uv(0, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vtr.x, (float)vtr.y, (float)vtr.z).uv(1, 0).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
        buffer.vertex(matrix, (float)vbr.x, (float)vbr.y, (float)vbr.z).uv(1, 1).color(1.0f, 1.0f, 1.0f, overlayAlpha).endVertex();
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
            return currentTime - startTime >= DURATION_TICKS;
        }

        float getProgress(long currentTime) {
            return Math.min((float) (currentTime - startTime) / DURATION_TICKS, 1.0f);
        }
    }
}
