package com.gy_mod.gy_trinket.core.world;

import com.gy_mod.gy_trinket.core.world.terrain.HashNoise;
import com.gy_mod.gy_trinket.core.world.terrain.RuinedTowerBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 法杖山城的结构片段：把 {@link RuinedTowerBlueprint} 的抽象方块写进世界。
 * <p>
 * 城内有两万多个方块，片段会为它覆盖的每个区块各调用一次 postProcess，
 * 因此方块按区块分桶缓存，避免每次重建整座城。
 */
public class RuinedTowerPiece extends StructurePiece {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final long towerSeed;

    /** 区块键 → 该区块内的方块列表（x,y,z,materialOrdinal） */
    private transient Map<Long, List<int[]>> buckets;

    public RuinedTowerPiece(int x, int y, int z, long seed) {
        super(ModWorldgen.RUINED_TOWER_PIECE.get(), 0, makeBox(x, y, z));
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.towerSeed = seed;
    }

    public RuinedTowerPiece(CompoundTag tag) {
        super(ModWorldgen.RUINED_TOWER_PIECE.get(), tag);
        this.centerX = tag.getInt("CX");
        this.centerY = tag.getInt("CY");
        this.centerZ = tag.getInt("CZ");
        this.towerSeed = tag.getLong("Seed");
    }

    private static BoundingBox makeBox(int x, int y, int z) {
        RuinedTowerBlueprint blueprint = new RuinedTowerBlueprint(0L);
        int radius = blueprint.footprint();
        return new BoundingBox(x - radius, y - 24, z - radius,
                x + radius, y + blueprint.totalHeight() + 48, z + radius);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", this.centerX);
        tag.putInt("CY", this.centerY);
        tag.putInt("CZ", this.centerZ);
        tag.putLong("Seed", this.towerSeed);
    }

    private Map<Long, List<int[]>> buckets() {
        if (this.buckets == null) {
            Map<Long, List<int[]>> map = new HashMap<>();
            new RuinedTowerBlueprint(this.towerSeed).build((x, y, z, material) -> {
                // 注意：分桶必须用世界坐标算区块，postProcess 拿到的是世界区块坐标
                long key = chunkKey((this.centerX + x) >> 4, (this.centerZ + z) >> 4);
                map.computeIfAbsent(key, ignored -> new ArrayList<>(512))
                        .add(new int[]{x, y, z, material.ordinal()});
            });
            this.buckets = map;
        }
        return this.buckets;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        List<int[]> list = buckets().get(chunkKey(chunkPos.x, chunkPos.z));
        if (list == null) {
            return;
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        RuinedTowerBlueprint.Material[] materials = RuinedTowerBlueprint.Material.values();

        for (int[] entry : list) {
            int worldX = this.centerX + entry[0];
            int worldY = this.centerY + entry[1];
            int worldZ = this.centerZ + entry[2];
            if (!box.isInside(worldX, worldY, worldZ)) {
                continue;
            }
            mutable.set(worldX, worldY, worldZ);
            BlockState state = blockFor(materials[entry[3]]);
            if (state.isAir()) {
                // 清理地形：只在原本不是空气时写入，省掉大量无意义的方块更新
                if (!level.getBlockState(mutable).isAir()) {
                    level.setBlock(mutable, state, 2);
                }
                continue;
            }
            if (level.getBlockState(mutable).is(Blocks.BEDROCK)) {
                continue;
            }
            level.setBlock(mutable, state, 2);
        }
    }

    /** 抽象材质 → 实际方块。整体白色系：石英、方解石、闪长岩。 */
    private static BlockState blockFor(RuinedTowerBlueprint.Material material) {
        return switch (material) {
            case WHITE_BRICK -> Blocks.QUARTZ_BRICKS.defaultBlockState();
            case WHITE_BLOCK -> Blocks.QUARTZ_BLOCK.defaultBlockState();
            case WHITE_SMOOTH -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            case WHITE_POLISHED -> Blocks.POLISHED_DIORITE.defaultBlockState();
            case WHITE_STONE -> Blocks.CALCITE.defaultBlockState();
            case WHITE_TRIM -> Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
            case PILLAR -> Blocks.QUARTZ_PILLAR.defaultBlockState();
            case STONE_BRICK -> Blocks.STONE_BRICKS.defaultBlockState();
            case SMOOTH_STONE -> Blocks.SMOOTH_STONE.defaultBlockState();
            case GILDED -> Blocks.GOLD_BLOCK.defaultBlockState();
            case GLOW -> Blocks.SEA_LANTERN.defaultBlockState();
            case CRYSTAL -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case RUNE -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case GLASS -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case BARS -> Blocks.IRON_BARS.defaultBlockState();
            case RUBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            case DARK -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case DARK_STAIR -> Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
            case ROOF -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case ROOF_STAIR -> Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
            case WOOD -> Blocks.BIRCH_PLANKS.defaultBlockState();
            case LEAVES -> Blocks.OAK_LEAVES.defaultBlockState();
            case PANE -> Blocks.GLASS_PANE.defaultBlockState();
            case STAIR -> Blocks.QUARTZ_STAIRS.defaultBlockState();
            case FENCE -> Blocks.OAK_FENCE.defaultBlockState();
            case TRAPDOOR -> Blocks.OAK_TRAPDOOR.defaultBlockState();
            case SLAB -> Blocks.QUARTZ_SLAB.defaultBlockState();
            case CARPET -> Blocks.RED_CARPET.defaultBlockState();
            case LANTERN -> Blocks.LANTERN.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
        };
    }
}
