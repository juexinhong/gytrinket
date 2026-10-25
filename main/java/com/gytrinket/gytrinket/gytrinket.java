package com.gytrinket.gytrinket;

import com.mojang.logging.LogUtils;
import com.gytrinket.gytrinket.config.ClientConfig;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.blocks.ModBlockEntities;
import com.gytrinket.gytrinket.blocks.ModBlocks;
import com.gytrinket.gytrinket.core.world.ModChunkGenerators;
import com.gytrinket.gytrinket.core.world.ModWorldgen;
import com.gytrinket.gytrinket.core.TickScheduler;
import com.gytrinket.gytrinket.particle.ModParticles;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.damage.DamageManager;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.damage_last.LastDamageManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructTypeRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneRegistry;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanRegistry;
import com.gytrinket.gytrinket.core.entity.construct.wingman.NanoRegenManager;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.meteor.MeteorEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathProtectionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathExplosionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.CounterPulseBehavior;
import com.gytrinket.gytrinket.core.shield.type.ReflectShieldType;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.items.ModCreativeModeTabs;
import com.gytrinket.gytrinket.items.ModItems;
import com.gytrinket.gytrinket.registry.ModRecipes;
import com.gytrinket.gytrinket.items.ModMenus;
import com.gytrinket.gytrinket.core.sound.ModSounds;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.datacenter.DataCenterLifecycleHandler;
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
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modEventBus);
        ModWorldgen.register(modEventBus);
        ModEntities.register(modEventBus);
        MeteorEntities.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(TickScheduler.class);
        MinecraftForge.EVENT_BUS.register(InvincibilityMarkerManager.class);
        MinecraftForge.EVENT_BUS.register(DataCenterLifecycleHandler.class);
        MinecraftForge.EVENT_BUS.register(AttributeManager.class);
        MinecraftForge.EVENT_BUS.register(ShieldTypeManager.class);
        MinecraftForge.EVENT_BUS.register(ReflectShieldType.class);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "gytrinket/gytrinket-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "gytrinket/gytrinket-client.toml");

        // Curios 饰品栏可选联动：未安装 Curios 时不注册事件，扩展机制静默失效
        com.gytrinket.gytrinket.compat.CuriosCompat.init();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerEntityAttributes);

    }

    private void setup(final FMLCommonSetupEvent event) {
        NetworkHandler.registerMessages();

        DataCenterLifecycleHandler.init();

        DamageManager.init();
        LastDamageManager.init();
        ShieldTypeManager.init();

        NanoRegenManager.init();
        
        // 注册无人机构造体类型
        ConstructTypeRegistry.register(new DroneRegistry());
        // 注册僚机构造体类型
        ConstructTypeRegistry.register(new WingmanRegistry());
        // 注册蜂群构造体类型
        ConstructTypeRegistry.register(new SwarmRegistry());
        
        ConstructTypeRegistry.executeRegistries();

        // ConstructAttributeRegistry.registerDefaults(); // 已废弃：属性名解析由 ConstructAttributeNameParser 替代

        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathProtectionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathExplosionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new ReshapingBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new CounterPulseBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new com.gytrinket.gytrinket.core.entity.construct.drone.behavior.SelfDestructBehavior());
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DRONE_CONSTRUCT.get(), com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity.createAttributes().build());
        event.put(ModEntities.SWARM_CONSTRUCT.get(), com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity.createAttributes().build());
        event.put(ModEntities.WINGMAN_CONSTRUCT.get(), com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity.createAttributes().build());
    }
}
