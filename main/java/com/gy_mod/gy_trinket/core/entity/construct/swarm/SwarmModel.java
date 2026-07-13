package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 蜂群构造体模型
 */
public class SwarmModel extends GeoModel<SwarmConstructEntity> {
    @Override
    public ResourceLocation getModelResource(SwarmConstructEntity entity) {
        return new ResourceLocation("gytrinket", "geo/swarm.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SwarmConstructEntity entity) {
        return new ResourceLocation("gytrinket", "textures/entity/swarm.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SwarmConstructEntity entity) {
        return new ResourceLocation("gytrinket", "animations/drone.animation.json");
    }
}
