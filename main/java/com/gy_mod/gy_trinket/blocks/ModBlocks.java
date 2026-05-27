package com.gy_mod.gy_trinket.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组方块注册类
 * 负责注册所有的方块
 */
public class ModBlocks {
    // 延迟注册器，用于注册方块
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, com.gy_mod.gy_trinket.gytrinket.MODID);

    // 注册光点核心方块
    public static final RegistryObject<Block> LIGHT_POINT_CORE = BLOCKS.register("light_point_core",
            () -> new LightPointCoreBlock(BlockBehaviour.Properties.of()
                    .strength(0.5f)
                    .noOcclusion()
                    .lightLevel(state -> 15)));
}