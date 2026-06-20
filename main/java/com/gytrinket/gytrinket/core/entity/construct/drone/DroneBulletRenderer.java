package com.gytrinket.gytrinket.core.entity.construct.drone;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 无人机子弹渲染器 - 使用GeckoLib渲染3D模型
 */
public class DroneBulletRenderer extends GeoEntityRenderer<DroneBullet> {

    public DroneBulletRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneBulletModel());
        this.scaleWidth = 0.7F;
        this.scaleHeight = 0.7F;
    }

    @Override
    protected int getBlockLightLevel(DroneBullet entity, BlockPos blockPos) {
        return 15;
    }
}
