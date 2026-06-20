package com.gytrinket.gytrinket.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块注册类
 * 负责注册所有的方块
 */
public class ModBlocks {
    // 延迟注册器，用于注册方块
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(com.gytrinket.gytrinket.gytrinket.MODID);

    // 注册光点核心方块
    public static final DeferredBlock<Block> LIGHT_POINT_CORE = BLOCKS.register("light_point_core",
            () -> new LightPointCoreBlock(BlockBehaviour.Properties.of()
                    .strength(0.5f)
                    .noOcclusion()
                    .lightLevel(state -> 15)));
}
