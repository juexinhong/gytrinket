package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 爆破弹渲染器（占位，暂时复用无人机子弹渲染逻辑）
 */
public class ExplosiveProjectileRenderer extends GeoEntityRenderer<ExplosiveProjectile> {

    public ExplosiveProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, new ExplosiveProjectileModel());
        this.scaleWidth = 0.7F;
        this.scaleHeight = 0.7F;
    }

    @Override
    protected int getBlockLightLevel(ExplosiveProjectile entity, BlockPos blockPos) {
        return 15;
    }
}
