package com.gy_mod.gy_trinket.core.entity.construct.drone;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 无人机子弹模型类 - 用于加载Blockbench创建的模型和动画文件
 */
public class DroneBulletModel extends GeoModel<DroneBullet> {

    @Override
    public ResourceLocation getModelResource(DroneBullet animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/drone_bullet.geo.json");
    }

    @Override
    public ResourceLocation getAnimationResource(DroneBullet animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone_bullet.animation.json");
    }

    @Override
    public ResourceLocation getTextureResource(DroneBullet animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone_bullet.png");
    }

    public ResourceLocation getGlowTextureResource(DroneBullet animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone_bullet.png");
    }
}
