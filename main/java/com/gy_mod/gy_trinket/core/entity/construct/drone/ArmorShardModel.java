package com.gy_mod.gy_trinket.core.entity.construct.drone;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArmorShardModel extends GeoModel<ArmorShardEntity> {

    @Override
    public ResourceLocation getModelResource(ArmorShardEntity animatable) {
        return new ResourceLocation("gytrinket", "geo/armor_fragment.geo.json");
    }

    @Override
    public ResourceLocation getAnimationResource(ArmorShardEntity animatable) {
        return new ResourceLocation("gytrinket", "animations/armor_fragment.animation.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArmorShardEntity animatable) {
        return new ResourceLocation("gytrinket", "textures/entity/armor_fragment1.png");
    }

    public ResourceLocation getGlowTextureResource(ArmorShardEntity animatable) {
        return new ResourceLocation("gytrinket", "textures/entity/armor_fragment2.png");
    }
}

