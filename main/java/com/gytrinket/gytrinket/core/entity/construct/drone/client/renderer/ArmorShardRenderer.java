package com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer;

import com.gytrinket.gytrinket.core.entity.construct.drone.ArmorShardEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.ArmorShardModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ArmorShardRenderer extends GeoEntityRenderer<ArmorShardEntity> {

    public ArmorShardRenderer(EntityRendererProvider.Context context) {
        super(context, new ArmorShardModel());
    }

    @Override
    protected int getBlockLightLevel(ArmorShardEntity entity, BlockPos blockPos) {
        return 15;
    }
}
