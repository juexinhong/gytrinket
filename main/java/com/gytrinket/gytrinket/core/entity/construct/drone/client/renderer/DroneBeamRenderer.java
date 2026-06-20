package com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBeamModel;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBeamProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 无人机光束炮渲染器 - 使用GeckoLib渲染3D模型
 */
public class DroneBeamRenderer extends GeoEntityRenderer<DroneBeamProjectile> {

    public DroneBeamRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneBeamModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(DroneBeamProjectile entity, float entityYaw, float partialTicks, PoseStack poseStack,
                     MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw = entity.getYRot();
        float pitch = entity.getXRot();

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        poseStack.translate(0.0F, 0.0F, -10.0F);

        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    @Override
    protected int getBlockLightLevel(DroneBeamProjectile entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(DroneBeamProjectile entity) {
        return new DroneBeamModel().getTextureResource(entity);
    }
}
