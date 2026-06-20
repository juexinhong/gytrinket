package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.blocks.ModBlockEntities;
import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.client.LightningRenderManager;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderEvent;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleTickEvent;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBulletRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer.DroneBeamRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer.ArmorShardRenderer;
import com.gytrinket.gytrinket.key.KeyInputHandler;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneInputHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化类
 * 负责注册客户端相关的渲染器
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClient {
    /**
     * 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(LightningRenderManager::onRenderLevelLast);
        ShieldParticleRenderEvent.init();
        ShieldParticleTickEvent.init();
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyInputHandler.onRegisterKeyMappings(event);
        DroneInputHandler.onRegisterKeyMappings(event);
    }

    /**
     * 注册方块实体渲染器事件
     * 在此事件中注册方块实体的渲染器
     * @param event 渲染器注册事件
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 注册光点核心方块的渲染器
        event.registerBlockEntityRenderer(ModBlockEntities.LIGHT_POINT_CORE.get(), LightPointCoreBlockRenderer::new);
        // 注册无人机渲染器
        event.registerEntityRenderer(ModEntities.DRONE_CONSTRUCT.get(), DroneRenderer::new);
        // 注册无人机子弹渲染器
        event.registerEntityRenderer(ModEntities.DRONE_BULLET.get(), DroneBulletRenderer::new);
        // 注册无人机光束炮渲染器
        event.registerEntityRenderer(ModEntities.DRONE_BEAM.get(), DroneBeamRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMOR_SHARD.get(), ArmorShardRenderer::new);
    }
}
