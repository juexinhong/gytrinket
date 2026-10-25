package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.mojang.math.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

/**
 * 蜂群构造体渲染器（标准 Minecraft 实体渲染，不依赖 GeckoLib）
 */
public class SwarmRenderer extends LivingEntityRenderer<SwarmConstructEntity, SwarmEntityModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/swarm.png");

    public SwarmRenderer(EntityRendererProvider.Context context) {
        super(context, new SwarmEntityModel(context.bakeLayer(SwarmEntityModel.LAYER_LOCATION)), 0.0F);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(SwarmConstructEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(SwarmConstructEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick) {
        poseStack.translate(0.0D, 0.1D, 0.0D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - rotationYaw));
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTick;
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-pitch));
    }

    @Override
    protected void scale(SwarmConstructEntity entity, PoseStack poseStack, float partialTickTime) {
        // 原 GeckoLib 渲染器 withScale(0.6)。
        // 1.20.1 的 LivingEntityRenderer 不会自动应用实体大小倍率，这里手动乘上。
        float scale = 0.6F * (float) entity.getSizeMultiplier();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected void renderNameTag(SwarmConstructEntity entity, Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        // 不渲染蜂群上方的名牌
    }

    @Override
    protected int getBlockLightLevel(SwarmConstructEntity entity, BlockPos pos) {
        return entity.getLevel().getBrightness(LightLayer.BLOCK, pos);
    }
}
