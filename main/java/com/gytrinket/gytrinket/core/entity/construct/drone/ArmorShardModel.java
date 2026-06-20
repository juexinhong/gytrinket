package com.gytrinket.gytrinket.core.entity.construct.drone;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArmorShardModel extends GeoModel<ArmorShardEntity> {

    @Override
    public ResourceLocation getModelResource(ArmorShardEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/armor_fragment.geo.json");
    }

    @Override
    public ResourceLocation getAnimationResource(ArmorShardEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/armor_fragment.animation.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArmorShardEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/armor_fragment1.png");
    }

    public ResourceLocation getGlowTextureResource(ArmorShardEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/armor_fragment2.png");
    }
}
