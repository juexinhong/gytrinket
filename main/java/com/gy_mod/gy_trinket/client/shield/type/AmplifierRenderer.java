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

/**
 * 增幅护盾渲染器（与光环护盾 AuraRenderer 同款逻辑）
 * <p>
 * 在玩家（或被保护实体）脚下渲染 amplifier.png 地面贴图，
 * 透明度与尺寸由 AmplifierClientData 插值驱动。
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID, value = Dist.CLIENT)
public class AmplifierRenderer {

    private static final ResourceLocation AMPLIFIER_TEXTURE = new ResourceLocation(
        com.gy_mod.gy_trinket.gytrinket.MODID, "textures/particle/amplifier.png"
    );

    private AmplifierRenderer() {}

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

        double displayAlpha = AmplifierClientData.getDisplayAlpha();
        if (displayAlpha <= 0.001) return;

        // 透明度由进度驱动：有危险物时淡入（1.0），无危险物时20刻淡出；
        // 无危险物长时间后透明度归零，此处自然不再渲染
        float alpha = (float) displayAlpha;
        double size = AmplifierClientData.getDisplaySize();
        double brightness = AmplifierClientData.getDisplayBrightness();

        float pt = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();

        List<double[]> renderPositions = getRenderPositions(mc, player, pt, camPos);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, AMPLIFIER_TEXTURE);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        if (renderPositions.isEmpty()) {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return;
        }

        Matrix4f matrix = poseStack.last().pose();

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float halfSize = (float) size / 2.0f;
        // 亮度（8~15）映射为颜色缩放：基础8时较暗，达上限15时为全白
        float colorScale = (float)(brightness / 15.0);
        int rgb = (int)(colorScale * 255);
        int packedColor = ((int)(alpha * 255) << 24) | (rgb << 16) | (rgb << 8) | rgb;

        for (double[] pos : renderPositions) {
            float px = (float) pos[0];
            float py = (float) pos[1];
            float pz = (float) pos[2];

            bufferBuilder.vertex(matrix, px - halfSize, py, pz - halfSize).uv(0.0f, 0.0f).color(packedColor);
            bufferBuilder.vertex(matrix, px - halfSize, py, pz + halfSize).uv(0.0f, 1.0f).color(packedColor);
            bufferBuilder.vertex(matrix, px + halfSize, py, pz + halfSize).uv(1.0f, 1.0f).color(packedColor);
            bufferBuilder.vertex(matrix, px + halfSize, py, pz - halfSize).uv(1.0f, 0.0f).color(packedColor);
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
