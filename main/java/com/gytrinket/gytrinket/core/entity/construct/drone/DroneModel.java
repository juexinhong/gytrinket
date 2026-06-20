package com.gytrinket.gytrinket.core.entity.construct.drone;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 无人机构造体模型
 * <p>
 * 根据无人机效果选择对应的模型、纹理和动画资源
 */
public class DroneModel extends GeoModel<DroneConstructEntity> {
    @Override
    public ResourceLocation getModelResource(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/assaultdrone.geo.json");
        } else if (entity.isDefenseDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/defense_drone.geo.json");
        } else {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/drone.geo.json");
        }
    }

    @Override
    public ResourceLocation getTextureResource(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/assaultdrone1.png");
        } else if (entity.isDefenseDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/defense_drone1.png");
        } else {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone1.png");
        }
    }

    @Override
    public ResourceLocation getAnimationResource(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/assaultdrone.animation.json");
        } else if (entity.isDefenseDrone()) {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone.animation.json");
        } else {
            return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone.animation.json");
        }
    }
}
