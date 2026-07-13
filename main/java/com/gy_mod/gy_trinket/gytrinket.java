package com.gy_mod.gy_trinket;

import com.mojang.logging.LogUtils;
import com.gy_mod.gy_trinket.blocks.ModBlockEntities;
import com.gy_mod.gy_trinket.blocks.ModBlocks;
import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.particle.ModParticles;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.damage.DamageManager;
import com.gy_mod.gy_trinket.core.damage.InvincibilityMarkerManager;
import com.gy_mod.gy_trinket.core.damage_last.LastDamageManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructTypeRegistry;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeRegistry;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneRegistry;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanRegistry;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmRegistry;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.NearDeathProtectionBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.NearDeathExplosionBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.CounterPulseBehavior;
import com.gy_mod.gy_trinket.core.shield.type.ReflectShieldType;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.event.LightPointStoreEventHandler;
import com.gy_mod.gy_trinket.items.ModCreativeModeTabs;
import com.gy_mod.gy_trinket.items.ModItems;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.datacenter.DataCenterLifecycleHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(gytrinket.MODID)
public class gytrinket {
    public static final String MODID = "gytrinket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public gytrinket(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModEntities.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(TickScheduler.class);
        MinecraftForge.EVENT_BUS.register(InvincibilityMarkerManager.class);
        MinecraftForge.EVENT_BUS.register(new LightPointStoreEventHandler());
        MinecraftForge.EVENT_BUS.register(DataCenterLifecycleHandler.class);
        MinecraftForge.EVENT_BUS.register(AttributeManager.class);
        MinecraftForge.EVENT_BUS.register(ShieldTypeManager.class);
        MinecraftForge.EVENT_BUS.register(ReflectShieldType.class);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerEntityAttributes);

        LOGGER.info("光点饰品模组初始化完成");
    }

    private void setup(final FMLCommonSetupEvent event) {
        NetworkHandler.registerMessages();
        LOGGER.info("网络消息注册完成");

        DataCenterLifecycleHandler.init();
        LOGGER.info("数据中心初始化完成");

        DamageManager.init();
        LOGGER.info("伤害管理器初始化完成");
        LastDamageManager.init();
        LOGGER.info("最终伤害管理器初始化完成");
        ShieldTypeManager.init();
        LOGGER.info("护盾类型管理器初始化完成");
        
        // 注册无人机构造体类型
        ConstructTypeRegistry.register(new DroneRegistry());
        // 注册僚机构造体类型
        ConstructTypeRegistry.register(new WingmanRegistry());
        // 注册蜂群构造体类型
        ConstructTypeRegistry.register(new SwarmRegistry());
        
        ConstructTypeRegistry.executeRegistries();

        ConstructAttributeRegistry.registerDefaults();

        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathProtectionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathExplosionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new ReshapingBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new CounterPulseBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.SelfDestructBehavior());
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DRONE_CONSTRUCT.get(), com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity.createAttributes().build());
        event.put(ModEntities.SWARM_CONSTRUCT.get(), com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity.createAttributes().build());
    }
}
