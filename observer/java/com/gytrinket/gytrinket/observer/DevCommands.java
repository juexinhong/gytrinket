package com.gytrinket.gytrinket.observer;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.world.terrain.RuinedTowerBlueprint;
import com.gytrinket.gytrinket.engine.perf.TickProfiler;
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
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 模组命令。
 * <p>
 * 除了玩家用的 {@code /gytrinket resetlevel}，还带几个开发辅助命令，
 * 让我在没有游戏客户端的情况下也能检查和迭代建筑：
 * <ul>
 *   <li>{@code /gytrinket render <from> <to> [pxPerBlock]}：把区域软件渲染成等轴测 PNG；</li>
 *   <li>{@code /gytrinket top <from> <to> [pxPerBlock]}：正交俯视投影；</li>
 *   <li>{@code /gytrinket view <from> <to> <cam> <target> [width]}：指定相机与目标做透视渲染；</li>
 *   <li>{@code /gytrinket dump <from> <to> <name>}：把区域存成结构 NBT；</li>
 *   <li>{@code /gytrinket samples}：在命令位置砌出样品房；</li>
 *   <li>{@code /gytrinket build}：在命令位置砌出手工塔模型；</li>
 *   <li>{@code /gytrinket buildtower}：只砌塔体，不砌程序城。</li>
 * </ul>
 * 本文件由 1.21.1/NeoForge 版本移植到 1.20.1/Forge：
 * 事件总线注解、{@code NbtIo}、{@code MinecraftServer#getServerDirectory()} 等
 * 均已按 1.20.1 的签名适配，渲染仍使用 AWT（服务端同样可用）。
 */
public final class DevCommands {

    private DevCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("gytrinket")

                .then(Commands.literal("render")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> render(ctx.getSource().getLevel(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"), 4.0))
                                        .then(Commands.argument("px", DoubleArgumentType.doubleArg(0.5, 12.0))
                                                .executes(ctx -> render(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                        DoubleArgumentType.getDouble(ctx, "px"))
                                                )))))
                .then(Commands.literal("top")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> renderTop(ctx.getSource().getLevel(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"), 2.0))
                                        .then(Commands.argument("px", DoubleArgumentType.doubleArg(0.5, 8.0))
                                                .executes(ctx -> renderTop(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                        DoubleArgumentType.getDouble(ctx, "px"))
                                                )))))
                .then(Commands.literal("view")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("cam", BlockPosArgument.blockPos())
                                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                        .executes(ctx -> view(ctx.getSource().getLevel(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                                Vec3.atCenterOf(
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "cam")),
                                                                Vec3.atCenterOf(
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "target")),
                                                                960))
                                                        .then(Commands.argument("width",
                                                                        IntegerArgumentType.integer(160, 1920))
                                                                .executes(ctx -> view(ctx.getSource().getLevel(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                                        Vec3.atCenterOf(
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "cam")),
                                                                        Vec3.atCenterOf(
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "target")),
                                                                        IntegerArgumentType.getInteger(ctx, "width"))
                                                                )))))))
                .then(Commands.literal("dump")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                // 1.20.1 的 MinecraftServer#getServerDirectory() 返回 File
                                                .executes(ctx -> dump(ctx.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        ctx.getSource().getServer().getServerDirectory().toPath())
                                                )))))
                .then(Commands.literal("samples")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int[] count = {0};
                            new RuinedTowerBlueprint(0L).buildSamples((x, y, z, m) -> {
                                ctx.getSource().getLevel().setBlock(
                                        new BlockPos(ctx.getSource().getPosition()).offset(x, y, z),
                                        blockFor(m), 3);
                                count[0]++;
                            });
                            gytrinket.LOGGER.info("[samples] {} blocks", count[0]);
                            return count[0];
                        }))
                .then(Commands.literal("build")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> build(ctx.getSource().getLevel(),
                                new BlockPos(ctx.getSource().getPosition()))))
                .then(Commands.literal("buildtower")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> buildTowerOnly(ctx.getSource().getLevel(),
                                new BlockPos(ctx.getSource().getPosition()))))
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
                .then(Commands.literal("wave")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> sendEnergyWave(ctx.getSource(), 8.0D, 1))
                        .then(Commands.argument("length", DoubleArgumentType.doubleArg(1.0D, 20.0D))
                                .executes(ctx -> sendEnergyWave(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "length"), 1))
                                .then(Commands.argument("color", IntegerArgumentType.integer(0, 1))
                                        .executes(ctx -> sendEnergyWave(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "length"),
                                                IntegerArgumentType.getInteger(ctx, "color"))))))
                .then(Commands.literal("construct")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> spawnConstruct(ctx.getSource(), null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> spawnConstruct(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("constructclear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> clearConstructs(ctx.getSource())))
                .then(Commands.literal("store")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> storeItems(ctx.getSource(), new String[0]))
                        .then(Commands.argument("items", StringArgumentType.greedyString())
                                .executes(ctx -> storeItems(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "items").split("\\s+")))))
                .then(Commands.literal("storeclear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> clearStore(ctx.getSource())))
                .then(Commands.literal("storelist")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> listStore(ctx.getSource())))
                .then(Commands.literal("defs")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dump")
                                .executes(ctx -> dumpDefs(ctx.getSource())))
                        .then(Commands.literal("rev")
                                .executes(ctx -> printDefsRev(ctx.getSource()))))
                .then(Commands.literal("perf")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(ctx -> startPerf(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopPerf(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetPerf(ctx.getSource())))
                        .then(Commands.literal("dump")
                                .executes(ctx -> dumpPerf(ctx.getSource()))))
        );
    }

    // ------------------------------------------------------------------
    // 能量波特效验收：直接给所有玩家发 EnergyWaveExplosionPacket（不造成伤害）
    // ------------------------------------------------------------------
    private static int sendEnergyWave(CommandSourceStack source, double length, int colorType) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        Vec3 look = player.getLookAngle();
        Vec3 center = player.position().add(0, player.getEyeHeight() * 0.5, 0).add(look.scale(1.5));
        com.gytrinket.gytrinket.network.NetworkHandler.sendEnergyWaveExplosionToAll(
                (ServerLevel) player.getLevel(), center, look, length, -1, colorType, 0.0);
        gytrinket.LOGGER.info("[wave] sent center={} dir={} length={} color={}", center, look, length, colorType);
        source.sendSuccess(Component.literal("已发送能量波特效（长度 " + length + "，颜色 "
                + (colorType == 1 ? "蓝色系/反射护盾" : "黄橙系/蜂群") + "）"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // 光点核心存储（PlayerStore）验收：等价于在光点核心界面里放进物品
    // ------------------------------------------------------------------
    /**
     * 往光点核心存储里放物品：{@code /gytrinket store <id> [id ...]}。
     * 每个参数可以是 {@code id} 或 {@code id*count}。写入通过 ItemStackHandler 触发
     * {@code PlayerLightPointStoreChangedEvent}，属性/护盾/攻击模式会走真实重算链路。
     */
    private static int storeItems(CommandSourceStack source, String[] specs) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        var store = com.gytrinket.gytrinket.storage.PlayerStoreManager.getOrCreatePlayerStore(player);
        var handler = store.getItemHandler();
        int added = 0;
        for (String spec : specs) {
            if (spec == null || spec.isBlank()) continue;
            String id = spec;
            int count = 1;
            int star = spec.lastIndexOf('*');
            if (star > 0) {
                id = spec.substring(0, star);
                try {
                    count = Integer.parseInt(spec.substring(star + 1));
                } catch (NumberFormatException ignored) {
                    id = spec;
                }
            }
            var rl = net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "gytrinket:" + id);
            var item = rl == null ? null : net.minecraft.core.Registry.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                source.sendFailure(Component.literal("未知物品: " + id));
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, Math.min(count, 64)));
            boolean placed = false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).isEmpty()) {
                    handler.setStackInSlot(slot, stack);
                    placed = true;
                    added++;
                    break;
                }
            }
            if (!placed) {
                source.sendFailure(Component.literal("光点核心存储已满，未放入: " + id));
            }
        }
        gytrinket.LOGGER.info("[store] added {} stacks for {}", added, player.getName().getString());
        source.sendSuccess(Component.literal("已放入光点核心存储: " + added + " 件，当前属性重算已触发"), false);
        return added;
    }

    /** 清空光点核心存储（验收复位用） */
    private static int clearStore(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        var store = com.gytrinket.gytrinket.storage.PlayerStoreManager.getOrCreatePlayerStore(player);
        var handler = store.getItemHandler();
        int removed = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                removed++;
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        source.sendSuccess(Component.literal("已清空光点核心存储: " + removed + " 件"), false);
        return removed;
    }

    /** 打印光点核心存储内容与禁用状态（验收用） */
    private static int listStore(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        var store = com.gytrinket.gytrinket.storage.PlayerStoreManager.getOrCreatePlayerStore(player);
        var handler = store.getItemHandler();
        var disabled = com.gytrinket.gytrinket.core.shield.DisableSystem.getDisabledItems(player.getUUID());
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            count++;
            String id = net.minecraft.core.Registry.ITEM.getKey(stack.getItem()).toString();
            boolean off = disabled.contains(id);
            gytrinket.LOGGER.info("[store] slot={} item={} x{} disabled={}{}", slot, id, stack.getCount(), off,
                    off ? " reason=" + com.gytrinket.gytrinket.core.shield.DisableSystem
                            .getDisabledReason(player.getUUID(), id) : "");
        }
        gytrinket.LOGGER.info("[store] total={} disabledIds={}", count, disabled);
        source.sendSuccess(Component.literal("光点核心存储: " + count + " 件"), false);
        return count;
    }

    // ------------------------------------------------------------------
    // 构造体验收：走真实构建链路（ConstructBuilder → onCreated → addConstruct）
    // ------------------------------------------------------------------
    /**
     * 立即完成一次构造体构建。不传 type 时打印可选类型。
     * <p>
     * 之所以走 {@link com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder} 而不是
     * 直接 /summon：构造体实体在 {@code checkManagerRegistration} 里要求 owner 与
     * ConstructManager 注册表都存在，裸召唤的实体会在下一轮检查时自毁。
     */
    private static int spawnConstruct(CommandSourceStack source, String typeId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        var manager = com.gytrinket.gytrinket.core.entity.construct.ConstructManager.getInstance();
        if (typeId == null) {
            String ids = String.join(", ", manager.getAllConstructTypeIds());
            source.sendSuccess(Component.literal("构造体类型: " + ids), false);
            return manager.getAllConstructTypeIds().size();
        }
        var type = manager.getConstructType(typeId);
        if (type == null) {
            source.sendFailure(Component.literal("未知构造体类型: " + typeId
                    + "，可用: " + String.join(", ", manager.getAllConstructTypeIds())));
            return 0;
        }
        var builder = new com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder(player, type);
        int guard = 0;
        while (!builder.isCompleted() && guard++ < 20000) {
            builder.tick();
        }
        if (!builder.isCompleted()) {
            source.sendFailure(Component.literal("构建未完成（构建速度可能为 0）: " + typeId));
            return 0;
        }
        int count = manager.getPlayerConstructCountByType(player, typeId);
        gytrinket.LOGGER.info("[construct] spawned {} for {} (count={}, entities={})",
                typeId, player.getName().getString(), count,
                manager.getActiveConstructEntities(player.getUUID(), typeId).size());
        source.sendSuccess(Component.literal("已构建 " + typeId + "，当前该类型数量: " + count), false);
        return count;
    }

    /** 清掉执行者名下的全部构造体实体（验收复位用） */
    private static int clearConstructs(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        var manager = com.gytrinket.gytrinket.core.entity.construct.ConstructManager.getInstance();
        var server = player.getServer();
        var level = server != null ? server.getLevel(player.getLevel().dimension()) : null;
        int removed = 0;
        for (var entry : manager.getPlayerConstructs(player.getUUID()).entrySet()) {
            for (var data : entry.getValue()) {
                if (level != null) {
                    var entity = level.getEntity(data.getEntityUUID());
                    if (entity != null) {
                        entity.discard();
                        removed++;
                    }
                }
                manager.markConstructDead(player.getUUID(), data.getConstructId(), data.getEntityUUID());
            }
            manager.removeConstructsByType(player, entry.getKey());
        }
        gytrinket.LOGGER.info("[construct] cleared {} entities for {}",
                removed, player.getName().getString());
        source.sendSuccess(Component.literal("已清理构造体实体: " + removed), false);
        return removed;
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
        com.gytrinket.gytrinket.core.projectile.MultipartHitScan.Hit hit =
                com.gytrinket.gytrinket.core.projectile.MultipartHitScan.findFirstOnPath(
                        source.getLevel(), from, to,
                        com.gytrinket.gytrinket.core.projectile.MultipartHitScan.DEFAULT_HITBOX_INFLATE,
                        e -> true);
        if (hit == null) {
            gytrinket.LOGGER.info("[hittest] {} -> {} : no hit", from, to);
            source.sendSuccess(Component.literal("[hittest] no hit"), false);
            return 0;
        }
        String parentClass = hit.target().getClass().getSimpleName();
        String boxClass = hit.hitbox().getClass().getSimpleName();
        String kind = hit.hitbox() instanceof net.minecraftforge.entity.PartEntity<?> ? "part" : "parent";
        String text = String.format("[hittest] %s -> %s : %s %s (parent %s) pos=%.3f/%.3f/%.3f",
                from, to, kind, boxClass, parentClass,
                hit.position().x, hit.position().y, hit.position().z);
        gytrinket.LOGGER.info(text);
        source.sendSuccess(Component.literal(text), false);
        return 1;
    }

    /**
     * 列出附近多体节实体的父实体与全部体节碰撞箱（诊断用）。
     */
    private static int dumpParts(CommandSourceStack source, double radius) {
        Vec3 pos = source.getPosition();
        java.util.List<net.minecraft.world.entity.Entity> parents = source.getLevel().getEntities(
                (net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(pos, pos).inflate(radius),
                e -> {
                    net.minecraftforge.entity.PartEntity<?>[] parts = e.getParts();
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
    private static final Map<Block, int[]> TEX_CACHE = new HashMap<>();

    private static int[] textureOf(Block block) {
        if (TEX_CACHE.containsKey(block)) {
            return TEX_CACHE.get(block);
        }
        int[] tex = null;
        ResourceLocation id = Registry.BLOCK.getKey(block);
        if ("minecraft".equals(id.getNamespace())) {
            byte[] png = readAsset("assets/minecraft/textures/block/" + id.getPath() + ".png");
            if (png != null) {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
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

    private static ZipFile clientAssets;
    private static boolean clientAssetsSearched;

    /** 先找 classpath 上的资源（1.20.1 ForgeGradle 的 dev 环境），再找旧的 client-extra jar */
    private static byte[] readAsset(String path) {
        try (InputStream in = DevCommands.class.getResourceAsStream("/" + path)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {
            // 继续尝试 jar
        }
        ZipFile zip = findClientAssets();
        if (zip != null) {
            ZipEntry entry = zip.getEntry(path);
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static ZipFile findClientAssets() {
        if (clientAssetsSearched) {
            return clientAssets;
        }
        clientAssetsSearched = true;
        // 1.20.1 ForgeGradle 把 client-extra 挂在运行期 classpath 上
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            if (!entry.contains("client-extra")) {
                continue;
            }
            File jar = new File(entry);
            if (!jar.isFile()) {
                continue;
            }
            try {
                clientAssets = new ZipFile(jar);
                return clientAssets;
            } catch (Exception ignored) {
                // 继续找
            }
        }
        // 旧版插件把 client-extra 拷到 build/moddev/artifacts
        File[] candidates = {
                new File("../build/moddev/artifacts"),
                new File("build/moddev/artifacts")
        };
        for (File dir : candidates) {
            File[] jars = dir.listFiles((d, n) -> n.contains("client-extra") && n.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                try {
                    clientAssets = new ZipFile(jars[0]);
                    return clientAssets;
                } catch (Exception ignored) {
                    // 继续找
                }
            }
        }
        return null;
    }

    private static int tintFor(Block block) {
        String p = Registry.BLOCK.getKey(block).getPath();
        if (p.contains("grass") || p.contains("leaves") || p.contains("vine") || p.contains("fern")) {
            return 0x86C05A;
        }
        if (p.contains("water")) {
            return 0x3F76E4;
        }
        return 0xFFFFFF;
    }

    private static int view(ServerLevel level, BlockPos from, BlockPos to,
                            Vec3 cam, Vec3 target, int width) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;

        Block[] palette = new Block[8192];
        int paletteSize = 1;
        Map<Block, Integer> blockIndex = new HashMap<>();
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
                    Block block = state.getBlock();
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
                    Block block = palette[idx];
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

    private static final Map<Block, Integer> TEXTURE_CACHE = new HashMap<>();

    private static int blockColor(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Integer cached = TEXTURE_CACHE.get(block);
        if (cached == null) {
            Integer avg = averageTexture(block);
            // 1.20.1：MapColor#col 仍是 public 字段
            cached = avg != null ? avg : (state.getMapColor(level, pos).col & 0xFFFFFF);
            TEXTURE_CACHE.put(block, cached);
        }
        return cached;
    }

    /** 从客户端资源里读 16×16 贴图并取平均色；读不到时回退到地图颜色 */
    private static Integer averageTexture(Block block) {
        ResourceLocation id = Registry.BLOCK.getKey(block);
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }
        byte[] png = readAsset("assets/minecraft/textures/block/" + id.getPath() + ".png");
        if (png == null) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
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
    private static int dump(ServerLevel level, BlockPos from, BlockPos to, String name, Path dir) {
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
        Path out = dir.toAbsolutePath().resolve("gytrinket_" + name + ".nbt");
        try {
            Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 1.20.1 的 NbtIo.writeCompressed 只接受 java.io.File
            NbtIo.writeCompressed(tag, out.toFile());
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

    // ------------------------------------------------------------------
    // P5 诊断：定义状态 golden dump / rev，tick 采样
    // ------------------------------------------------------------------
    private static int dumpDefs(CommandSourceStack source) {
        try {
            Map<String, Object> state = collectP5DefsState();
            String json = P5Dump.canonical(state);
            Path dir = Path.of("p5dump");
            Files.createDirectories(dir);
            Path file = dir.resolve("defs.json");
            Files.writeString(file, json, StandardCharsets.UTF_8);
            String rev = P5Dump.sha256(json);
            Files.writeString(dir.resolve("defs.rev"), rev, StandardCharsets.UTF_8);
            source.sendSuccess(Component.literal("p5 defs dump: " + file.toAbsolutePath() + " rev=" + rev), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("p5 defs dump failed: " + e));
            return 0;
        }
    }

    private static int printDefsRev(CommandSourceStack source) {
        try {
            String rev = P5Dump.sha256(P5Dump.canonical(collectP5DefsState()));
            source.sendSuccess(Component.literal("p5 defs rev=" + rev), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("p5 defs rev failed: " + e));
            return 0;
        }
    }

    private static int startPerf(CommandSourceStack source) {
        TickProfiler.reset();
        TickProfiler.setEnabled(true);
        source.sendSuccess(Component.literal("tick profiler enabled (samples:" + TickProfiler.sampleCount("server_tick") + ")"), false);
        return 1;
    }

    private static int stopPerf(CommandSourceStack source) {
        TickProfiler.setEnabled(false);
        source.sendSuccess(Component.literal("tick profiler disabled; use /gytrinket perf dump for samples"), false);
        return 1;
    }

    private static int resetPerf(CommandSourceStack source) {
        TickProfiler.reset();
        source.sendSuccess(Component.literal("tick profiler samples cleared"), false);
        return 1;
    }

    private static int dumpPerf(CommandSourceStack source) {
        source.sendSuccess(Component.literal(TickProfiler.dumpJson()), false);
        return 1;
    }

    private static Map<String, Object> collectP5DefsState() {
        Map<String, Object> root = new TreeMap<>();

        List<DefsManager.AttributeEntry> attributeDefs = new ArrayList<>(DefsManager.getAttributeDefs());
        attributeDefs.sort(Comparator.comparing(DefsManager.AttributeEntry::name));
        root.put("attributeDefs", attributeDefs);

        root.put("shieldTypes", new TreeMap<>(DefsManager.getServerShieldTypes()));

        Map<String, Map<String, Double>> shieldDefaults = new TreeMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : DefsManager.getServerShieldTypeParamDefaultDoubles().entrySet()) {
            shieldDefaults.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        root.put("shieldTypeParamDefaults", shieldDefaults);

        root.put("specialMechanicItems", new TreeSet<>(DefsManager.getServerSpecialMechanicItems()));

        Map<String, List<String>> effectiveSets = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : DefsManager.getServerAllEffectiveSets().entrySet()) {
            List<String> sets = new ArrayList<>(entry.getValue());
            sets.sort(Comparator.naturalOrder());
            effectiveSets.put(entry.getKey(), sets);
        }
        root.put("effectiveSets", effectiveSets);

        Map<String, List<String>> itemShieldTypes = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : DefsManager.getItemShieldTypes().entrySet()) {
            List<String> types = new ArrayList<>(entry.getValue());
            types.sort(Comparator.naturalOrder());
            itemShieldTypes.put(entry.getKey(), types);
        }
        root.put("itemShieldTypes", itemShieldTypes);

        root.put("disableTargets", DefsManager.getDisableTargets());
        root.put("dependencies", DefsManager.getDependencies());
        root.put("disableCategories", DefsManager.getDisableCategories());
        root.put("dependenciesAll", DefsManager.getDependenciesAll());
        root.put("moduleTrees", DefsManager.getModuleTrees());
        root.put("upgradePaths", DefsManager.getUpgradePaths());

        List<DefsManager.TooltipRuleDef> tooltips = new ArrayList<>(DefsManager.getServerTooltipRules());
        tooltips.sort(Comparator.comparing(DefsManager.TooltipRuleDef::titleKey)
                .thenComparing(DefsManager.TooltipRuleDef::itemSet));
        root.put("tooltipRules", tooltips);

        root.put("itemDefinitions", DefsManager.getItemDefinitions());
        root.put("specialMechanicOverrides", DefsManager.getServerSpecialMechanicOverrides());
        root.put("shieldTypeOverrides", DefsManager.getServerShieldTypeOverrides());
        root.put("configDerived", Config.dumpDerivedForDiagnostics());

        Map<String, Object> registeredAttributes = new TreeMap<>();
        for (String name : new TreeSet<>(AttributeManager.getAllRegisteredAttributes())) {
            AttributeDefinition definition = AttributeManager.getAttributeDefinition(name);
            if (definition != null) {
                Map<String, Object> info = new TreeMap<>();
                info.put("type", definition.getType().name());
                info.put("group", definition.getGroup());
                registeredAttributes.put(name, info);
            }
        }
        root.put("registeredAttributes", registeredAttributes);
        root.put("registeredItemAttributes", new TreeSet<>(AttributeManager.getAllRegisteredItemAttributes()));
        return root;
    }

    private record Voxel(int x, int y, int z, Color color) {
    }
}
