package com.gytrinket.gytrinket.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块实体注册类
 * 负责注册所有的方块实体
 */
public class ModBlockEntities {
    // 延迟注册器，用于注册方块实体
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, com.gytrinket.gytrinket.gytrinket.MODID);

    // 注册光点核心方块实体
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightPointCoreBlockEntity>> LIGHT_POINT_CORE = BLOCK_ENTITIES.register("light_point_core",
            () -> BlockEntityType.Builder.of(LightPointCoreBlockEntity::new, ModBlocks.LIGHT_POINT_CORE.get()).build(null));
}
