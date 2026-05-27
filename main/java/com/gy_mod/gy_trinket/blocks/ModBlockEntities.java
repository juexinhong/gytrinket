package com.gy_mod.gy_trinket.blocks;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组方块实体注册类
 * 负责注册所有的方块实体
 */
public class ModBlockEntities {
    // 延迟注册器，用于注册方块实体
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, com.gy_mod.gy_trinket.gytrinket.MODID);

    // 注册光点核心方块实体
    public static final RegistryObject<BlockEntityType<LightPointCoreBlockEntity>> LIGHT_POINT_CORE = BLOCK_ENTITIES.register("light_point_core",
            () -> BlockEntityType.Builder.of(LightPointCoreBlockEntity::new, ModBlocks.LIGHT_POINT_CORE.get()).build(null));
}