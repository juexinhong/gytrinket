package com.gytrinket.gytrinket.core.entity.construct.wingman;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 僚机构造体模型（占位，暂时复用无人机模型）
 */
public class WingmanModel extends GeoModel<WingmanConstructEntity> {
    @Override
    public ResourceLocation getModelResource(WingmanConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/drone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WingmanConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone1.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WingmanConstructEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone.animation.json");
    }
}
