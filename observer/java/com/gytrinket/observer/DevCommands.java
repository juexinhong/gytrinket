package com.gytrinket.observer;

import com.gytrinket.gytrinket.core.projectile.MultipartHitScan;
import com.gytrinket.gytrinket.core.world.terrain.RuinedTowerBlueprint;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 模组命令。
 * <p>
 * 除了玩家用的 {@code /gytrinket resetlevel}，还带三个开发辅助命令，
 * 让我在没有游戏客户端的情况下也能检查和迭代建筑：
 * <ul>
 *   <li>{@code /gytrinket render <from> <to> [pxPerBlock]}：把区域软件渲染成 PNG；</li>
 *   <li>{@code /gytrinket dump <from> <to> <name>}：把区域存成结构 NBT；</li>
 *   <li>{@code /gytrinket build}：在命令位置砌出手工塔模型。</li>
 * </ul>
 */
public final class DevCommands {

    private DevCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        try {
        dispatcher.register(Commands.literal("gytrinket")

                .then(Commands.literal("render")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> render(ctx.getSource().getLevel(),
                                                BlockPosArgument.getBlockPos(ctx, "from"),
                                                BlockPosArgument.getBlockPos(ctx, "to"), 4.0))
                                        .then(Commands.argument("px", DoubleArgumentType.doubleArg(0.5, 12.0))
                                                .executes(ctx -> render(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                                        BlockPosArgument.getBlockPos(ctx, "to"),
                                                        DoubleArgumentType.getDouble(ctx, "px"))
                                                )))))
                .then(Commands.literal("top")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> renderTop(ctx.getSource().getLevel(),
                                                BlockPosArgument.getBlockPos(ctx, "from"),
                                                BlockPosArgument.getBlockPos(ctx, "to"), 2.0))
                                        .then(Commands.argument("px", DoubleArgumentType.doubleArg(0.5, 8.0))
                                                .executes(ctx -> renderTop(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                                        BlockPosArgument.getBlockPos(ctx, "to"),
                                                        DoubleArgumentType.getDouble(ctx, "px"))
                                                )))))
                .then(Commands.literal("view")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("cam", BlockPosArgument.blockPos())
                                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                        .executes(ctx -> view(ctx.getSource().getLevel(),
                                                                BlockPosArgument.getBlockPos(ctx, "from"),
                                                                BlockPosArgument.getBlockPos(ctx, "to"),
                                                                net.minecraft.world.phys.Vec3.atCenterOf(
                                                                        BlockPosArgument.getBlockPos(ctx, "cam")),
                                                                net.minecraft.world.phys.Vec3.atCenterOf(
                                                                        BlockPosArgument.getBlockPos(ctx, "target")),
                                                                960))
                                                        .then(Commands.argument("width",
                                                                        IntegerArgumentType.integer(160, 1920))
                                                                .executes(ctx -> view(ctx.getSource().getLevel(),
                                                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                                                        BlockPosArgument.getBlockPos(ctx, "to"),
                                                                        net.minecraft.world.phys.Vec3.atCenterOf(
                                                                                BlockPosArgument.getBlockPos(ctx, "cam")),
                                                                        net.minecraft.world.phys.Vec3.atCenterOf(
                                                                                BlockPosArgument.getBlockPos(ctx, "target")),
                                                                        IntegerArgumentType.getInteger(ctx, "width"))
                                                                )))))))                .then(Commands.literal("dump")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ctx -> dump(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                                        BlockPosArgument.getBlockPos(ctx, "to"),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        ctx.getSource().getServer().getServerDirectory())
                                                )))))
                .then(Commands.literal("samples")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int[] count = {0};
                            new RuinedTowerBlueprint(0L).buildSamples((x, y, z, m) -> {
                                ctx.getSource().getLevel().setBlock(
                                        net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()).offset(x, y, z),
                                        blockFor(m), 3);
                                count[0]++;
                            });
                            gytrinket.LOGGER.info("[samples] {} blocks", count[0]);
                            return count[0];
                        }))
                .then(Commands.literal("build")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> build(ctx.getSource().getLevel(),
                                BlockPos.containing(ctx.getSource().getPosition()))))
                .then(Commands.literal("buildtower")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> buildTowerOnly(ctx.getSource().getLevel(),
                                BlockPos.containing(ctx.getSource().getPosition()))))
                .then(Commands.literal("hittest")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", Vec3Argument.vec3())
                                .then(Commands.argument("to", Vec3Argument.vec3())
                                        .executes(ctx -> hitTest(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "from"),
                                                Vec3Argument.getVec3(ctx, "to"))))))
                .then(Commands.literal("parts")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> dumpParts(ctx.getSource(), 64.0D))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                .executes(ctx -> dumpParts(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "radius")))))

        );
        } catch (Throwable t) {
            gytrinket.LOGGER.error("[dev] command registration failed", t);
        }
    }

    // ------------------------------------------------------------------
    // 多体节命中检测诊断：与无人机子弹/爆破弹共用 MultipartHitScan
    // ------------------------------------------------------------------
    /**
     * 用弹射物同一套算法沿线段找第一个碰撞箱，打印命中的是父实体还是体节。
     * <p>
     * HTTP {@code /command} 只返回命令结果值，因此命中返回 1、未命中返回 0，
     * 细节写进日志。用于验收多体节生物（末影龙/娜迦等）的弹道判定。
     */
    private static int hitTest(CommandSourceStack source, Vec3 from, Vec3 to) {
        MultipartHitScan.Hit hit = MultipartHitScan.findFirstOnPath(
                source.getLevel(), from, to, MultipartHitScan.DEFAULT_HITBOX_INFLATE, e -> true);
        if (hit == null) {
            gytrinket.LOGGER.info("[hittest] {} -> {} : no hit", from, to);
            source.sendSuccess(() -> Component.literal("[hittest] no hit"), false);
            return 0;
        }
        String parentClass = hit.target().getClass().getSimpleName();
        String boxClass = hit.hitbox().getClass().getSimpleName();
        String kind = hit.hitbox() instanceof PartEntity<?> ? "part" : "parent";
        String text = String.format("[hittest] %s -> %s : %s %s (parent %s) pos=%.3f/%.3f/%.3f",
                from, to, kind, boxClass, parentClass,
                hit.position().x, hit.position().y, hit.position().z);
        gytrinket.LOGGER.info(text);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * 列出附近多体节实体的父实体与全部体节碰撞箱（诊断用）。
     */
    private static int dumpParts(CommandSourceStack source, double radius) {
        Vec3 pos = source.getPosition();
        List<net.minecraft.world.entity.Entity> parents = source.getLevel().getEntities(
                (net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(pos, pos).inflate(radius),
                e -> {
                    net.neoforged.neoforge.entity.PartEntity<?>[] parts = e.getParts();
                    return parts != null && parts.length > 0;
                });
        gytrinket.LOGGER.info("[parts] {} -> {} multipart entities:", pos, parents.size());
        for (net.minecraft.world.entity.Entity parent : parents) {
            gytrinket.LOGGER.info("[parts] parent {} id={} pos={} bb={} pickable={} alive={}",
                    parent.getClass().getSimpleName(), parent.getId(), parent.position(),
                    parent.getBoundingBox(), parent.isPickable(), parent.isAlive());
            int index = 0;
            for (net.minecraft.world.entity.Entity part : parent.getParts()) {
                gytrinket.LOGGER.info("  [parts] #{} {} id={} pos={} bb={} alive={} pickable={} removed={}",
                        index++, part.getClass().getSimpleName(), part.getId(), part.position(),
                        part.getBoundingBox(), part.isAlive(), part.isPickable(), part.isRemoved());
            }
        }
        return parents.size();
    }

    // ------------------------------------------------------------------
    // 透视体素渲染（DDA 射线步进 + 逐像素贴图采样）
    // ------------------------------------------------------------------
    private static final java.util.Map<net.minecraft.world.level.block.Block, int[]> TEX_CACHE =
            new java.util.HashMap<>();

    private static int[] textureOf(net.minecraft.world.level.block.Block block) {
        if (TEX_CACHE.containsKey(block)) {
            return TEX_CACHE.get(block);
        }
        int[] tex = null;
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if ("minecraft".equals(id.getNamespace())) {
            byte[] png = readAsset("assets/minecraft/textures/block/" + id.getPath() + ".png");
            if (png != null) {
                try {
                    java.awt.image.BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(png));
                    if (img != null) {
                        tex = new int[256];
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                int px = img.getRGB(x * img.getWidth() / 16, y * img.getHeight() / 16);
                                tex[y * 16 + x] = px & 0xFFFFFF;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    tex = null;
                }
            }
        }
        TEX_CACHE.put(block, tex);
        return tex;
    }

    private static java.util.zip.ZipFile clientAssets;

    private static byte[] readAsset(String path) {
        try (java.io.InputStream in = DevCommands.class.getResourceAsStream("/" + path)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {
            // 继续尝试 jar
        }
        if (clientAssets == null) {
            clientAssets = findClientAssets();
        }
        if (clientAssets != null) {
            java.util.zip.ZipEntry entry = clientAssets.getEntry(path);
            if (entry != null) {
                try (java.io.InputStream in = clientAssets.getInputStream(entry)) {
                    return in.readAllBytes();
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static java.util.zip.ZipFile findClientAssets() {
        File[] candidates = {
                new File("../build/moddev/artifacts"),
                new File("build/moddev/artifacts")
        };
        for (File dir : candidates) {
            File[] jars = dir.listFiles((d, n) -> n.contains("client-extra") && n.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                try {
                    return new java.util.zip.ZipFile(jars[0]);
                } catch (Exception ignored) {
                    // 继续找
                }
            }
        }
        return null;
    }
    private static int tintFor(net.minecraft.world.level.block.Block block) {
        String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (p.contains("grass") || p.contains("leaves") || p.contains("vine") || p.contains("fern")) {
            return 0x86C05A;
        }
        if (p.contains("water")) {
            return 0x3F76E4;
        }
        return 0xFFFFFF;
    }
    private static int view(ServerLevel level, BlockPos from, BlockPos to,
                            net.minecraft.world.phys.Vec3 cam, net.minecraft.world.phys.Vec3 target, int width) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;

        net.minecraft.world.level.block.Block[] palette = new net.minecraft.world.level.block.Block[8192];
        int paletteSize = 1;
        java.util.Map<net.minecraft.world.level.block.Block, Integer> blockIndex = new java.util.HashMap<>();
        byte[] grid = new byte[sx * sy * sz];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    pos.set(minX + x, minY + y, minZ + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    net.minecraft.world.level.block.Block block = state.getBlock();
                    Integer idx = blockIndex.get(block);
                    if (idx == null) {
                        if (paletteSize >= palette.length) {
                            continue;
                        }
                        idx = paletteSize;
                        palette[paletteSize++] = block;
                        blockIndex.put(block, idx);
                    }
                    grid[(y * sz + z) * sx + x] = (byte) (idx & 0xFF);
                }
            }
        }

        int height = Math.max(1, width * 9 / 16);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        double dx0 = target.x - cam.x;
        double dy0 = target.y - cam.y;
        double dz0 = target.z - cam.z;
        double len = Math.sqrt(dx0 * dx0 + dy0 * dy0 + dz0 * dz0);
        dx0 /= len;
        dy0 /= len;
        dz0 /= len;
        double rx = -dz0;
        double ry = 0.0;
        double rz = dx0;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-6) {
            rx = 1.0;
            rz = 0.0;
            rl = 1.0;
        }
        rx /= rl;
        rz /= rl;
        double ux = ry * dz0 - rz * dy0;
        double uy = rz * dx0 - rx * dz0;
        double uz = rx * dy0 - ry * dx0;

        double tan = Math.tan(Math.toRadians(35.0));
        double aspect = width / (double) height;
        int skyTop = 0x2A3350;
        int skyBottom = 0x8A93A6;
        double[] hit = new double[5];
        double ox = cam.x - minX;
        double oy = cam.y - minY;
        double oz = cam.z - minZ;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double nx = (2.0 * (px + 0.5) / width - 1.0) * aspect * tan;
                double ny = (1.0 - 2.0 * (py + 0.5) / height) * tan;
                double dx = dx0 + rx * nx + ux * ny;
                double dy = dy0 + ry * nx + uy * ny;
                double dz = dz0 + rz * nx + uz * ny;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl;
                dy /= dl;
                dz /= dl;

                int rgb;
                if (!trace(grid, sx, sy, sz, ox, oy, oz, dx, dy, dz, hit)) {
                    rgb = blend(skyTop, skyBottom, (double) py / height);
                } else {
                    int face = (int) hit[0];
                    int idx = (int) hit[3];
                    double shade = face == 1 ? 1.0 : (face == 0 ? 0.80 : 0.64);
                    net.minecraft.world.level.block.Block block = palette[idx];
                    int[] tex = textureOf(block);
                    int base;
                    if (tex != null) {
                        int tx = Math.min(15, Math.max(0, (int) (hit[1] * 16.0)));
                        int ty = Math.min(15, Math.max(0, (int) ((1.0 - hit[2]) * 16.0)));
                        base = tex[ty * 16 + tx];
                    } else {
                        base = 0x9A9A9A;
                    }
                    int tint = tintFor(block);
                    rgb = scale(base, shade);
                    rgb = scale(rgb, ((tint >> 16 & 0xFF) / 255.0 + (tint >> 8 & 0xFF) / 255.0 + (tint & 0xFF) / 255.0) / 3.0);
                    int tr = (int) (((rgb >> 16) & 0xFF) * ((tint >> 16 & 0xFF) / 255.0));
                    int tg = (int) (((rgb >> 8) & 0xFF) * ((tint >> 8 & 0xFF) / 255.0));
                    int tb = (int) ((rgb & 0xFF) * ((tint & 0xFF) / 255.0));
                    rgb = (tr << 16) | (tg << 8) | tb;
                    rgb = blend(rgb, skyBottom, 0.22);
                }
                img.setRGB(px, py, rgb);
            }
        }
        g.dispose();

        File out = new File("gytrinket_view.png");
        try {
            ImageIO.write(img, "png", out);
            gytrinket.LOGGER.info("[view] {}x{} -> {}", width, height, out.getAbsolutePath());
        } catch (Exception e) {
            gytrinket.LOGGER.error("[view] failed", e);
            return 0;
        }
        return 1;
    }

    /** DDA：命中后写回 face / u / v / paletteIndex */
    private static boolean trace(byte[] grid, int sx, int sy, int sz,
                                 double ox, double oy, double oz,
                                 double dx, double dy, double dz, double[] hit) {
        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        int iz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double invX = dx == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
        double invY = dy == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
        double invZ = dz == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : ((stepX > 0 ? ix + 1 - ox : ox - ix) * invX);
        double tMaxY = dy == 0 ? Double.MAX_VALUE : ((stepY > 0 ? iy + 1 - oy : oy - iy) * invY);
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? iz + 1 - oz : oz - iz) * invZ);

        int face = 1;
        double enterT = 0.0;
        for (int i = 0; i < 8000; i++) {
            if (ix >= 0 && ix < sx && iy >= 0 && iy < sy && iz >= 0 && iz < sz) {
                int idx = grid[(iy * sz + iz) * sx + ix] & 0xFF;
                if (idx != 0) {
                    double hx = ox + dx * enterT;
                    double hy = oy + dy * enterT;
                    double hz = oz + dz * enterT;
                    hit[0] = face;
                    hit[3] = idx;
                    if (face == 0) {
                        hit[1] = hz - Math.floor(hz);
                        hit[2] = hy - Math.floor(hy);
                    } else if (face == 1) {
                        hit[1] = hx - Math.floor(hx);
                        hit[2] = hz - Math.floor(hz);
                    } else {
                        hit[1] = hx - Math.floor(hx);
                        hit[2] = hy - Math.floor(hy);
                    }
                    return true;
                }
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                enterT = tMaxX;
                ix += stepX;
                tMaxX += invX;
                face = 0;
            } else if (tMaxY < tMaxZ) {
                enterT = tMaxY;
                iy += stepY;
                tMaxY += invY;
                face = 1;
            } else {
                enterT = tMaxZ;
                iz += stepZ;
                tMaxZ += invZ;
                face = 2;
            }
            if (enterT > 400) {
                break;
            }
        }
        return false;
    }
    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * k);
        int gg = (int) Math.min(255, ((rgb >> 8) & 0xFF) * k);
        int b = (int) Math.min(255, (rgb & 0xFF) * k);
        return (r << 16) | (gg << 8) | b;
    }

    private static int blend(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bb2 = (int) (ab + (bb - ab) * t);
        return (r << 16) | (gg << 8) | bb2;
    }
    // ------------------------------------------------------------------
    // 正交俯视：每列取最高方块，按高度打光
    // ------------------------------------------------------------------
    private static int renderTop(ServerLevel level, BlockPos from, BlockPos to, double px) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int sx = maxX - minX + 1;
        int sz = maxZ - minZ + 1;
        int sc = Math.max(1, (int) Math.round(px));

        BufferedImage img = new BufferedImage(sx * sc, sz * sc, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int drawn = 0;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int topY = Integer.MIN_VALUE;
                BlockState topState = null;
                for (int y = maxY; y >= minY; y--) {
                    pos.set(minX + x, y, minZ + z);
                    BlockState st = level.getBlockState(pos);
                    if (!st.isAir()) {
                        topY = y;
                        topState = st;
                        break;
                    }
                }
                int rgb;
                if (topState == null) {
                    rgb = 0x1E1E22;
                } else {
                    Color c = mapColor(level, pos, topState);
                    double rel = (maxY == minY) ? 1.0 : (topY - minY) / (double) (maxY - minY);
                    double k = 0.70 + 0.42 * rel;
                    rgb = shade(new Color(Math.min(255, c.getRed()), Math.min(255, c.getGreen()),
                            Math.min(255, c.getBlue())), k).getRGB() & 0xFFFFFF;
                    drawn++;
                }
                g.setColor(new Color(rgb));
                g.fillRect(x * sc, z * sc, sc, sc);
            }
        }
        g.dispose();

        File out = new File("gytrinket_top.png");
        try {
            ImageIO.write(img, "png", out);
            gytrinket.LOGGER.info("[top] {}x{} -> {} ({})", img.getWidth(), img.getHeight(),
                    out.getAbsolutePath(), drawn);
        } catch (Exception e) {
            gytrinket.LOGGER.error("[top] failed", e);
            return 0;
        }
        return drawn;
    }

    // ------------------------------------------------------------------
    // 软件渲染：把世界里的方块投影成等轴测 PNG
    // ------------------------------------------------------------------
    private static int render(ServerLevel level, BlockPos from, BlockPos to, double px) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        List<Voxel> voxels = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    voxels.add(new Voxel(x - minX, y - minY, z - minZ, mapColor(level, pos, state)));
                }
            }
        }
        if (voxels.isEmpty()) {
            gytrinket.LOGGER.warn("[render] empty region");
            return 0;
        }
        voxels.sort(Comparator.comparingInt((Voxel v) -> v.x + v.z + v.y));

        int tw = Math.max(2, (int) Math.round(px));
        int th = Math.max(1, tw / 2);
        int ph = Math.max(1, (int) Math.round(px * 0.9));
        int w = (maxX - minX + maxZ - minZ + 4) * tw / 2 + 40;
        int h = (maxX - minX + maxZ - minZ + 4) * th / 2 + (maxY - minY + 4) * ph + 40;

        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(38, 38, 42));
        g.fillRect(0, 0, w, h);
        int ox = 20 + (maxZ - minZ + 2) * tw / 2;
        int oy = 20 + (maxY - minY + 2) * ph;

        for (Voxel v : voxels) {
            int cx = ox + (v.x - v.z) * tw / 2;
            int cy = oy - (v.x + v.z) * th / 2 - v.y * ph;
            g.setColor(shade(v.color, 1.10));
            g.fillPolygon(new int[]{cx, cx + tw / 2, cx, cx - tw / 2},
                    new int[]{cy - th / 2, cy, cy + th / 2, cy}, 4);
            g.setColor(shade(v.color, 0.78));
            g.fillPolygon(new int[]{cx - tw / 2, cx, cx, cx - tw / 2},
                    new int[]{cy, cy + th / 2, cy + th / 2 + ph, cy + ph}, 4);
            g.setColor(shade(v.color, 0.60));
            g.fillPolygon(new int[]{cx + tw / 2, cx, cx, cx + tw / 2},
                    new int[]{cy, cy + th / 2, cy + th / 2 + ph, cy + ph}, 4);
        }
        g.dispose();

        File out = new File("gytrinket_render.png");
        try {
            ImageIO.write(img, "png", out);
            gytrinket.LOGGER.info("[render] {} voxels -> {}", voxels.size(), out.getAbsolutePath());
        } catch (Exception e) {
            gytrinket.LOGGER.error("[render] failed", e);
            return 0;
        }
        return voxels.size();
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, Integer> TEXTURE_CACHE =
            new java.util.HashMap<>();

    private static int blockColor(ServerLevel level, BlockPos pos, BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        Integer cached = TEXTURE_CACHE.get(block);
        if (cached == null) {
            Integer avg = averageTexture(block);
            cached = avg != null ? avg : (state.getMapColor(level, pos).col & 0xFFFFFF);
            TEXTURE_CACHE.put(block, cached);
        }
        return cached;
    }

    /** 从客户端资源里读 16×16 贴图并取平均色；读不到时回退到地图颜色 */
    private static Integer averageTexture(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }
        String path = "/assets/minecraft/textures/block/" + id.getPath() + ".png";
        try (java.io.InputStream in = DevCommands.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            java.awt.image.BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return null;
            }
            long r = 0;
            long g = 0;
            long b = 0;
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int p = img.getRGB(x, y);
                    if (((p >>> 24) & 0xFF) < 128) {
                        continue;
                    }
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                    n++;
                }
            }
            if (n == 0) {
                return null;
            }
            return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Color mapColor(ServerLevel level, BlockPos pos, BlockState state) {
        int rgb = blockColor(level, pos, state);
        int r = (rgb >> 16) & 0xFF;
        int gg = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.min(255, (int) (r * 1.22) + 14);
        gg = Math.min(255, (int) (gg * 1.22) + 14);
        b = Math.min(255, (int) (b * 1.22) + 14);
        return new Color(r, gg, b);
    }

    private static Color shade(Color c, double k) {
        return new Color(Math.min(255, (int) (c.getRed() * k)),
                Math.min(255, (int) (c.getGreen() * k)),
                Math.min(255, (int) (c.getBlue() * k)));
    }

    // ------------------------------------------------------------------
    // 导出结构 NBT（服务端直接写，无 48 格限制）
    // ------------------------------------------------------------------
    private static int dump(ServerLevel level, BlockPos from, BlockPos to, String name, java.nio.file.Path dir) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(minX, minY, minZ),
                new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), false, null);
        CompoundTag tag = template.save(new CompoundTag());
        java.nio.file.Path out = dir.toAbsolutePath().resolve("gytrinket_" + name + ".nbt");
        try {
            java.nio.file.Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            NbtIo.writeCompressed(tag, out);
            gytrinket.LOGGER.info("[dump] wrote {} ({}x{}x{})", out.toAbsolutePath(),
                    maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        } catch (Exception e) {
            gytrinket.LOGGER.error("[dump] failed", e);
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // 原地建造
    // ------------------------------------------------------------------
    private static int build(ServerLevel level, BlockPos origin) {
        int[] count = {0};
        new RuinedTowerBlueprint(0L).build((x, y, z, m) -> {
            level.setBlock(origin.offset(x, y, z), blockFor(m), 3);
            count[0]++;
        });
        gytrinket.LOGGER.info("[build] {} blocks at {}", count[0], origin);
        return count[0];
    }

    /** 只建塔体，不建自带的程序城（用于把塔重新砌一遍） */
    private static int buildTowerOnly(ServerLevel level, BlockPos origin) {
        int[] count = {0};
        RuinedTowerBlueprint bp = new RuinedTowerBlueprint(0L);
        bp.includeCity = false;
        bp.build((x, y, z, m) -> {
            level.setBlock(origin.offset(x, y, z), blockFor(m), 3);
            count[0]++;
        });
        gytrinket.LOGGER.info("[buildtower] {} blocks at {}", count[0], origin);
        return count[0];
    }

    private static BlockState blockFor(RuinedTowerBlueprint.Material m) {
        return switch (m) {
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

    private record Voxel(int x, int y, int z, Color color) {
    }
}
