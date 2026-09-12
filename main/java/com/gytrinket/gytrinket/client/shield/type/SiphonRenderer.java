package com.gytrinket.gytrinket.client.shield.type;

import com.gytrinket.gytrinket.client.shield.ShieldHudRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class SiphonRenderer {

    private static final ResourceLocation SIPHON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        com.gytrinket.gytrinket.gytrinket.MODID, "textures/particle/siphon.png"
    );

    private SiphonRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        double currentShield = ShieldHudRenderer.getInstance().getCurrentShield();
        if (currentShield <= 0) return;

        List<SiphonClientData.State> states = SiphonClientData.getRenderStates();
        if (states.isEmpty()) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = event.getCamera().getPosition();

        List<double[]> renderPositions = getRenderPositions(mc, player, pt, camPos);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SIPHON_TEXTURE);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        if (renderPositions.isEmpty()) {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return;
        }

        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // 每个虹吸实例各自独立绘制贴图（各用各的尺寸与透明度）
        for (SiphonClientData.State state : states) {
            float alpha = (float) state.displayAlpha;
            float halfSize = (float) state.displaySize / 2.0f;
            // 亮度固定为12（0~15），映射为颜色缩放
            float colorScale = 12.0f / 15.0f;
            int rgb = (int)(colorScale * 255);
            int packedColor = ((int)(alpha * 255) << 24) | (rgb << 16) | (rgb << 8) | rgb;

            for (double[] pos : renderPositions) {
                float px = (float) pos[0];
                float py = (float) pos[1];
                float pz = (float) pos[2];

                bufferBuilder.addVertex(matrix, px - halfSize, py, pz - halfSize).setUv(0.0f, 0.0f).setColor(packedColor);
                bufferBuilder.addVertex(matrix, px - halfSize, py, pz + halfSize).setUv(0.0f, 1.0f).setColor(packedColor);
                bufferBuilder.addVertex(matrix, px + halfSize, py, pz + halfSize).setUv(1.0f, 1.0f).setColor(packedColor);
                bufferBuilder.addVertex(matrix, px + halfSize, py, pz - halfSize).setUv(1.0f, 0.0f).setColor(packedColor);
            }
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static List<double[]> getRenderPositions(Minecraft mc, Player player, float partialTick, Vec3 camPos) {
        List<double[]> positions = new ArrayList<>();

        int[] protectedIds = SiphonClientData.getProtectedEntityIds();
        if (protectedIds.length > 0) {
            for (int entityId : protectedIds) {
                Entity entity = mc.level.getEntity(entityId);
                if (entity != null) {
                    double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camPos.x;
                    double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + 0.05 - camPos.y;
                    double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camPos.z;
                    positions.add(new double[]{x, y, z});
                }
            }
        } else {
            double x = Mth.lerp(partialTick, player.xOld, player.getX()) - camPos.x;
            double y = Mth.lerp(partialTick, player.yOld, player.getY()) + 0.05 - camPos.y;
            double z = Mth.lerp(partialTick, player.zOld, player.getZ()) - camPos.z;
            positions.add(new double[]{x, y, z});
        }

        return positions;
    }
}
