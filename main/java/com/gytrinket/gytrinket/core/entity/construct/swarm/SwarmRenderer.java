package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

/**
 * 蜂群构造体渲染器（标准 Minecraft 实体渲染，不依赖 GeckoLib）
 */
public class SwarmRenderer extends LivingEntityRenderer<SwarmConstructEntity, SwarmEntityModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/swarm.png");

    public SwarmRenderer(EntityRendererProvider.Context context) {
        super(context, new SwarmEntityModel(context.bakeLayer(SwarmEntityModel.LAYER_LOCATION)), 0.0F);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(SwarmConstructEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void renderNameTag(SwarmConstructEntity entity, net.minecraft.network.chat.Component displayName,
                                 PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                 float partialTick) {
        // 不渲染蜂群上方的名牌
    }

    @Override
    protected void setupRotations(SwarmConstructEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks, float scale) {
        poseStack.translate(0.0D, 0.1D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks;
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
    }

    @Override
    protected void scale(SwarmConstructEntity entity, PoseStack poseStack, float partialTickTime) {
        // 原 GeckoLib 渲染器 withScale(0.6)。
        // 实体大小倍率由 getScale() 提供，LivingEntityRenderer 已先应用一次。
        poseStack.scale(0.6F, 0.6F, 0.6F);
    }

    @Override
    protected int getBlockLightLevel(SwarmConstructEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.BLOCK, pos);
    }
}
