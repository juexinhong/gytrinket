package com.gytrinket.gytrinket;

import com.mojang.logging.LogUtils;
import com.gytrinket.gytrinket.blocks.ModBlockEntities;
import com.gytrinket.gytrinket.blocks.ModBlocks;
import com.gytrinket.gytrinket.core.TickScheduler;
import com.gytrinket.gytrinket.particle.ModParticles;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.damage.DamageManager;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.damage_last.LastDamageManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructTypeRegistry;
import com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathProtectionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathExplosionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.CounterPulseBehavior;
import com.gytrinket.gytrinket.core.shield.type.ReflectShieldType;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.event.LightPointStoreEventHandler;
import com.gytrinket.gytrinket.items.ModCreativeModeTabs;
import com.gytrinket.gytrinket.items.ModItems;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.datacenter.DataCenterLifecycleHandler;
import com.gytrinket.gytrinket.storage.datacenter.ModAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(gytrinket.MODID)
public class gytrinket {
    public static final String MODID = "gytrinket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public gytrinket(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModEntities.register(modEventBus);
        ModAttachments.register(modEventBus);

        NeoForge.EVENT_BUS.register(TickScheduler.class);
        NeoForge.EVENT_BUS.register(InvincibilityMarkerManager.class);
        NeoForge.EVENT_BUS.register(new LightPointStoreEventHandler());
        NeoForge.EVENT_BUS.register(DataCenterLifecycleHandler.class);
        NeoForge.EVENT_BUS.register(AttributeManager.class);
        NeoForge.EVENT_BUS.register(ShieldTypeManager.class);
        NeoForge.EVENT_BUS.register(ReflectShieldType.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(NetworkHandler::registerMessages);

        LOGGER.info("光点饰品模组初始化完成");
    }

    private void setup(final FMLCommonSetupEvent event) {
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

        ConstructTypeRegistry.executeRegistries();

        ConstructAttributeRegistry.registerDefaults();

        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathProtectionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathExplosionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new ReshapingBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new CounterPulseBehavior());
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DRONE_CONSTRUCT.get(), com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity.createAttributes().build());
    }
}
