package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 僚机构造体渲染器（占位，暂时复用无人机渲染逻辑）
 */
public class WingmanRenderer extends GeoEntityRenderer<WingmanConstructEntity> {
    public WingmanRenderer(EntityRendererProvider.Context context) {
        super(context, new WingmanModel());
        withScale(0.8F, 0.8F);

        addRenderLayer(new GeoRenderLayer<WingmanConstructEntity>(this) {
            @Override
            public void render(PoseStack poseStack, WingmanConstructEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                ResourceLocation glowTexture = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone2.png");
                RenderType glowRenderType = RenderType.eyes(glowTexture);
                VertexConsumer glowBuffer = bufferSource.getBuffer(glowRenderType);
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowRenderType,
                    glowBuffer, partialTick, packedLight, packedOverlay,
                    1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }

    @Override
    public void render(WingmanConstructEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float renderYaw = entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTicks;

        poseStack.pushPose();
        poseStack.translate(0, 0.3D, 0);

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
    protected int getBlockLightLevel(WingmanConstructEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.BLOCK, pos);
    }
}
