package com.gytrinket.gytrinket.core.entity.construct.wingman;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 爆破弹模型（占位，暂时复用无人机子弹模型）
 */
public class ExplosiveProjectileModel extends GeoModel<ExplosiveProjectile> {
    @Override
    public ResourceLocation getModelResource(ExplosiveProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "geo/drone_bullet.geo.json");
    }

    @Override
    public ResourceLocation getAnimationResource(ExplosiveProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "animations/drone_bullet.animation.json");
    }

    @Override
    public ResourceLocation getTextureResource(ExplosiveProjectile animatable) {
        return ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone_bullet.png");
    }
}
