package com.gytrinket.gytrinket.core.entity.meteor;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 陨石外壳的深色石头材质池。
 * <p>
 * 这里刻意只用原版方块，不新增贴图；颜色整体偏深，避免陨石在孤岛暗色天空下发白。
 */
public final class MeteorPalette {

    private static final BlockState[] WEIGHTED;

    static {
        List<BlockState> states = new ArrayList<>(100);
        add(states, Blocks.DEEPSLATE, 18);
        add(states, Blocks.COBBLED_DEEPSLATE, 14);
        add(states, Blocks.TUFF, 14);
        add(states, Blocks.BASALT, 10);
        add(states, Blocks.SMOOTH_BASALT, 8);
        add(states, Blocks.BLACKSTONE, 10);
        add(states, Blocks.ANDESITE, 8);
        add(states, Blocks.STONE, 6);
        add(states, Blocks.CRACKED_DEEPSLATE_TILES, 7);
        add(states, Blocks.POLISHED_BLACKSTONE, 5);
        WEIGHTED = states.toArray(new BlockState[0]);
    }

    private MeteorPalette() {
    }

    private static void add(List<BlockState> states, Block block, int weight) {
        BlockState state = block.defaultBlockState();
        for (int i = 0; i < weight; i++) {
            states.add(state);
        }
    }

    /** 按哈希稳定选取材质，保证同一颗陨石每次渲染的方块一致。 */
    public static BlockState pick(long hash) {
        int index = (int) Math.floorMod(hash, (long) WEIGHTED.length);
        return WEIGHTED[index];
    }

    /** 给方块残骸随机挑一个材质。 */
    public static BlockState random(RandomSource random) {
        return WEIGHTED[random.nextInt(WEIGHTED.length)];
    }
}
