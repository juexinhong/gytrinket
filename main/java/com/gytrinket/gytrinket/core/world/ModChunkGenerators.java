package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 自定义区块生成器注册
 */
public class ModChunkGenerators {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, gytrinket.MODID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> ISLAND =
            CHUNK_GENERATORS.register("island", () -> IslandChunkGenerator.CODEC);
}
