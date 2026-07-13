package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.blocks.ModBlockEntities;
import com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.client.LightningRenderManager;
import com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleRenderEvent;
import com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleTickEvent;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBulletRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.DroneBeamRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.DroneBulletTrailManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer.ArmorShardRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.ExplosiveProjectileRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmRenderer;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.client.EnergyWaveRenderManager;
import com.gy_mod.gy_trinket.key.KeyInputHandler;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneInputHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化类
 * 负责注册客户端相关的渲染器
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClient {
    /**
     * 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(LightningRenderManager::onRenderLevelLast);
        MinecraftForge.EVENT_BUS.addListener(EnergyWaveRenderManager::onRenderLevelLast);
        MinecraftForge.EVENT_BUS.addListener(DroneBulletTrailManager::onRenderLevelLast);
        ShieldParticleRenderEvent.init();
        ShieldParticleTickEvent.init();
        com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.init();
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
        // 注册僚机渲染器
        event.registerEntityRenderer(ModEntities.WINGMAN_CONSTRUCT.get(), WingmanRenderer::new);
        // 注册爆破弹渲染器
        event.registerEntityRenderer(ModEntities.EXPLOSIVE_PROJECTILE.get(), ExplosiveProjectileRenderer::new);
        // 注册蜂群渲染器
        event.registerEntityRenderer(ModEntities.SWARM_CONSTRUCT.get(), SwarmRenderer::new);
    }
}