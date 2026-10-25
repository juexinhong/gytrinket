package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端注册：把战场维度的天空/雾效挂到 dimension_type 的 effects 上。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IslandClientSetup {

    private IslandClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(gytrinket.MODID, "island"), new IslandDimensionEffects());
    }
}