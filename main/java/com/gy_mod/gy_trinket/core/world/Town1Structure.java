package com.gy_mod.gy_trinket.core.world;

import com.gy_mod.gy_trinket.core.world.terrain.BattlefieldTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 城镇 1（孤岛维度专属结构）。
 * <p>
 * 只在孤岛维度原点区块 (0,0) 生成一次，把 {@code data/gytrinket/structure/town1.nbt}
 * 的整座城镇（含四门、城墙、住宅、中央高塔）粘贴到 {@link #ORIGIN}。
 * 结构 NBT 已剔除空气块，因此不会清除孤岛地形；地形由 {@code gytrinket:island}
 * 维度生成器铺到 {@link BattlefieldTerrain#PAD_TOP}，与模板的地面层对齐。
 */
public class Town1Structure extends Structure {

    public static final Codec<Town1Structure> CODEC = simpleCodec(Town1Structure::new);

    /** 模板最小角：x/z = -93，地面层 y=0 对应孤岛台面 y=32 */
    public static final BlockPos ORIGIN =
            new BlockPos(-93, BattlefieldTerrain.PAD_TOP, -93);

    public Town1Structure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.TOWN1.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        if (Math.abs(chunkPos.x) <= 1 && Math.abs(chunkPos.z) <= 1) {
            com.gy_mod.gy_trinket.gytrinket.LOGGER.info("[town1] findGenerationPoint chunk={}", chunkPos);
        }
        // 只在原点区块生成一次，其余区块直接跳过（结构集仍会逐区块询问）
        if (chunkPos.x != 0 || chunkPos.z != 0) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(ORIGIN,
                builder -> builder.addPiece(new Town1Piece(context.structureTemplateManager(), ORIGIN))));
    }
}
