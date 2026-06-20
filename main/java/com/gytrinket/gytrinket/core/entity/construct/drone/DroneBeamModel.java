package com.gytrinket.gytrinket.core.entity.construct.drone;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 无人机光束炮模型类 - 用于加载Blockbench创建的模型和动画文件
 */
public class DroneBeamModel extends GeoModel<DroneBeamProjectile> {

    @Override
    public ResourceLocation getModelResource(DroneBeamProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/drone_beam.geo.json");
    }

    @Override
    public ResourceLocation getAnimationResource(DroneBeamProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone_beam.animation.json");
    }

    @Override
    public ResourceLocation getTextureResource(DroneBeamProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone_beam.png");
    }
}
