package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.blocks.ModBlockEntities;
import com.gytrinket.gytrinket.blocks.ModBlocks;
import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.meteor.MeteorEntities;
import com.gytrinket.gytrinket.core.sound.ModSounds;
import com.gytrinket.gytrinket.core.world.ModChunkGenerators;
import com.gytrinket.gytrinket.core.world.ModWorldgen;
import com.gytrinket.gytrinket.items.ModCreativeModeTabs;
import com.gytrinket.gytrinket.items.ModItems;
import com.gytrinket.gytrinket.items.ModMenus;
import com.gytrinket.gytrinket.particle.ModParticles;
import com.gytrinket.gytrinket.registry.ModRecipes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

/**
 * 注册表模块：所有 DeferredRegister 与实体属性注册都在这里，主类不再逐个接线。
 */
public final class RegistryModule implements GyModule {

    @Override
    public String id() {
        return "core.registry";
    }

    @Override
    public void register(ModuleContext context) {
        ModBlocks.BLOCKS.register(context.modBus());
        ModBlockEntities.BLOCK_ENTITIES.register(context.modBus());
        ModItems.ITEMS.register(context.modBus());
        ModRecipes.SERIALIZERS.register(context.modBus());
        ModMenus.MENUS.register(context.modBus());
        ModCreativeModeTabs.register(context.modBus());
        ModParticles.PARTICLE_TYPES.register(context.modBus());
        ModSounds.SOUND_EVENTS.register(context.modBus());
        ModChunkGenerators.CHUNK_GENERATORS.register(context.modBus());
        ModWorldgen.register(context.modBus());
        ModEntities.register(context.modBus());
        MeteorEntities.register(context.modBus());
        context.modBus().addListener(RegistryModule::registerEntityAttributes);
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DRONE_CONSTRUCT.get(), DroneConstructEntity.createAttributes().build());
        event.put(ModEntities.SWARM_CONSTRUCT.get(), SwarmConstructEntity.createAttributes().build());
        event.put(ModEntities.WINGMAN_CONSTRUCT.get(), WingmanConstructEntity.createAttributes().build());
    }
}
