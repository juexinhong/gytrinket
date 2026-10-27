package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.core.world.terrain.RuinedTowerBlueprint;
import com.gytrinket.gytrinket.gytrinket;
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

    /** >0 时倒计时，归零后打印"创建新的世界"界面的 worldgen lifecycle（实验性警告诊断用） */
    private static int pendingWorldCheckTicks = -1;

    /** worldcheck 的可选世界类型 id（如 flat / amplified），非空时在倒计时中途切换预设 */
    private static String pendingWorldCheckPreset = null;

    private ClientConsole() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (pendingWorldCheckTicks > 0) {
            --pendingWorldCheckTicks;
            if (pendingWorldCheckTicks == 70 && pendingWorldCheckPreset != null) {
                applyWorldPreset(pendingWorldCheckPreset);
            }
            if (pendingWorldCheckTicks == 0) {
                logWorldCreationStability(mc);
            }
        }
        // 控制台命令文件先读出来：join/quit 这类命令在主菜单阶段就要能执行（自动化验证用）
        Path file = mc.gameDirectory.toPath().resolve("gytrinket_cmd.txt");
        String line = null;
        if (Files.exists(file)) {
            try {
                line = Files.readString(file).trim();
            } catch (Exception e) {
                line = null;
            }
            if (line != null && line.isEmpty()) {
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
                line = null;
            }
        }
        if (line != null && line.startsWith("join")) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
            }
            String[] head = line.split("\\s+");
            if (head.length > 1) {
                gytrinket.LOGGER.info("[cmd] join {}", head[1]);
                mc.createWorldOpenFlows().loadLevel(mc.screen, head[1]);
            }
            return;
        }
        if (line != null && line.startsWith("connect")) {
            // 连开发服务器：截图/指令验证走独立服务端，避免单人存档锁与世界复制的坑
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
            }
            String[] head = line.split("\\s+");
            if (head.length > 1) {
                gytrinket.LOGGER.info("[cmd] connect {}", head[1]);
                net.minecraft.client.multiplayer.resolver.ServerAddress address =
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(head[1]);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                        mc.screen, mc, address,
                        new net.minecraft.client.multiplayer.ServerData("dev", head[1], false));
            }
            return;
        }
        if (line != null && line.startsWith("worldcheck")) {
            // 实验性警告诊断：主菜单/世界内都可执行；可带世界类型参数（如 worldcheck flat）验证预设切换路径
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
            }
            String[] head = line.split("\\s+");
            pendingWorldCheckPreset = head.length > 1 ? head[1] : null;
            gytrinket.LOGGER.info("[cmd] worldcheck: opening CreateWorldScreen (preset={})",
                    pendingWorldCheckPreset == null ? "current" : pendingWorldCheckPreset);
            mc.setScreen(null);
            net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, null);
            pendingWorldCheckTicks = 100;
            return;
        }
        if (mc.player == null || mc.level == null) {
            // 没有世界时先把命令留在文件里，进世界后的下一 tick 再执行
            return;
        }
        // 观察台的自动循环（每 20 刻换一批），与 1.21.1 对齐
        ShieldDemoHarness.tick(mc);
        if (line == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception e) {
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
                                com.gytrinket.gytrinket.core.shield.ShieldManager.getCurrentShield(sp.getUUID()),
                                com.gytrinket.gytrinket.core.shield.ShieldManager.getMaxShield(sp.getUUID()),
                                com.gytrinket.gytrinket.core.shield.ShieldManager.getInstances(sp.getUUID()).size());
                    }
                }
                case "shieldset" -> {
                    double value = Double.parseDouble(p[1]);
                    var sp = serverPlayer();
                    if (sp != null) {
                        com.gytrinket.gytrinket.core.shield.ShieldManager.setCurrentShield(sp.getUUID(), value);
                        gytrinket.LOGGER.info("[cmd] shieldset {} -> current={} max={}",
                                value,
                                com.gytrinket.gytrinket.core.shield.ShieldManager.getCurrentShield(sp.getUUID()),
                                com.gytrinket.gytrinket.core.shield.ShieldManager.getMaxShield(sp.getUUID()));
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
                    mc.setScreen(new com.gytrinket.gytrinket.client.screen.ConfigEntriesScreen(null, clientTab));
                    gytrinket.LOGGER.info("[cmd] cfg clientTab={}", clientTab);
                }
                case "defsrev" -> gytrinket.LOGGER.info("[defs] client revision={}",
                        com.gytrinket.gytrinket.core.defs.DefsManager.clientRevision());
                case "langcheck" -> {
                    // 诊断：确认运行时语言表里的原始字符串（MC 会把 %d/%.1f 转成 %s）
                    String key = p.length > 1 ? p[1] : "tooltip.gytrinket.wingman_module_desc";
                    String raw = net.minecraft.locale.Language.getInstance().getOrDefault(key);
                    gytrinket.LOGGER.info("[langcheck] RAW {}", raw.replace("\n", "\\n"));
                    try {
                        String fmt = String.format(raw, "A1", "B2", "C3", "D4", "E5", "F6", "G7");
                        gytrinket.LOGGER.info("[langcheck] FMT {}", fmt.replace("\n", "\\n"));
                    } catch (Throwable t) {
                        gytrinket.LOGGER.error("[langcheck] FORMAT FAILED", t);
                    }
                    String comp = net.minecraft.network.chat.Component.translatable(key).getString();
                    gytrinket.LOGGER.info("[langcheck] COMPONENT {}", comp.replace("\n", "\\n"));
                }
                case "tooltip" -> {
                    // 诊断工具提示：打印指定物品的整段 tooltip（含数据驱动的动态数值行）
                    String id = p.length > 1 ? p[1] : "gytrinket:shield_gy1";
                    String fullId = id.contains(":") ? id : "gytrinket:" + id;
                    var rl = net.minecraft.resources.ResourceLocation.tryParse(fullId);
                    var item = net.minecraft.core.Registry.ITEM.get(rl);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        gytrinket.LOGGER.info("[tooltip] unknown item {}", fullId);
                        break;
                    }
                    var stack = new net.minecraft.world.item.ItemStack(item);
                    var lines = stack.getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                    gytrinket.LOGGER.info("[tooltip] {} -> {} lines", fullId, lines.size());
                    for (int i = 0; i < lines.size(); i++) {
                        gytrinket.LOGGER.info("[tooltip]   {}: {}", i, lines.get(i).getString());
                    }
                }
                case "tabs" -> {
                    // 调试用：列出创造标签页（序号 / 标题 / 物品数），验证注册顺序与语言键
                    int idx = 0;
                    for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTab.TABS) {
                        if (tab == null) {
                            idx++;
                            continue;
                        }
                        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> list =
                                net.minecraft.core.NonNullList.create();
                        try {
                            tab.fillItemList(list);
                        } catch (Exception ex) {
                            // 搜索页的物品表由客户端单独实现，遍历时会抛异常，跳过
                            gytrinket.LOGGER.info("[tabs] #{} label={} count=SKIP",
                                    idx, tab.getDisplayName().getString());
                            idx++;
                            continue;
                        }
                        StringBuilder head = new StringBuilder();
                        for (int i = 0; i < Math.min(5, list.size()); i++) {
                            head.append(net.minecraft.core.Registry.ITEM.getKey(list.get(i).getItem())).append(' ');
                        }
                        gytrinket.LOGGER.info("[tabs] #{} label={} count={} head=[{}]",
                                idx, tab.getDisplayName().getString(), list.size(), head.toString().trim());
                        idx++;
                    }
                }
                case "screen" -> {
                    // 调试用：直接打开某个界面做截图验收
                    String screenId = p.length > 1 ? p[1] : "creative";
                    switch (screenId) {
                        case "creative" -> mc.setScreen(
                                new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player));
                        case "creativetab" -> {
                            // 反射设置创造界面的静态 selectedTab，让界面直接开在本模组标签页
                            int tabIndex = -1;
                            for (int i = 0; i < net.minecraft.world.item.CreativeModeTab.TABS.length; i++) {
                                if (net.minecraft.world.item.CreativeModeTab.TABS[i]
                                        == com.gytrinket.gytrinket.items.ModCreativeModeTabs.GY_TRINKET_TAB) {
                                    tabIndex = i;
                                    break;
                                }
                            }
                            try {
                                java.lang.reflect.Field f = net.minecraft.client.gui.screens.inventory
                                        .CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                                f.setAccessible(true);
                                f.setInt(null, tabIndex);
                            } catch (Exception ex) {
                                gytrinket.LOGGER.warn("[cmd] select creative tab failed", ex);
                            }
                            mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player));
                        }
                        case "playerpanel" -> {
                            // 演示数据只用真实属性键（否则面板会显示未本地化的 key）
                            java.util.Map<String, Double> attrs = new java.util.LinkedHashMap<>();
                            attrs.put("attack_damage", 12.5D);
                            attrs.put("shield_base", 120.0D);
                            attrs.put("shield_damage_reduction", 0.25D);
                            attrs.put("attack_speed_percent", 1.15D);
                            attrs.put("construct_count_base", 3.0D);
                            // 装备区演示数据：4 件真实物品（NBT 走 ItemStack.of 反序列化）
                            String[] demoIds = {"gytrinket:shield_gy3", "gytrinket:shield_amplifier3",
                                    "gytrinket:attack_frame", "gytrinket:construct_frame"};
                            net.minecraft.nbt.ListTag demoItems = new net.minecraft.nbt.ListTag();
                            for (int i = 0; i < demoIds.length; i++) {
                                net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
                                t.putString("id", demoIds[i]);
                                t.putByte("Count", (byte) 1);
                                t.putByte("slot", (byte) i);
                                demoItems.add(t);
                            }
                            mc.setScreen(new com.gytrinket.gytrinket.client.screen.PlayerPanelScreen(
                                    attrs, demoItems, 6,
                                    new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.ListTag(),
                                    8, 12, 3, 150, 2, new String[0]));
                        }
                        case "upgradetarget" -> mc.setScreen(
                                new com.gytrinket.gytrinket.client.screen.UpgradeTargetScreen(
                                        null, new net.minecraft.nbt.ListTag()));
                        case "interceptor" -> mc.setScreen(
                                new com.gytrinket.gytrinket.client.screen.InterceptorWeaponSelectScreen(0));
                        case "interceptorammo" -> mc.setScreen(
                                new com.gytrinket.gytrinket.client.screen.InterceptorAmmoSelectScreen(0));
                        case "interceptorconfig" -> {
                            // 拦截机配置容器：客户端直接构造一份演示数据（entityId/武器槽均为空）
                            var inventory = mc.player.getInventory();
                            var container = new com.gytrinket.gytrinket.core.entity.construct.wingman
                                    .InterceptorConfigContainer(0, inventory,
                                    net.minecraft.world.item.ItemStack.EMPTY,
                                    net.minecraft.world.item.ItemStack.EMPTY, "melee");
                            mc.setScreen(new com.gytrinket.gytrinket.client.screen
                                    .InterceptorConfigContainerScreen(container, inventory,
                                    net.minecraft.network.chat.Component.translatable(
                                            "screen.gytrinket.interceptor_config")));
                        }
                        case "core" -> {
                            // 光点核心方块界面：27 核心槽 + 玩家背包。
                            // screen core demo = 填入演示物品 + 注入禁用原因，用于验收禁用遮罩层级
                            var inventory = mc.player.getInventory();
                            var container = new net.minecraft.world.SimpleContainer(27);
                            if (p.length > 2 && "demo".equals(p[2])) {
                                String[] demoIds = {"gytrinket:shield_gy3", "gytrinket:assault_module",
                                        "gytrinket:charged_attack_module", "gytrinket:burst_fire_module",
                                        "gytrinket:journey_module", "gytrinket:attack_frame"};
                                for (int i = 0; i < demoIds.length; i++) {
                                    var rl = new net.minecraft.resources.ResourceLocation(demoIds[i]);
                                    var item = net.minecraft.core.Registry.ITEM.get(rl);
                                    container.setItem(i, new net.minecraft.world.item.ItemStack(item, i == 0 ? 16 : 1));
                                }
                                java.util.List<String> reasons = new java.util.ArrayList<>();
                                for (int i = 0; i < 27; i++) {
                                    reasons.add(i < 3 ? "演示禁用" : "");
                                }
                                com.gytrinket.gytrinket.client.datacenter.ClientDataCenter
                                        .updateDisabledReasons(reasons);
                            }
                            var menu = new com.gytrinket.gytrinket.menu.LightPointCoreMenu(0, inventory, container);
                            mc.setScreen(new com.gytrinket.gytrinket.client.screen.LightPointCoreScreen(
                                    menu, inventory,
                                    net.minecraft.network.chat.Component.translatable(
                                            "block.gytrinket.light_point_core")));
                        }
                        default -> gytrinket.LOGGER.warn("[cmd] unknown screen {}", screenId);
                    }
                    gytrinket.LOGGER.info("[cmd] screen {}", screenId);
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
                    com.gytrinket.gytrinket.config.ClientConfig.SHIELD_PARTICLE_STYLE.set(style);
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
                case "hudmode" -> {
                    // 切换护盾 HUD 样式：hudmode 0 = 默认纯色长条，hudmode 1 = 原版纹理样式
                    boolean vanilla = p.length > 1 && !"0".equals(p[1]);
                    com.gytrinket.gytrinket.config.ClientConfig.VANILLA_STYLE_HUD.set(vanilla);
                    gytrinket.LOGGER.info("[cmd] hudmode vanilla={}", vanilla);
                }
                case "key" -> {
                    // 模拟按键：key attack 0|1（左键）、key use 0|1（右键）。
                    // KeyMapping#setDown 会累加点击计数，原版 handleKeybinds 下一 tick 就会触发攻击/使用。
                    String name = p.length > 1 ? p[1] : "attack";
                    boolean down = p.length > 2 && !"0".equals(p[2]);
                    net.minecraft.client.KeyMapping mapping = switch (name) {
                        case "attack" -> mc.options.keyAttack;
                        case "use" -> mc.options.keyUse;
                        case "jump" -> mc.options.keyJump;
                        case "sprint" -> mc.options.keySprint;
                        default -> null;
                    };
                    if (mapping == null) {
                        gytrinket.LOGGER.warn("[cmd] key: unknown mapping {}", name);
                    } else {
                        mapping.setDown(down);
                        gytrinket.LOGGER.info("[cmd] key {} down={}", name, down);
                    }
                }
                case "keyclick" -> {
                    // 模拟一次真实点击（KeyMapping.click 累加 clickCount，下一 tick 原版 handleKeybinds 走 startAttack）
                    String name = p.length > 1 ? p[1] : "attack";
                    int times = p.length > 2 ? Math.max(1, Math.min(20, Integer.parseInt(p[2]))) : 1;
                    com.mojang.blaze3d.platform.InputConstants.Key key = switch (name) {
                        case "attack" -> com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);
                        case "use" -> com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1);
                        // 键盘键：tab（阵列切换）/ h（玩家面板）/ i（斩杀开关）
                        case "tab" -> com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                                .getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_TAB);
                        case "h" -> com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                                .getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_H);
                        case "i" -> com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                                .getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_I);
                        default -> null;
                    };
                    if (key == null) {
                        gytrinket.LOGGER.warn("[cmd] keyclick: unknown mapping {}", name);
                    } else {
                        for (int i = 0; i < times; i++) {
                            net.minecraft.client.KeyMapping.click(key);
                        }
                        gytrinket.LOGGER.info("[cmd] keyclick {} x{}", name, times);
                    }
                }
                case "panel" -> {
                    // 打开真实玩家面板（等价于按 H）：向服务端请求面板数据
                    com.gytrinket.gytrinket.network.NetworkHandler.INSTANCE.sendToServer(
                            new com.gytrinket.gytrinket.network.packet.RequestPanelDataMessage());
                    gytrinket.LOGGER.info("[cmd] panel request sent");
                }
                case "attackinfo" -> {
                    // 诊断：攻击模式客户端判定链路（快照物品 / 模块集合 / 充能/强袭状态）
                    var snap = com.gytrinket.gytrinket.client.datacenter.ClientDataCenter.getSnapshot();
                    gytrinket.LOGGER.info("[attackinfo] snapshotSlots={} curiosSlots={}",
                            snap.getSlotCount(), snap.getCuriosSlotCount());
                    for (int i = 0; i < snap.getSlotCount(); i++) {
                        var st = snap.getItemInSlot(i);
                        if (st.isEmpty()) continue;
                        var item = st.getItem();
                        gytrinket.LOGGER.info("[attackinfo] slot={} item={} assault={} charged={} electric={}",
                                i, net.minecraft.core.Registry.ITEM.getKey(item),
                                com.gytrinket.gytrinket.config.Config.isAssaultItem(item),
                                com.gytrinket.gytrinket.config.Config.isChargedAttackItem(item),
                                com.gytrinket.gytrinket.config.Config.isElectricDischargeItem(item));
                    }
                    gytrinket.LOGGER.info("[attackinfo] hasAssault={} hasCharged={} charging={} assaultMode={} comboCooldown={} burstFiring={} leftDown={}",
                            com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil.hasAssaultItem(),
                            com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil.hasChargedAttackItem(),
                            com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackInputHandler.isCharging(),
                            com.gytrinket.gytrinket.client.attack_mode.assault.AssaultInputHandler.isAssaultMode(),
                            com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler
                                    .isInComboCooldown(mc.player.getUUID()),
                            com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler
                                    .isBurstFiring(mc.player.getUUID()),
                            mc.options.keyAttack.isDown());
                }
                case "persp" -> {
                    // 切换视角：0 = 第一人称，1 = 第三人称背后，2 = 第三人称正面（验收构造体/护盾用）
                    int mode = p.length > 1 ? Integer.parseInt(p[1]) : 1;
                    mc.options.setCameraType(net.minecraft.client.CameraType.values()[
                            Math.max(0, Math.min(2, mode))]);
                    gytrinket.LOGGER.info("[cmd] persp {}", mc.options.getCameraType());
                }
                case "entities" -> {
                    // 列出身周实体（验收用：确认服务端实体是否真的同步到客户端）
                    double radius = p.length > 1 ? Double.parseDouble(p[1]) : 32.0D;
                    java.util.List<net.minecraft.world.entity.Entity> list = mc.level.getEntities(
                            mc.player, mc.player.getBoundingBox().inflate(radius), e -> true);
                    list.sort(java.util.Comparator.comparingDouble(e -> e.distanceTo(mc.player)));
                    gytrinket.LOGGER.info("[entities] radius={} count={}", radius, list.size());
                    for (net.minecraft.world.entity.Entity e : list) {
                        gytrinket.LOGGER.info("[entities] {} id={} pos={} dist={} invisible={} removed={}",
                                net.minecraft.core.Registry.ENTITY_TYPE.getKey(e.getType()), e.getId(),
                                String.format("%.2f/%.2f/%.2f", e.getX(), e.getY(), e.getZ()),
                                String.format("%.2f", e.distanceTo(mc.player)),
                                e.isInvisible(), e.isRemoved());
                    }
                }
                case "hudshield" -> {
                    // 注入 HUD 数值（纯客户端渲染验收；真实同步链路另行测）
                    double cur = p.length > 1 ? Double.parseDouble(p[1]) : 80.0D;
                    double max = p.length > 2 ? Double.parseDouble(p[2]) : 120.0D;
                    double adaptive = p.length > 3 ? Double.parseDouble(p[3]) : 0.15D;
                    com.gytrinket.gytrinket.client.shield.ShieldHudRenderer.getInstance()
                            .updateShieldData(cur, max, adaptive);
                    gytrinket.LOGGER.info("[cmd] hudshield current={} max={} adaptive={}", cur, max, adaptive);
                }
                case "click" -> {
                    // 通用界面点击（像素坐标）：验证按钮/行点击
                    int cx = Integer.parseInt(p[1]);
                    int cy = Integer.parseInt(p[2]);
                    int btn = p.length > 3 ? Integer.parseInt(p[3]) : 0;
                    boolean handled = mc.screen != null && mc.screen.mouseClicked(cx, cy, btn);
                    gytrinket.LOGGER.info("[cmd] click {}/{} btn={} screen={} handled={}",
                            cx, cy, btn, mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(), handled);
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

    /** 把"创建新的世界"界面切到指定世界类型（flat/amplified/...），模拟用户点世界类型按钮。 */
    private static void applyWorldPreset(String presetId) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cws)) {
            gytrinket.LOGGER.info("[worldcheck] apply preset skipped（screen 不是 CreateWorldScreen）");
            return;
        }
        try {
            var component = cws.worldGenSettingsComponent;
            var presets = component.registryHolder()
                    .registryOrThrow(net.minecraft.core.Registry.WORLD_PRESET_REGISTRY);
            var key = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.Registry.WORLD_PRESET_REGISTRY,
                    net.minecraft.resources.ResourceLocation.tryParse(
                            presetId.contains(":") ? presetId : "minecraft:" + presetId));
            var holder = presets.getHolderOrThrow(key);
            var updaterClass = Class.forName(
                    "net.minecraft.client.gui.screens.worldselection.WorldCreationContext$SimpleUpdater");
            var method = component.getClass().getDeclaredMethod("updateSettings", updaterClass);
            method.setAccessible(true);
            net.minecraft.client.gui.screens.worldselection.WorldCreationContext.SimpleUpdater updater =
                    settings -> holder.value().recreateWorldGenSettings(settings);
            method.invoke(component, updater);
            gytrinket.LOGGER.info("[worldcheck] applied world preset {}", key.location());
        } catch (Throwable t) {
            gytrinket.LOGGER.error("[worldcheck] apply preset failed", t);
        }
    }

    /** 打印"创建新的世界"界面的 worldgen lifecycle（stable = 点创建不会弹实验性确认框）与维度表。 */
    private static void logWorldCreationStability(Minecraft mc) {
        try {
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cws)) {
                gytrinket.LOGGER.info("[worldcheck] screen={}（不是 CreateWorldScreen）",
                        mc.screen == null ? "null" : mc.screen.getClass().getName());
                return;
            }
            var context = cws.worldGenSettingsComponent.settings();
            com.mojang.serialization.Lifecycle stability = context.worldSettingsStability();
            boolean stable = stability == com.mojang.serialization.Lifecycle.stable();
            java.util.List<String> dimensions = new java.util.ArrayList<>();
            for (var key : context.worldGenSettings().dimensions().keySet()) {
                dimensions.add(key.toString());
            }
            java.util.Collections.sort(dimensions);
            gytrinket.LOGGER.info("[worldcheck] stability={} stable={} dimensions={}", stability, stable, dimensions);

        } catch (Exception e) {
            gytrinket.LOGGER.error("[worldcheck] failed", e);
        }
    }

    private static BlockState blockByName(String id) {
        String full = id.contains(":") ? id : "minecraft:" + id;
        var rl = net.minecraft.resources.ResourceLocation.tryParse(full);
        var block = net.minecraft.core.Registry.BLOCK.get(rl);
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
