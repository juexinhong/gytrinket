package com.gy_mod.gy_trinket.core.world;

import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 自定义区块生成器注册
 */
public class ModChunkGenerators {

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, gytrinket.MODID);

    public static final Supplier<Codec<? extends ChunkGenerator>> ISLAND =
            CHUNK_GENERATORS.register("island", () -> IslandChunkGenerator.CODEC);
}
