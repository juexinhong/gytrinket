package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 自定义区块生成器注册
 */
public class ModChunkGenerators {

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registry.CHUNK_GENERATOR_REGISTRY, gytrinket.MODID);

    public static final Supplier<Codec<? extends ChunkGenerator>> ISLAND =
            CHUNK_GENERATORS.register("island", () -> IslandChunkGenerator.CODEC);
}
