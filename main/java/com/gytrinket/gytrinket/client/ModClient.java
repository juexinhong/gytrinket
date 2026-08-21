package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.blocks.ModBlockEntities;
import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.client.LightningRenderManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer.DroneBulletTrailManager;
import com.gytrinket.gytrinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderEvent;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleTickEvent;
import com.gytrinket.gytrinket.client.shader.ModShaders;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBulletRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer.DroneBeamRenderer;
import com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer.ArmorShardRenderer;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanRenderer;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanEntityModel;
import com.gytrinket.gytrinket.core.entity.construct.wingman.ExplosiveProjectileRenderer;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorConfigContainer;
import com.gytrinket.gytrinket.client.screen.InterceptorConfigContainerScreen;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmRenderer;
import com.gytrinket.gytrinket.items.ModItems;
import com.gytrinket.gytrinket.key.KeyInputHandler;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneInputHandler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化类
 * 负责注册客户端相关的渲染器
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class ModClient {
    /**
     * 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(LightningRenderManager::onRenderLevelLast);
        NeoForge.EVENT_BUS.addListener(EnergyWaveVisualManager::onRenderLevelLast);
        NeoForge.EVENT_BUS.addListener(DroneBulletTrailManager::onRenderLevelLast);
        ShieldParticleRenderEvent.init();
        ShieldParticleTickEvent.init();
    }

    /**
     * 注册光点核心物品的自定义渲染器扩展
     * 使 Minecraft 使用 BEWLR 渲染路径，让 GeckoLib 的 GeoItemRenderer 接管 3D 模型渲染
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return LightPointCoreItemRenderer.getRenderer();
            }
        }, ModItems.LIGHT_POINT_CORE.get());
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(InterceptorConfigContainer.TYPE, InterceptorConfigContainerScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyInputHandler.onRegisterKeyMappings(event);
        DroneInputHandler.onRegisterKeyMappings(event);
    }

    /**
     * 注册自定义着色器
     * shield_glass: Alpha 混合（玻璃表面）
     * gytrinket_energy_wave_vol: 能量波体积渲染（3D raymarching）
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(com.gytrinket.gytrinket.gytrinket.MODID, "shield_glass"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ),
            ModShaders::setShieldGlassShader
        );
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(com.gytrinket.gytrinket.gytrinket.MODID, "gytrinket_energy_wave_vol"),
                DefaultVertexFormat.POSITION_TEX
            ),
            ModShaders::setEnergyWaveVolShader
        );
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(com.gytrinket.gytrinket.gytrinket.MODID, "gytrinket_lightning_vol"),
                DefaultVertexFormat.POSITION_TEX
            ),
            ModShaders::setLightningVolShader
        );
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

    /**
     * 注册模型层定义（LayerDefinitions）
     * 标准 EntityModel 渲染需要在此烘焙模型的 LayerLocation
     */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WingmanEntityModel.LAYER_LOCATION, WingmanEntityModel::createBodyLayer);
    }
}
