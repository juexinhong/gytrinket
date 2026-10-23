package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.core.world.terrain.RuinedTowerBlueprint;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 残破高塔结构。
 * <p>
 * 每个区块中心尝试生成一座塔；塔的位置取该点地表高度，塔身由
 * {@link RuinedTowerBlueprint} 程序化砌出（无需 NBT 模板）。
 */
public class RuinedTowerStructure extends Structure {

    public static final MapCodec<RuinedTowerStructure> CODEC = simpleCodec(RuinedTowerStructure::new);

    public RuinedTowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.RUINED_TOWER.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());

        // 残塔只长在中心孤岛上
        if (!com.gytrinket.gytrinket.core.world.terrain.BattlefieldTerrain.isTowerSite(x, z)) {
            return Optional.empty();
        }
        long seed = context.random().nextLong();
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new RuinedTowerPiece(x, y, z, seed))));
    }

    /**
     * 塔的位置取区块中心的地表高度，塔身由种子程序化砌出。
     */
    /** 供结构片段复用的塔基参数（避免重复构造蓝图） */
    static RuinedTowerBlueprint blueprint(long seed) {
        return new RuinedTowerBlueprint(seed);
    }
}
