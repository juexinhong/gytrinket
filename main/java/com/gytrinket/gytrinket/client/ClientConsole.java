package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.core.world.terrain.RuinedTowerBlueprint;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 开发控制台：客户端运行期间轮询 {@code run/gytrinket_cmd.txt}，
 * 每读一行就立刻执行，然后删除文件。这样可以在不重启游戏的情况下
 * 实时调整相机、截图、建房、清地。
 * <p>
 * 支持的命令：
 * <pre>
 * cam  x y z yaw pitch      移动到机位
 * shot name                 截图到 screenshots/name.png
 * build variant x y z       在坐标砌民居（variant 0..3，3 为别墅）
 * clear x0 y0 z0 x1 y1 z1   清空区域
 * srv  &lt;命令&gt;               直接执行一条服务端命令
 * hud  0|1                  隐藏/显示 HUD
 * reload                    重载资源包（改完着色器后热更新）
 * shielddemo [x y z]        搭护盾粒子观察台并把镜头摆好
 * shieldstop                收掉观察台
 * shieldauto 0|1            自动循环生成粒子
 * shieldfreeze 0|1          冻结/解冻粒子年龄（固定姿态截图用）
 * shieldage n               把所有粒子年龄固定到第 n 刻
 * shieldburst               立刻生成一次受击粒子
 * shieldclear               清空当前粒子
 * </pre>
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class ClientConsole {

    private ClientConsole() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        ShieldDemoHarness.tick(mc);
        Path file = mc.gameDirectory.toPath().resolve("gytrinket_cmd.txt");
        if (!Files.exists(file)) {
            return;
        }
        String line;
        try {
            line = Files.readString(file).trim();
            Files.deleteIfExists(file);
        } catch (Exception e) {
            return;
        }
        if (line.isEmpty()) {
            return;
        }
        String[] p = line.split("\\s+");
        // 所有修改都必须在服务端世界执行，否则会被服务端回滚（方块闪烁）
        var integrated = mc.getSingleplayerServer();
        var target = integrated != null ? integrated.overworld() : mc.level;
        try {
            switch (p[0]) {
                case "cam" -> {
                    double x = Double.parseDouble(p[1]);
                    double y = Double.parseDouble(p[2]);
                    double z = Double.parseDouble(p[3]);
                    float yaw = Float.parseFloat(p[4]);
                    float pitch = Float.parseFloat(p[5]);
                    mc.options.pauseOnLostFocus = false;
                    if (mc.screen != null) {
                        mc.setScreen(null);
                    }
                    mc.player.getAbilities().mayfly = true;
                    mc.player.getAbilities().flying = true;
                    mc.player.getAbilities().instabuild = true;
                    mc.player.onUpdateAbilities();
                    mc.player.setPos(x, y, z);
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(pitch);
                    mc.player.setOldPosAndRot();
                    gytrinket.LOGGER.info("[cmd] cam {} {} {}", x, y, z);
                }
                case "shot" -> {
                    String name = p.length > 1 ? p[1] : "shot";
                    Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
                    });
                    gytrinket.LOGGER.info("[cmd] shot {}", name);
                }
                case "build" -> {
                    int variant = Integer.parseInt(p[1]);
                    int bx = Integer.parseInt(p[2]);
                    int by = Integer.parseInt(p[3]);
                    int bz = Integer.parseInt(p[4]);
                    if (mc.getSingleplayerServer() != null && mc.level != null) {
                        RuinedTowerBlueprint bp = new RuinedTowerBlueprint(0L);
                        if (p.length > 5) {
                            bp.overrideHeight = Integer.parseInt(p[5]);
                        }
                        if (p.length > 6) {
                            bp.overrideWidth = Integer.parseInt(p[6]);
                        }
                        bp.placeHouse(variant, (x, y, z, m) ->
                                target.setBlock(new BlockPos(bx + x, by + y, bz + z), blockFor(m), 3));
                        gytrinket.LOGGER.info("[cmd] built variant {} at {} {} {}", variant, bx, by, bz);
                    }
                }
                case "clear" -> {
                    int x0 = Integer.parseInt(p[1]);
                    int y0 = Integer.parseInt(p[2]);
                    int z0 = Integer.parseInt(p[3]);
                    int x1 = Integer.parseInt(p[4]);
                    int y1 = Integer.parseInt(p[5]);
                    int z1 = Integer.parseInt(p[6]);
                    if (mc.level != null) {
                        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                                    target.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                                }
                            }
                        }
                        gytrinket.LOGGER.info("[cmd] cleared");
                    }
                }
                case "set" -> {
                    int sx = Integer.parseInt(p[1]);
                    int sy = Integer.parseInt(p[2]);
                    int sz = Integer.parseInt(p[3]);
                    target.setBlock(new BlockPos(sx, sy, sz), blockByName(p[4]), 3);
                    gytrinket.LOGGER.info("[cmd] set {} {} {} {}", sx, sy, sz, p[4]);
                }
                case "fill" -> {
                    int fx0 = Integer.parseInt(p[1]);
                    int fy0 = Integer.parseInt(p[2]);
                    int fz0 = Integer.parseInt(p[3]);
                    int fx1 = Integer.parseInt(p[4]);
                    int fy1 = Integer.parseInt(p[5]);
                    int fz1 = Integer.parseInt(p[6]);
                    BlockState st = blockByName(p[7]);
                    for (int x = Math.min(fx0, fx1); x <= Math.max(fx0, fx1); x++) {
                        for (int y = Math.min(fy0, fy1); y <= Math.max(fy0, fy1); y++) {
                            for (int z = Math.min(fz0, fz1); z <= Math.max(fz0, fz1); z++) {
                                target.setBlock(new BlockPos(x, y, z), st, 3);
                            }
                        }
                    }
                    gytrinket.LOGGER.info("[cmd] fill {}", p[7]);
                }
                case "dump" -> {
                    String name = p[1];
                    int dx0 = Integer.parseInt(p[2]);
                    int dy0 = Integer.parseInt(p[3]);
                    int dz0 = Integer.parseInt(p[4]);
                    int dx1 = Integer.parseInt(p[5]);
                    int dy1 = Integer.parseInt(p[6]);
                    int dz1 = Integer.parseInt(p[7]);
                    int minX = Math.min(dx0, dx1);
                    int minY = Math.min(dy0, dy1);
                    int minZ = Math.min(dz0, dz1);
                    var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
                    template.fillFromWorld(mc.level, new BlockPos(minX, minY, minZ),
                            new net.minecraft.core.Vec3i(Math.abs(dx1 - dx0) + 1, Math.abs(dy1 - dy0) + 1,
                                    Math.abs(dz1 - dz0) + 1), false, null);
                    var tag = template.save(new net.minecraft.nbt.CompoundTag());
                    java.nio.file.Path out = mc.gameDirectory.toPath().resolve("gytrinket_" + name + ".nbt");
                    net.minecraft.nbt.NbtIo.writeCompressed(tag, out);
                    gytrinket.LOGGER.info("[cmd] dumped {} ({} blocks)", out.toAbsolutePath(), tag.getList("blocks", 10).size());
                }
                case "srv" -> {
                    String command = line.substring(3).trim();
                    var server = mc.getSingleplayerServer();
                    var source = ShieldDemoHarness.serverSource(mc);
                    if (server != null && source != null && !command.isEmpty()) {
                        server.execute(() -> server.getCommands().performPrefixedCommand(source, command));
                        gytrinket.LOGGER.info("[cmd] srv {}", command);
                    }
                }
                case "hud" -> {
                    // hud 0 隐藏 HUD（干净截图），hud 1 显示
                    mc.options.hideGui = p.length < 2 || "0".equals(p[1]);
                    gytrinket.LOGGER.info("[cmd] hud hidden={}", mc.options.hideGui);
                }
                case "reload" -> {
                    mc.reloadResourcePacks();
                    gytrinket.LOGGER.info("[cmd] reload resource packs");
                }
                case "shielddemo" -> {
                    if (p.length >= 4) {
                        ShieldDemoHarness.start(mc,
                                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                                p.length > 4 ? Double.parseDouble(p[4]) : 3.2D,
                                p.length > 5 ? Double.parseDouble(p[5]) : 38.0D);
                    } else {
                        ShieldDemoHarness.start(mc,
                                Math.floor(mc.player.getX()) + 0.5D,
                                Math.floor(mc.player.getY()) + 0.05D,
                                Math.floor(mc.player.getZ()) + 0.5D,
                                3.2D, 38.0D);
                    }
                    gytrinket.LOGGER.info("[cmd] shielddemo");
                }
                case "shieldstop" -> {
                    ShieldDemoHarness.stop(mc);
                    gytrinket.LOGGER.info("[cmd] shieldstop");
                }
                case "shieldauto" -> {
                    ShieldDemoHarness.setAuto(p.length > 1 && !"0".equals(p[1]));
                    gytrinket.LOGGER.info("[cmd] shieldauto {}", p.length > 1 ? p[1] : "1");
                }
                case "shieldfreeze" -> {
                    ShieldDemoHarness.setFreeze(p.length > 1 && !"0".equals(p[1]));
                    gytrinket.LOGGER.info("[cmd] shieldfreeze {}", p.length > 1 ? p[1] : "1");
                }
                case "shieldage" -> {
                    ShieldDemoHarness.setAge(p.length > 1 ? Integer.parseInt(p[1]) : 8);
                    gytrinket.LOGGER.info("[cmd] shieldage {}", p.length > 1 ? p[1] : "8");
                }
                case "shieldburst" -> {
                    ShieldDemoHarness.burst(mc);
                    gytrinket.LOGGER.info("[cmd] shieldburst");
                }
                case "shieldorbit" -> {
                    ShieldDemoHarness.orbit(mc,
                            p.length > 1 ? Double.parseDouble(p[1]) : 38.0D,
                            p.length > 2 ? Double.parseDouble(p[2]) : 3.2D,
                            p.length > 3 ? Double.parseDouble(p[3]) : 1.15D);
                    gytrinket.LOGGER.info("[cmd] shieldorbit");
                }
                case "shieldclear" -> {
                    com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderManager
                            .getInstance().clear();
                    gytrinket.LOGGER.info("[cmd] shieldclear");
                }
                default -> gytrinket.LOGGER.warn("[cmd] unknown {}", p[0]);
            }
        } catch (Exception e) {
            gytrinket.LOGGER.warn("[cmd] failed: {} ({})", line, e.getMessage());
        }
    }

    private static BlockState blockByName(String id) {
        String full = id.contains(":") ? id : "minecraft:" + id;
        var rl = net.minecraft.resources.ResourceLocation.tryParse(full);
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
        return block.defaultBlockState();
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
}
