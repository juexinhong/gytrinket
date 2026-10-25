package com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer;

import com.mojang.math.Vector3f;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBeamEntityModel;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBeamProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 无人机光束炮渲染器（标准 Minecraft 实体渲染，不依赖 GeckoLib）
 * <p>
 * 保留了原 GeckoLib 动画 {@code fire} 的横截面脉动：x/y 缩放 0 → 1 → 0，
 * 每个 20 tick 循环一次（光束因此呈现"闪现"效果）。
 */
public class DroneBeamRenderer extends EntityRenderer<DroneBeamProjectile> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/drone_beam.png");

    private final DroneBeamEntityModel model;

    public DroneBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DroneBeamEntityModel(context.bakeLayer(DroneBeamEntityModel.LAYER_LOCATION));
        this.shadowRadius = 0.5F;
    }

    @Override
    public ResourceLocation getTextureLocation(DroneBeamProjectile entity) {
        return TEXTURE;
    }

    @Override
    public void render(DroneBeamProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 原动画 fire：keyframe 0.0/0.1667/0.3333 秒 → scale [0,0,1] / [1,1,1] / [0,0,1]，1 秒一循环
        float ticks = Math.floorMod(entity.tickCount, 20) + partialTicks;
        float seconds = ticks / 20.0F;
        float pulse;
        if (seconds < 0.1667F) {
            pulse = seconds / 0.1667F;
        } else if (seconds < 0.3333F) {
            pulse = 1.0F - (seconds - 0.1667F) / 0.1666F;
        } else {
            pulse = 0.0F;
        }
        ModelPart root = this.model.getRootPart();
        root.xScale = pulse;
        root.yScale = pulse;
        root.zScale = 1.0F;

        poseStack.pushPose();
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-pitch));
        poseStack.translate(0.0F, 0.0F, -10.0F);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
        // 切到原生实体模型空间（x/y 取反、脚底 y=24）
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        this.model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    protected int getBlockLightLevel(DroneBeamProjectile entity, BlockPos pos) {
        return 15;
    }
}
