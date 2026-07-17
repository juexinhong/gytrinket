package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.DroneBulletTrailManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.TrailType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 爆破弹渲染器 - 仅渲染轨迹，不渲染模型
 * <p>
 * 与无人机子弹使用相同的轨迹渲染系统，
 * 通过 {@link DroneBulletTrailManager} 注册拖尾。
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
    public ResourceLocation getTextureLocation(ExplosiveProjectile entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/explosive_projectile.png");
    }
}
