package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 蜂群构造体渲染器
 */
public class SwarmRenderer extends GeoEntityRenderer<SwarmConstructEntity> {
    public SwarmRenderer(EntityRendererProvider.Context context) {
        super(context, new SwarmModel());
        withScale(0.6F, 0.6F);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(SwarmConstructEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float renderYaw = entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTicks;

        poseStack.pushPose();
        poseStack.translate(0, 0.1D, 0);

        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks;

        poseStack.pushPose();
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-renderYaw)));
        poseStack.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(pitch)));
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(renderYaw)));

        BlockPos pos = entity.blockPosition();
        int blockLight = entity.level().getBrightness(LightLayer.BLOCK, pos);
        int skyLight = entity.level().getBrightness(LightLayer.SKY, pos);
        int correctPackedLight = LightTexture.pack(blockLight, skyLight);

        super.render(entity, renderYaw, partialTicks, poseStack, bufferSource, correctPackedLight);

        poseStack.popPose();
        poseStack.popPose();
    }

    @Override
    protected int getBlockLightLevel(SwarmConstructEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.BLOCK, pos);
    }
}
