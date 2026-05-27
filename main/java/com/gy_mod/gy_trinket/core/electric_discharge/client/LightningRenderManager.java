package com.gy_mod.gy_trinket.core.electric_discharge.client;

import com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager;
import com.gy_mod.gy_trinket.core.electric_discharge.LightningRenderData;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

public class LightningRenderManager {
    private static final List<LightningRenderData> lightningDataList = new ArrayList<>();

    public static void addLightning(List<ElectricDischargeManager.LightningSegment> segments) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        
        double totalLength = 0;
        for (ElectricDischargeManager.LightningSegment segment : segments) {
            totalLength += segment.start().distanceTo(segment.end());
        }
        
        lightningDataList.add(new LightningRenderData(segments, currentTime, 8, totalLength));
    }

    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        
        lightningDataList.removeIf(data -> data.isExpired(currentTime));

        if (lightningDataList.isEmpty()) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (LightningRenderData data : lightningDataList) {
            float progress = data.getProgress(currentTime);
            float alpha = calculateAlpha(progress);
            float[] colors = getLightningColor(progress);

            List<ElectricDischargeManager.LightningSegment> segments = data.getSegments();
            
            Vec3 origin = null;
            if (!segments.isEmpty()) {
                origin = segments.get(0).start();
            }

            double defaultLength = 6.0;
            double scaleRatio = Math.max(0.3, 1 + (data.getTotalLength() / defaultLength - 1) * 0.25);

            Vec3 lastPerpendicular = null;

            for (ElectricDischargeManager.LightningSegment segment : segments) {
                Vec3 start = segment.start();
                Vec3 end = segment.end();

                double distanceFromOrigin = origin != null ? origin.distanceTo(start) : 0;
                double segmentLength = start.distanceTo(end);

                Vec3 direction = end.subtract(start).normalize();
                
                Vec3 perpendicular = calculatePerpendicular(direction, lastPerpendicular);
                
                if (lastPerpendicular == null) {
                    lastPerpendicular = perpendicular;
                }

                float maxWidth = 0.08f * (float) scaleRatio;
                float minWidth = 0.01f * (float) scaleRatio;
                
                double totalDistance = data.getTotalLength();
                float startProgress = Math.min(1.0f, (float) (distanceFromOrigin / totalDistance));
                float endProgress = Math.min(1.0f, (float) ((distanceFromOrigin + segmentLength) / totalDistance));
                
                float startWidth = minWidth + (maxWidth - minWidth) * (1.0f - (float) Math.sqrt(startProgress));
                float endWidth = minWidth + (maxWidth - minWidth) * (1.0f - (float) Math.sqrt(endProgress));

                float startAlpha = alpha * (1.0f - startProgress * 0.3f);
                float endAlpha = alpha * (1.0f - endProgress * 0.3f);

                Vec3 startLeft = start.add(lastPerpendicular.scale(startWidth));
                Vec3 startRight = start.subtract(lastPerpendicular.scale(startWidth));
                Vec3 endLeft = end.add(perpendicular.scale(endWidth));
                Vec3 endRight = end.subtract(perpendicular.scale(endWidth));

                bufferBuilder.vertex(poseStack.last().pose(), (float) startLeft.x, (float) startLeft.y, (float) startLeft.z).color(colors[0], colors[1], colors[2], startAlpha).endVertex();
                bufferBuilder.vertex(poseStack.last().pose(), (float) startRight.x, (float) startRight.y, (float) startRight.z).color(colors[0], colors[1], colors[2], startAlpha).endVertex();
                bufferBuilder.vertex(poseStack.last().pose(), (float) endRight.x, (float) endRight.y, (float) endRight.z).color(colors[0], colors[1], colors[2], endAlpha).endVertex();
                bufferBuilder.vertex(poseStack.last().pose(), (float) endLeft.x, (float) endLeft.y, (float) endLeft.z).color(colors[0], colors[1], colors[2], endAlpha).endVertex();

                lastPerpendicular = perpendicular;
            }
        }

        poseStack.popPose();

        BufferUploader.drawWithShader(bufferBuilder.end());

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static Vec3 calculatePerpendicular(Vec3 direction, Vec3 previousPerpendicular) {
        Vec3 cross = new Vec3(-direction.z, 0, direction.x);
        double crossLength = cross.length();
        
        Vec3 newPerpendicular;
        if (crossLength > 0.001) {
            newPerpendicular = cross.normalize();
        } else {
            newPerpendicular = new Vec3(1, 0, 0);
        }

        if (previousPerpendicular != null) {
            double dot = previousPerpendicular.dot(newPerpendicular);
            if (dot < 0) {
                newPerpendicular = newPerpendicular.scale(-1);
            }
        }

        return newPerpendicular;
    }

    private static float[] getLightningColor(float progress) {
        float t = progress * 2.0f;
        
        if (t <= 1.0f) {
            float whiteToBlue = t;
            return new float[]{
                    1.0f - whiteToBlue * 0.3f,
                    1.0f - whiteToBlue * 0.1f,
                    0.8f + whiteToBlue * 0.2f
            };
        } else {
            float blueFade = (t - 1.0f);
            return new float[]{
                    Mth.lerp(blueFade, 0.7f, 0.2f),
                    Mth.lerp(blueFade, 0.9f, 0.3f),
                    Mth.lerp(blueFade, 1.0f, 0.8f)
            };
        }
    }

    private static float calculateAlpha(float progress) {
        if (progress < 0.2f) {
            return progress / 0.2f;
        } else if (progress > 0.7f) {
            return 1.0f - (progress - 0.7f) / 0.3f;
        }
        return 1.0f;
    }
}