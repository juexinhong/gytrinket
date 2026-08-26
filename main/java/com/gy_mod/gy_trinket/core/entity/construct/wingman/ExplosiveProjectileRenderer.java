package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.DroneBulletTrailManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.TrailType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 爆破弹渲染器（纯拖尾，无3D模型）
 */
public class ExplosiveProjectileRenderer extends EntityRenderer<ExplosiveProjectile> {

    public ExplosiveProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ExplosiveProjectile entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        DroneBulletTrailManager.registerTrail(entity, TrailType.EXPLOSIVE);
    }

    @Override
    protected int getBlockLightLevel(ExplosiveProjectile entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosiveProjectile entity) {
        return new ResourceLocation("textures/entity/explosive_projectile.png");
    }
}

