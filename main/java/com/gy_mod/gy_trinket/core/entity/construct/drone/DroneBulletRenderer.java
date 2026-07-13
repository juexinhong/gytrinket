package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.DroneBulletTrailManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 无人机子弹渲染器 - 仅渲染轨迹，不渲染模型
 */
public class DroneBulletRenderer extends EntityRenderer<DroneBullet> {

    public DroneBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DroneBullet entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        DroneBulletTrailManager.registerTrail(entity);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneBullet entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/drone_bullet.png");
    }
}
