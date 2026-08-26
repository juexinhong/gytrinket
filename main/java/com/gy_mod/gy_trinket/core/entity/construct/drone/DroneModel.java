package com.gy_mod.gy_trinket.core.entity.construct.drone;

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
            return new ResourceLocation("gytrinket", "geo/assaultdrone.geo.json");
        } else if (entity.isDefenseDrone()) {
            return new ResourceLocation("gytrinket", "geo/defense_drone.geo.json");
        } else {
            return new ResourceLocation("gytrinket", "geo/drone.geo.json");
        }
    }

    @Override
    public ResourceLocation getTextureResource(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return new ResourceLocation("gytrinket", "textures/entity/assaultdrone1.png");
        } else if (entity.isDefenseDrone()) {
            return new ResourceLocation("gytrinket", "textures/entity/defense_drone1.png");
        } else {
            return new ResourceLocation("gytrinket", "textures/entity/drone1.png");
        }
    }

    @Override
    public ResourceLocation getAnimationResource(DroneConstructEntity entity) {
        return null;
    }
}

