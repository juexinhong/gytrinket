package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.core.world.terrain.BattlefieldTerrain;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * 城镇 1 模板的直接放置器（绕开原版 structure set / generate_features 开关）。
 * <p>
 * 孤岛维度必须无条件生成城镇 1：这里在 {@link IslandChunkGenerator#applyBiomeDecoration}
 * 里按区块放置模板方块。运行时读取紧凑缓存 {@code town1.bin}（稠密调色板索引，
 * 8.85 MB，含空气；结构文件 {@code town1.nbt} 仍包含空气、剔除草与泥土）。
 * 每个区块只遍历本区块的 16×16×253 体素，不解析整份 NBT。
 */
public final class TownTemplatePlacer {

    /** 模板最小角（x/z=-93，地面层对齐孤岛台面 y=32） */
    public static final BlockPos ORIGIN =
            new BlockPos(-93, BattlefieldTerrain.PAD_TOP, -93);

    private static final String RESOURCE = "/data/gytrinket/structure/town1.bin";

    private static int sizeX, sizeY, sizeZ;
    private static BlockState[] palette;
    private static byte[] dense;

    private TownTemplatePlacer() {
    }

    /** 按区块放置城镇 1 的当前区块部分；不在城镇范围内的区块直接跳过。 */
    public static void placeChunk(WorldGenLevel level, ChunkPos chunkPos) {
        if (dense == null) {
            load(level);
        }
        if (dense == null) {
            return;
        }
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        if (minX + 15 < ORIGIN.getX() || minX > ORIGIN.getX() + sizeX - 1
                || minZ + 15 < ORIGIN.getZ() || minZ > ORIGIN.getZ() + sizeZ - 1) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sizeY; y++) {
            int wy = ORIGIN.getY() + y;
            for (int z = 0; z < 16; z++) {
                int wz = minZ + z;
                int lz = wz - ORIGIN.getZ();
                if (lz < 0 || lz >= sizeZ) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    int wx = minX + x;
                    int lx = wx - ORIGIN.getX();
                    if (lx < 0 || lx >= sizeX) {
                        continue;
                    }
                    int index = (y * sizeZ + lz) * sizeX + lx;
                    int value = dense[index] & 0xFF;
                    if (value == 255) {
                        // 结构未覆盖（草/泥土剔除处）：保留孤岛地形
                        continue;
                    }
                    BlockState state = palette[value];
                    pos.set(wx, wy, wz);
                    // 空气也占空间：已是空气则跳过，否则清成空气（压掉地形/其它结构）
                    if (state.isAir()) {
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, state, 2);
                        }
                        continue;
                    }
                    level.setBlock(pos, state, 2);
                }
            }
        }
    }

    private static synchronized void load(WorldGenLevel level) {
        if (dense != null) {
            return;
        }
        try (InputStream raw = TownTemplatePlacer.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                com.gytrinket.gytrinket.gytrinket.LOGGER.error("[town1] runtime cache missing: {}", RESOURCE);
                return;
            }
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'T' || magic[1] != '1' || magic[2] != 'B' || magic[3] != '1') {
                    throw new IOException("bad town1.bin magic");
                }
                int minX = in.readInt();
                int minY = in.readInt();
                int minZ = in.readInt();
                sizeX = in.readInt();
                sizeY = in.readInt();
                sizeZ = in.readInt();
                // minY 是模板在源世界的底层（-60），放置时用 ORIGIN.y=32 对齐孤岛台面
                if (minX != ORIGIN.getX() || minZ != ORIGIN.getZ()) {
                    throw new IOException("town1.bin x/z origin mismatch: " + minX + "," + minZ);
                }
                int paletteCount = in.readInt();
                BlockState[] states = new BlockState[paletteCount];
                net.minecraft.core.Registry<net.minecraft.world.level.block.Block> blockLookup =
                level.registryAccess().registryOrThrow(Registry.BLOCK_REGISTRY);
                for (int i = 0; i < paletteCount; i++) {
                    String name = readString(in);
                    int props = in.readUnsignedShort();
                    StringBuilder text = new StringBuilder(name);
                    if (props > 0) {
                        text.append('[');
                        for (int p = 0; p < props; p++) {
                            if (p > 0) {
                                text.append(',');
                            }
                            text.append(readString(in)).append('=').append(readString(in));
                        }
                        text.append(']');
                    }
                    states[i] = BlockStateParser.parseForBlock(blockLookup, text.toString(), false).blockState();
                }
                int total = sizeX * sizeY * sizeZ;
                byte[] data = new byte[total];
                in.readFully(data);
                palette = states;
                dense = data;
                com.gytrinket.gytrinket.gytrinket.LOGGER.info(
                        "[town1] runtime cache ready: {}x{}x{}, {} palette, {} bytes",
                        sizeX, sizeY, sizeZ, paletteCount, total);
            }
        } catch (Exception exception) {
            dense = null;
            com.gytrinket.gytrinket.gytrinket.LOGGER.error("[town1] failed to load runtime cache", exception);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
