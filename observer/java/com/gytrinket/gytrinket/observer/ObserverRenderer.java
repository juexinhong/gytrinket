package com.gytrinket.gytrinket.observer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 世界观察的渲染/导出/统计工具。 */
public final class ObserverRenderer {

    private ObserverRenderer() {
    }

    public static String stateString(BlockState state) {
        ResourceLocation id = Registry.BLOCK.getKey(state.getBlock());
        StringBuilder out = new StringBuilder(id.toString());
        var properties = state.getProperties();
        if (!properties.isEmpty()) {
            out.append('[');
            boolean first = true;
            for (Property<?> property : properties) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(property.getName()).append('=').append(propertyName(state, property));
            }
            out.append(']');
        }
        return out.toString();
    }

    private static <T extends Comparable<T>> String propertyName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** 正交俯视：每列取 y0..y1 内最高的非空气方块，按地图色 + 高度打光。 */
    public static BufferedImage top(ServerLevel level, int x0, int z0, int x1, int z1, int y0, int y1, int scale) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        BufferedImage image = new BufferedImage(Math.max(1, width * scale), Math.max(1, height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState top = null;
                int topY = minY;
                for (int y = maxY; y >= minY; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        top = state;
                        topY = y;
                        break;
                    }
                }
                int rgb = 0x101010;
                if (top != null) {
                    // 1.20.1：defaultMapColor() 定义在 BlockBehaviour 上，MapColor.col 是 public final
                    int color = top.getMaterial().getColor().col;
                    float shade = maxY == minY ? 1.0f : 0.62f + 0.38f * ((topY - minY) / (float) (maxY - minY));
                    rgb = shade(color, shade);
                }
                graphics.setColor(new Color(rgb));
                graphics.fillRect((x - minX) * scale, (z - minZ) * scale, scale, scale);
            }
        }
        graphics.dispose();
        return image;
    }

    private static int shade(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r * factor));
        g = Math.min(255, (int) (g * factor));
        b = Math.min(255, (int) (b * factor));
        return (r << 16) | (g << 8) | b;
    }

    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 导出结构 NBT（含空气，与 /gytrinket dump 一致）。 */
    public static byte[] dump(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) throws IOException {
        int minX = Math.min(x0, x1);
        int minY = Math.min(y0, y1);
        int minZ = Math.min(z0, z1);
        int sizeX = Math.abs(x1 - x0) + 1;
        int sizeY = Math.abs(y1 - y0) + 1;
        int sizeZ = Math.abs(z1 - z0) + 1;
        StructureTemplate template = new StructureTemplate();
        // 1.20.1：fillFromWorld(Level, BlockPos, Vec3i, boolean, Block)
        template.fillFromWorld(level, new BlockPos(minX, minY, minZ), new Vec3i(sizeX, sizeY, sizeZ), false, null);
        CompoundTag tag = template.save(new CompoundTag());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 1.20.1 的 NbtIo.writeCompressed 除了 File 也接受 OutputStream，可直接写内存
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    /** 区域方块直方图（只统计非空气）。 */
    public static Map<String, Integer> scan(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, int limit) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        Map<String, Integer> histogram = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String id = Registry.BLOCK.getKey(state.getBlock()).toString();
                    histogram.merge(id, 1, Integer::sum);
                    if (++count >= limit) {
                        return histogram;
                    }
                }
            }
        }
        return histogram;
    }

    public static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> histogram) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(histogram.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        return list;
    }

    public static String histogramText(Map<String, Integer> histogram, int top) {
        int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder out = new StringBuilder("total=").append(total).append('\n');
        int i = 0;
        for (Map.Entry<String, Integer> entry : sorted(histogram)) {
            out.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
            if (++i >= top) {
                break;
            }
        }
        return out.toString();
    }
}
