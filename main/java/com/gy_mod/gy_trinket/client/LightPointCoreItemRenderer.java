package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.items.LightPointCoreBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 光点核心物品渲染器（仅客户端）
 * 通过 IClientItemExtensions 注册给光点核心物品使用，
 * 使物品形态使用与方块实体相同的 GeckoLib 3D 模型。
 */
@OnlyIn(Dist.CLIENT)
public class LightPointCoreItemRenderer extends GeoItemRenderer<LightPointCoreBlockItem> {
    private static final float DEG_45 = (float) Math.toRadians(30);
    private static final float DEG_30 = (float) Math.toRadians(30);

    private static LightPointCoreItemRenderer instance;

    private LightPointCoreItemRenderer() {
        super(new LightPointCoreItemModel());
    }

    /**
     * 获取共享渲染器实例
     */
    public static LightPointCoreItemRenderer getRenderer() {
        if (instance == null) {
            instance = new LightPointCoreItemRenderer();
        }

        return instance;
    }

    /**
     * 调整显示变换：
     * - 物品栏（GUI）：缩小、X/Y 轴旋转、降低高度
     * - 手持：以方块底面为轴缩小 50%，底部贴合手上
     */
    @Override
    public void preRender(PoseStack poseStack, LightPointCoreBlockItem animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        if (isReRender) {
            return;
        }

        if (this.renderPerspective == ItemDisplayContext.GUI) {
            // 先以方块中心为旋转/缩放轴
            poseStack.translate(0f, 0.0f, 0f);
            poseStack.mulPose(new Quaternionf().rotationX(DEG_30));
            poseStack.mulPose(new Quaternionf().rotationY(DEG_45));
            poseStack.scale(0.63f, 0.63f, 0.63f);
            poseStack.translate(0f, -0.55f, 0f);
        } else if (this.renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || this.renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || this.renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || this.renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            // 以方块底面中心（y=0）为轴缩小50%，底部保持贴合手上
            poseStack.scale(0.5f, 0.5f, 0.5f);
        }
    }

    /**
     * 光点核心物品模型
     * 复用方块实体的模型、材质和动画资源路径
     */
    private static class LightPointCoreItemModel extends GeoModel<LightPointCoreBlockItem> {
        @Override
        public ResourceLocation getModelResource(LightPointCoreBlockItem object) {
            return new ResourceLocation("gytrinket", "geo/light_point_core_block_gy.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(LightPointCoreBlockItem object) {
            return new ResourceLocation("gytrinket", "textures/block/light_point_core_block_gy1.png");
        }

        @Override
        public ResourceLocation getAnimationResource(LightPointCoreBlockItem object) {
            return new ResourceLocation("gytrinket", "animations/light_point_core_block_gy.animation.json");
        }

        /**
         * 获取渲染类型
         * 与方块渲染一致，使用支持透明度的 entityTranslucent
         */
        @Override
        public RenderType getRenderType(LightPointCoreBlockItem animatable, ResourceLocation texture) {
            return RenderType.entityTranslucent(texture);
        }
    }
}
