package com.gy_mod.gy_trinket.core.entity.meteor.client;

import com.gy_mod.gy_trinket.core.entity.meteor.MeteorEntity;
import com.gy_mod.gy_trinket.core.entity.meteor.MeteorShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 陨石渲染器：把缓存里的外壳体素逐个用原版方块模型画出来。
 * <p>
 * 所有方块都走同一个 solid RenderType，会由 {@link MultiBufferSource} 合批；
 * 单颗陨石只画外壳（约 100~300 个方块），不会出现「整颗实心球」的开销。
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        MeteorShape shape = MeteorShapeCache.get(entity.getShapeSeed(), entity.getSize(), entity.isFragment());
        float cellSize = shape.cellSize();
        float scale = cellSize * 0.98F;

        poseStack.pushPose();
        // 实体坐标在脚底，形状中心在碰撞箱中心。
        // 碰撞箱居中缩小后，模型中心 = 碰撞箱半高；视觉外壳围绕这个中心绘制。
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        // 把体素外壳的实际最外侧面缩放到标称半径。
        poseStack.scale(shape.visualScale(), shape.visualScale(), shape.visualScale());

        // 缓慢转向速度方向；yaw 由服务端同步，pitch 在客户端做插值。
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 方块缩放对所有格子相同，提到循环外；循环内只做平移/回退，
        // 避免每个体素 push/pop 一次 Pose。矩阵结果与逐格 push+scale 完全等价。
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        for (MeteorShape.RenderCell cell : shape.cells()) {
            float tx = (cell.x() * cellSize - scale * 0.5F) / scale;
            float ty = (cell.y() * cellSize - scale * 0.5F) / scale;
            float tz = (cell.z() * cellSize - scale * 0.5F) / scale;
            poseStack.translate(tx, ty, tz);
            this.blockRenderer.renderSingleBlock(
                    cell.state(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.translate(-tx, -ty, -tz);
        }
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        // 渲染器不直接使用实体贴图；返回一个确实存在的贴图以兼容父类。
        return new ResourceLocation("minecraft", "textures/block/deepslate.png");
    }
}
