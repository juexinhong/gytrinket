package com.gytrinket.gytrinket.core.entity.construct.swarm;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 蜂群构造体模型
 */
public class SwarmModel extends GeoModel<SwarmConstructEntity> {
    @Override
    public ResourceLocation getModelResource(SwarmConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/swarm.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SwarmConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/swarm.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SwarmConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone.animation.json");
    }
}
