package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * 客户端注册：把战场维度的天空/雾效挂到 dimension_type 的 effects 上。
 */
@EventBusSubscriber(modid = gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IslandClientSetup {

    private IslandClientSetup() {
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(gytrinket.MODID, "island"),
                new IslandDimensionEffects());
    }
}
