package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.core.world.terrain.RuinedTowerBlueprint;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

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
 * shielddemo [x y z] [age]  在坐标（或视线前方）摆一圈护盾粒子并冻结年龄
 * shieldage n               把现有粒子年龄改成 n 刻
 * shieldfreeze [0]          冻结/解冻粒子计时
 * shieldstyle 0|1|2         切换护盾粒子形象（水晶/旧立体/平面）
 * shieldclear               清掉护盾粒子与演示实体
 * </pre>
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class ClientConsole {

    private ClientConsole() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 观察台的自动循环（每 20 刻换一批），与 1.21.1 对齐
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
                case "shieldinfo" -> {
                    var sp = serverPlayer();
                    if (sp != null) {
                        gytrinket.LOGGER.info("[cmd] shieldinfo current={} max={} instances={}",
                                com.gy_mod.gy_trinket.core.shield.ShieldManager.getCurrentShield(sp.getUUID()),
                                com.gy_mod.gy_trinket.core.shield.ShieldManager.getMaxShield(sp.getUUID()),
                                com.gy_mod.gy_trinket.core.shield.ShieldManager.getInstances(sp.getUUID()).size());
                    }
                }
                case "shieldset" -> {
                    double value = Double.parseDouble(p[1]);
                    var sp = serverPlayer();
                    if (sp != null) {
                        com.gy_mod.gy_trinket.core.shield.ShieldManager.setCurrentShield(sp.getUUID(), value);
                        gytrinket.LOGGER.info("[cmd] shieldset {} -> current={} max={}",
                                value,
                                com.gy_mod.gy_trinket.core.shield.ShieldManager.getCurrentShield(sp.getUUID()),
                                com.gy_mod.gy_trinket.core.shield.ShieldManager.getMaxShield(sp.getUUID()));
                    }
                }
                case "quit" -> {
                    // 正常退出（会走 PlayerLoggedOutEvent → PlayerDataCenter 落盘），测持久化用
                    gytrinket.LOGGER.info("[cmd] quit");
                    mc.stop();
                }
                case "reload" -> {
                    // 改完着色器 / 贴图不用重启客户端：热重载资源包（含 core shader）
                    mc.reloadResourcePacks();
                    gytrinket.LOGGER.info("[cmd] reload resource packs");
                }
                case "cfg" -> {
                    // 打开配置界面：cfg 1 = 直接进「客户端」分组（调试多选一行用）
                    boolean clientTab = p.length > 1 && !"0".equals(p[1]);
                    mc.setScreen(new com.gy_mod.gy_trinket.client.screen.ConfigEntriesScreen(null, clientTab));
                    gytrinket.LOGGER.info("[cmd] cfg clientTab={}", clientTab);
                }
                case "cfgclick" -> {
                    // 模拟一次左键点击（像素坐标），用来验证配置界面的行点击/多选一循环
                    double cx = Double.parseDouble(p[1]);
                    double cy = Double.parseDouble(p[2]);
                    boolean handled = mc.screen != null && mc.screen.mouseClicked(cx, cy, 0);
                    gytrinket.LOGGER.info("[cmd] cfgclick {}/{} handled={}", cx, cy, handled);
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
                case "shielddemo" -> {
                    // 观察台：隐形盔甲架 + 一批 19 颗粒子（中心 + 三圈六边形），镜头自动摆到斜前方
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
                case "shieldstyle" -> {
                    int style = Integer.parseInt(p[1]);
                    com.gy_mod.gy_trinket.config.ClientConfig.SHIELD_PARTICLE_STYLE.set(style);
                    gytrinket.LOGGER.info("[cmd] shieldstyle {}", style);
                }
                case "shieldclear" -> {
                    ShieldDemoHarness.stop(mc);
                    gytrinket.LOGGER.info("[cmd] shieldclear");
                }
                case "hud" -> {
                    // hud 0 隐藏 HUD（干净截图），hud 1 显示
                    mc.options.hideGui = p.length < 2 || "0".equals(p[1]);
                    gytrinket.LOGGER.info("[cmd] hud hidden={}", mc.options.hideGui);
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
                    net.minecraft.nbt.NbtIo.writeCompressed(tag, out.toFile());
                    gytrinket.LOGGER.info("[cmd] dumped {} ({} blocks)", out.toAbsolutePath(), tag.getList("blocks", 10).size());
                }                default -> gytrinket.LOGGER.warn("[cmd] unknown {}", p[0]);
            }
        } catch (Exception e) {
            gytrinket.LOGGER.warn("[cmd] failed: {} ({})", line, e.getMessage());
        }
    }


    /** 客户端线程上取当前单人存档的服务端玩家（dev 控制台用，没有则返回 null） */
    private static net.minecraft.server.level.ServerPlayer serverPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.player == null) {
            return null;
        }
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
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
