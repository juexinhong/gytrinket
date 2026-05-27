package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.blocks.LightPointCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 光点核心方块渲染器
 * 负责使用 GeckoLib 渲染光点核心方块
 * 支持双层透明材质渲染
 */
public class LightPointCoreBlockRenderer extends GeoBlockRenderer<LightPointCoreBlockEntity> {
    /**
     * 构造函数
     * @param context 方块实体渲染器提供者上下文
     */
    public LightPointCoreBlockRenderer(BlockEntityRendererProvider.Context context) {
        super(new LightPointCoreBlockModel());
    }

    /**
     * 自定义渲染方法
     * 实现双层材质的渲染
     * @param poseStack 坐标变换栈
     * @param animatable 动画方块实体
     * @param bufferSource 多缓冲源
     * @param renderType 渲染类型
     * @param buffer 顶点消费者
     * @param yaw Y轴旋转
     * @param partialTick 部分刻
     * @param packedLight 打包光照
     */
    @Override
    public void defaultRender(PoseStack poseStack, LightPointCoreBlockEntity animatable, MultiBufferSource bufferSource, RenderType renderType, VertexConsumer buffer, float yaw, float partialTick, int packedLight) {
        // 第一次渲染，使用第一个材质（基础层）
        super.defaultRender(poseStack, animatable, bufferSource, renderType, buffer, yaw, partialTick, packedLight);

        // 第二次渲染，使用第二个材质（装饰层）
        poseStack.pushPose();

        // 获取渲染颜色
        Color renderColor = getRenderColor(animatable, partialTick, packedLight);
        float red = renderColor.getRedFloat();
        float green = renderColor.getGreenFloat();
        float blue = renderColor.getBlueFloat();
        float alpha = renderColor.getAlphaFloat();
        int packedOverlay = getPackedOverlay(animatable, 0, partialTick);
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(animatable, this));

        // 使用第二个材质
        ResourceLocation secondTexture = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/block/light_point_core_block_gy3.png");
        RenderType secondRenderType = RenderType.entityTranslucent(secondTexture);
        VertexConsumer secondBuffer = bufferSource.getBuffer(secondRenderType);

        // 执行第二层渲染
        preRender(poseStack, animatable, model, bufferSource, secondBuffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        if (firePreRenderEvent(poseStack, model, bufferSource, partialTick, packedLight)) {
            preApplyRenderLayers(poseStack, animatable, model, secondRenderType, bufferSource, secondBuffer, packedLight, packedLight, packedOverlay);
            actuallyRender(poseStack, animatable, model, secondRenderType, bufferSource, secondBuffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            applyRenderLayers(poseStack, animatable, model, secondRenderType, bufferSource, secondBuffer, partialTick, packedLight, packedOverlay);
            postRender(poseStack, animatable, model, bufferSource, secondBuffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            firePostRenderEvent(poseStack, model, bufferSource, partialTick, packedLight);
        }

        poseStack.popPose();

        // 最终渲染
        renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        doPostRenderCleanup();
    }

    /**
     * 光点核心方块模型类
     * 定义模型、材质和动画资源的位置
     */
    private static class LightPointCoreBlockModel extends GeoModel<LightPointCoreBlockEntity> {
        /**
         * 获取 GeckoLib 模型资源位置
         * @param object 方块实体
         * @return 模型资源位置
         */
        @Override
        public ResourceLocation getModelResource(LightPointCoreBlockEntity object) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/light_point_core_block_gy.geo.json");
        }

        /**
         * 获取材质资源位置
         * @param object 方块实体
         * @return 材质资源位置（第一个材质）
         */
        @Override
        public ResourceLocation getTextureResource(LightPointCoreBlockEntity object) {
            // 使用第一个材质文件（基础层）
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/block/light_point_core_block_gy1.png");
        }

        /**
         * 获取动画资源位置
         * @param object 方块实体
         * @return 动画资源位置
         */
        @Override
        public ResourceLocation getAnimationResource(LightPointCoreBlockEntity object) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/light_point_core_block_gy.animation.json");
        }

        /**
         * 获取渲染类型
         * 为了支持透明度，使用 entityTranslucent 渲染类型
         * @param animatable 方块实体
         * @param texture 材质资源
         * @return 渲染类型
         */
        @Override
        public RenderType getRenderType(LightPointCoreBlockEntity animatable, ResourceLocation texture) {
            // 使用支持透明度的渲染类型
            return RenderType.entityTranslucent(texture);
        }
    }
}