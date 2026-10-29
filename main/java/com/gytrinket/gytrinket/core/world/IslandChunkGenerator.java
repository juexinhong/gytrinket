package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.core.world.terrain.BattlefieldTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 战场维度区块生成器。
 * <p>
 * 地形由 {@link BattlefieldTerrain} 计算：一片被反复轰炸的焦土，遍布多尺度弹坑、
 * 翻起的坑缘与罕见的巨型弹坑；原点附近保留一块相对平整的落脚地。
 * <p>
 * 本类只负责把地形数学「翻译」成方块：
 * <ul>
 *   <li>整列石头填充 + 基岩托底；</li>
 *   <li>按弹坑程度分层铺地表（坑底黑石/凝灰岩、坑坡生土、坑缘碎石、远处焦土）；</li>
 *   <li>只放置城镇 1（原版 {@code applyBiomeDecoration} 会顺带铺植被/矿物，这里只要城镇）。</li>
 * </ul>
 */
public class IslandChunkGenerator extends ChunkGenerator {

    public static final Codec<IslandChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
    ).apply(instance, IslandChunkGenerator::new));

    /** 用来从世界种子派生「战场种子」的固定键（同一世界恒定） */
    private static final ResourceLocation TERRAIN_SALT = new ResourceLocation("gytrinket", "battlefield");

    public IslandChunkGenerator(BiomeSource biomeSource) {
        // 1.19.2 的 ChunkGenerator 构造器要求 structure_set 注册表与覆盖集：
        // 本维度不用 structure_set（残塔/城镇由 StructureType 直接生成），给一个空注册表即可。
        super(emptyStructureSets(), java.util.Optional.empty(), biomeSource);
    }

    private static Registry<StructureSet> emptyStructureSets() {
        MappedRegistry<StructureSet> registry = new MappedRegistry<StructureSet>(Registry.STRUCTURE_SET_REGISTRY, Lifecycle.stable(), null);
        registry.freeze();
        return registry;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 由世界种子派生的战场地形（同一世界恒定，跨区块一致） */
    private BattlefieldTerrain terrain(RandomState random) {
        long salt = random.getOrCreateRandomFactory(TERRAIN_SALT).at(0, 0, 0).nextLong();
        return new BattlefieldTerrain(salt);
    }

    private int surfaceHeight(int x, int z, RandomState random) {
        return (int) Math.floor(terrain(random).height(x, z));
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BattlefieldTerrain terrain = terrain(random);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = (int) Math.floor(terrain.height(minX + x, minZ + z));
                for (int y = getMinY(); y <= top; y++) {
                    pos.set(minX + x, y, minZ + z);
                    chunk.setBlockState(pos, y == getMinY() ? bedrock : stone, false);
                }
            }
        }

        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BattlefieldTerrain terrain = terrain(random);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = minX + x;
                int wz = minZ + z;
                BattlefieldTerrain.Sample sample = terrain.sample(wx, wz);
                int top = (int) Math.floor(sample.height());
                if (top <= getMinY() + 1 || top >= getGenDepth() + getMinY() - 1) {
                    continue;
                }

                // 每列的确定性随机（与地形种子无关，仅用于材质变化）
                RandomSource colRandom = random.getOrCreateRandomFactory(TERRAIN_SALT)
                        .at(wx, 0, wz);
                int depth = 3 + colRandom.nextInt(3);

                BlockState surface = surfaceBlock(sample, colRandom);
                BlockState sub = subSurfaceBlock(sample, colRandom);

                for (int y = top; y > top - depth && y > getMinY(); y--) {
                    pos.set(wx, y, wz);
                    if (chunk.getBlockState(pos).is(Blocks.STONE)) {
                        chunk.setBlockState(pos, y == top ? surface : sub, false);
                    }
                }

                // 散落的碎石块，让地表更"战场"
                if (colRandom.nextDouble() < 0.06) {
                    pos.set(wx, top + 1, wz);
                    if (chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, colRandom.nextBoolean()
                                ? Blocks.COBBLESTONE.defaultBlockState()
                                : Blocks.ANDESITE.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    /**
     * 地表方块：整片世界是灰白的死寂石质，只在弹坑处露出焦黑的坑底。
     * 全部由石头类方块构成，没有泥土与草。
     */
    private BlockState surfaceBlock(BattlefieldTerrain.Sample sample, RandomSource random) {
        double crater = sample.crater();
        double rim = sample.rim();
        double zone = sample.zone();
        double roll = random.nextDouble();
        boolean island = zone < 0.6;
        boolean ring = zone >= 0.6 && zone < 1.6;

        if (crater > 0.62) {
            // 坑底：保留少量岩浆/黑石，让深弹坑有明显的烧灼感
            if (crater > 0.86 && roll < 0.10) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (roll < 0.28) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }
            if (roll < 0.52) {
                return Blocks.BASALT.defaultBlockState();
            }
            return Blocks.TUFF.defaultBlockState();
        }
        if (crater > 0.28) {
            // 坑坡：灰黑的碎屑岩
            if (roll < 0.30) {
                return Blocks.ANDESITE.defaultBlockState();
            }
            if (roll < 0.55) {
                return Blocks.GRAVEL.defaultBlockState();
            }
            return Blocks.TUFF.defaultBlockState();
        }
        if (rim > 0.14) {
            // 坑缘：翻起的灰白碎石
            if (roll < 0.30) {
                return Blocks.GRAVEL.defaultBlockState();
            }
            if (roll < 0.55) {
                return Blocks.COBBLESTONE.defaultBlockState();
            }
            if (roll < 0.55) {
                return Blocks.CALCITE.defaultBlockState();
            }
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (island) {
            // 孤岛高原：惨白的石灰岩与闪长岩
            if (roll < 0.30) {
                return Blocks.CALCITE.defaultBlockState();
            }
            if (roll < 0.60) {
                return Blocks.DIORITE.defaultBlockState();
            }
            if (roll < 0.72) {
                return Blocks.POLISHED_DIORITE.defaultBlockState();
            }
            if (roll < 0.84) {
                return Blocks.STONE.defaultBlockState();
            }
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (ring) {
            // 环带盆地：积灰的深色石
            if (roll < 0.38) {
                return Blocks.TUFF.defaultBlockState();
            }
            if (roll < 0.62) {
                return Blocks.ANDESITE.defaultBlockState();
            }
            if (roll < 0.78) {
                return Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            return Blocks.GRAVEL.defaultBlockState();
        }
        // 外围：灰白焦土与碎石
        if (roll < 0.32) {
            return Blocks.DIORITE.defaultBlockState();
        }
        if (roll < 0.56) {
            return Blocks.CALCITE.defaultBlockState();
        }
        if (roll < 0.76) {
            return Blocks.TUFF.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** 表层下方的过渡层：灰白岩层的断面 */
    private BlockState subSurfaceBlock(BattlefieldTerrain.Sample sample, RandomSource random) {
        double roll = random.nextDouble();
        if (sample.crater() > 0.5) {
            return roll < 0.5 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
        }
        if (sample.zone() < 0.6) {
            return roll < 0.45 ? Blocks.CALCITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState();
        }
        if (roll < 0.40) {
            return Blocks.TUFF.defaultBlockState();
        }
        if (roll < 0.70) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // 战场没有洞穴/峡谷雕刻
    }

    /**
     * 只放置结构，不铺植被和矿物。
     * <p>
     * 原版实现会连带跑一遍地形特征（草、树、矿脉），这里复刻其结构放置部分：
     * 按生成步骤遍历该步骤的结构，把与当前区块相交的 StructureStart 落到方块层。
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        Map<Integer, List<Structure>> byStep = registry.stream()
                .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));

        WorldgenRandom worldgenRandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = worldgenRandom.setDecorationSeed(level.getSeed(), sectionPos.minBlockX(), sectionPos.minBlockZ());
        BoundingBox writable = writableArea(chunk);

        int steps = GenerationStep.Decoration.values().length;
        for (int step = 0; step < steps; step++) {
            int index = 0;
            for (Structure structure : byStep.getOrDefault(step, Collections.emptyList())) {
                // 孤岛维度只允许城镇 1；残塔/高塔等程序化建筑不生成。
                if (!(structure instanceof Town1Structure)) {
                    index++;
                    continue;
                }
                if (structureManager.shouldGenerateStructures()) {
                    worldgenRandom.setFeatureSeed(decorationSeed, index, step);
                    for (StructureStart start : structureManager.startsForStructure(sectionPos, structure)) {
                        try {
                            start.placeInChunk(level, structureManager, this, worldgenRandom, writable, chunkPos);
                        } catch (Exception exception) {
                            com.gytrinket.gytrinket.gytrinket.LOGGER.error(
                                    "Failed to place structure piece for {} at {}", structure, chunkPos, exception);
                        }
                    }
                }
                index++;
            }
        }
        // 城镇 1 最后放置：空气/实体方块覆盖同区块内的地形与其它结构，防止被覆盖
        TownTemplatePlacer.placeChunk(level, chunkPos);
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelHeightAccessor height = chunk.getHeightAccessorForGeneration();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        return new BoundingBox(minX, height.getMinBuildHeight() + 1, minZ,
                minX + 15, height.getMaxBuildHeight() - 1, minZ + 15);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 交由结构或后续机制决定刷怪
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return surfaceHeight(x, z, random) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int top = surfaceHeight(x, z, random);
        BlockState[] states = new BlockState[Math.max(0, top - getMinY() + 1)];
        Arrays.fill(states, Blocks.STONE.defaultBlockState());
        return new NoiseColumn(getMinY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        BattlefieldTerrain.Sample sample = terrain(random).sample(pos.getX(), pos.getZ());
        info.add(String.format("gytrinket:battlefield  crater=%.2f rim=%.2f ejecta=%.2f",
                sample.crater(), sample.rim(), sample.ejecta()));
    }
}
