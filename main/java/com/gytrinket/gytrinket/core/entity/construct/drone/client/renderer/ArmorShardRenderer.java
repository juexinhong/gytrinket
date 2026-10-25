package com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer;

import com.mojang.math.Vector3f;

import com.gytrinket.gytrinket.core.entity.construct.drone.ArmorFragmentEntityModel;
import com.gytrinket.gytrinket.core.entity.construct.drone.ArmorShardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 装甲碎片渲染器（标准 Minecraft 实体渲染，不依赖 GeckoLib）
 * <p>
 * 碎片是纯几何体，恒亮显示（不受方块光照影响）。
 */
public class ArmorShardRenderer extends EntityRenderer<ArmorShardEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/armor_fragment1.png");

    private final ArmorFragmentEntityModel model;

    public ArmorShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ArmorFragmentEntityModel(
                context.bakeLayer(ArmorFragmentEntityModel.LAYER_LOCATION));
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ArmorShardEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ArmorShardEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 与原 GeckoLib 渲染器一致：非生物实体固定转 180°（GeckoLib applyRotations 行为）
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
        // 切到原生实体模型空间（x/y 取反、脚底 y=24）
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    protected int getBlockLightLevel(ArmorShardEntity entity, BlockPos pos) {
        return 15;
    }
}
