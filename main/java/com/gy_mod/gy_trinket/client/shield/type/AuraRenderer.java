package com.gy_mod.gy_trinket.client.shield.type;

import com.gy_mod.gy_trinket.client.shield.ShieldHudRenderer;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID, value = Dist.CLIENT)
public class AuraRenderer {

    private static final ResourceLocation AURA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        com.gy_mod.gy_trinket.gytrinket.MODID, "textures/particle/aura.png"
    );

    private AuraRenderer() {}

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

        double displayAlpha = AuraClientData.getDisplayAlpha();
        if (displayAlpha <= 0.001) return;

        float alpha = (float) displayAlpha;
        double size = AuraClientData.getDisplaySize();

        float pt = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();

        List<double[]> renderPositions = getRenderPositions(mc, player, pt, camPos);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, AURA_TEXTURE);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float halfSize = (float) size / 2.0f;

        for (double[] pos : renderPositions) {
            float px = (float) pos[0];
            float py = (float) pos[1];
            float pz = (float) pos[2];

            bufferBuilder.vertex(matrix, px - halfSize, py, pz - halfSize).uv(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
            bufferBuilder.vertex(matrix, px - halfSize, py, pz + halfSize).uv(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
            bufferBuilder.vertex(matrix, px + halfSize, py, pz + halfSize).uv(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
            bufferBuilder.vertex(matrix, px + halfSize, py, pz - halfSize).uv(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        }

        BufferUploader.drawWithShader(bufferBuilder.end());

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
