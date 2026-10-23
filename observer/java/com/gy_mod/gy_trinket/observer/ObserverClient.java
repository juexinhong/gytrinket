package com.gy_mod.gy_trinket.observer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 客户端观察工具：
 * <ul>
 *   <li>{@code run/observer_cmd.txt}：逐行执行 {@code cam/shot/fov/hud/tp/cmd}；</li>
 *   <li>HTTP API（默认 127.0.0.1:25567）：/camera /screenshot /pos /fov /hud /cmd。</li>
 * </ul>
 * 1.20.1 适配：Forge 的 {@code @Mod} 没有 dist 参数，改用 {@code @Mod.EventBusSubscriber}，
 * 由 Forge 在客户端侧自动注册（服务端不会加载这个类，因此不会碰到客户端专属类）。
 */
@Mod.EventBusSubscriber(modid = ObserverMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ObserverClient {

    private static SimpleHttpServer http;
    private static boolean started;
    private static long lastScreenshot;

    private ObserverClient() {
    }

    /** 1.20.1 用 TickEvent.ClientTickEvent（1.21.1 是 ClientTickEvent.Post）；只处理 END 阶段。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!started) {
            started = true;
            startHttp(minecraft);
        }
        pollConsole(minecraft);
    }

    private static synchronized void startHttp(Minecraft minecraft) {
        if (http != null) {
            return;
        }
        int port = Integer.getInteger("observer.client.http.port", 25567);
        Map<String, SimpleHttpServer.Handler> routes = new HashMap<>();
        routes.put("/pos", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> posJson(minecraft))));
        routes.put("/camera", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> camera(minecraft, query))));
        routes.put("/screenshot", (query, body) -> SimpleHttpServer.Response.json(screenshot(minecraft, query.getOrDefault("name", "obs_" + System.currentTimeMillis()))));
        routes.put("/fov", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> fov(minecraft, query))));
        routes.put("/hud", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> hud(minecraft, query))));
        routes.put("/cmd", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> clientCommand(minecraft, query, body))));
        routes.put("/hand", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> hand(minecraft, query))));
        routes.put("/reload", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> reload(minecraft))));
        routes.put("/renderdistance", (query, body) -> SimpleHttpServer.Response.json(onClient(minecraft, () -> renderDistance(minecraft, query))));
        routes.put("/fps", (query, body) -> SimpleHttpServer.Response.json("{\"fps\":" + Minecraft.getInstance().getFps() + "}"));
        try {
            http = new SimpleHttpServer(port, routes);
            ObserverMod.LOGGER.info("[observer] client mod initialized");
        } catch (Exception exception) {
            ObserverMod.LOGGER.error("[observer] failed to start client HTTP server", exception);
        }
    }

    private static <T> T onClient(Minecraft minecraft, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void pollConsole(Minecraft minecraft) {
        Path file = minecraft.gameDirectory.toPath().resolve("observer_cmd.txt");
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
            Files.deleteIfExists(file);
        } catch (Exception exception) {
            return;
        }
        for (String line : lines) {
            String command = line.trim();
            if (!command.isEmpty() && !command.startsWith("#")) {
                execute(minecraft, command);
            }
        }
    }

    private static void execute(Minecraft minecraft, String line) {
        String[] parts = line.split("\\s+");
        try {
            switch (parts[0]) {
                case "cam" -> {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    float yaw = Float.parseFloat(parts[4]);
                    float pitch = Float.parseFloat(parts[5]);
                    setCamera(minecraft, x, y, z, yaw, pitch);
                }
                case "tp" -> {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    setCamera(minecraft, x, y, z, minecraft.player.getYRot(), minecraft.player.getXRot());
                }
                case "shot" -> screenshotNow(minecraft, parts.length > 1 ? parts[1] : "shot");
                case "fov" -> minecraft.options.fov().set((int) Float.parseFloat(parts[1]));
                case "hud" -> minecraft.options.hideGui = parts.length > 1 && parts[1].equalsIgnoreCase("off");
                case "hand" -> minecraft.gameRenderer.setRenderHand(parts.length > 1 && parts[1].equalsIgnoreCase("on"));
                case "reload" -> reload(minecraft);
                case "cmd" -> minecraft.player.connection.sendCommand(line.substring(4).trim());
                default -> ObserverMod.LOGGER.warn("[observer] unknown console command: {}", line);
            }
        } catch (Exception exception) {
            ObserverMod.LOGGER.warn("[observer] console command failed: {} ({})", line, exception.toString());
        }
    }

    private static void setCamera(Minecraft minecraft, double x, double y, double z, float yaw, float pitch) {
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        minecraft.player.getAbilities().mayfly = true;
        minecraft.player.getAbilities().flying = true;
        minecraft.player.getAbilities().instabuild = true;
        minecraft.player.onUpdateAbilities();
        minecraft.player.setPos(x, y, z);
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
        minecraft.player.setOldPosAndRot();
        ObserverMod.LOGGER.info("[observer] camera {} {} {} {} {}", x, y, z, yaw, pitch);
    }

    private static void screenshotNow(Minecraft minecraft, String name) {
        // 1.20.1：Screenshot.grab(File, String, RenderTarget, Consumer<Component>)
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), component -> {
        });
    }

    private static String screenshot(Minecraft minecraft, String name) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String safeName = name.replaceAll("[^a-zA-Z0-9_.-]", "_");
        minecraft.execute(() -> {
            try {
                Screenshot.grab(minecraft.gameDirectory, safeName + ".png", minecraft.getMainRenderTarget(),
                        component -> future.complete(new File(minecraft.gameDirectory, "screenshots/" + safeName + ".png").getAbsolutePath()));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        try {
            String path = future.get(15, TimeUnit.SECONDS);
            lastScreenshot = System.currentTimeMillis();
            return "{\"path\":\"" + SimpleHttpServer.escape(path) + "\",\"name\":\"" + SimpleHttpServer.escape(safeName) + "\"}";
        } catch (Exception exception) {
            throw new RuntimeException("screenshot failed: " + exception);
        }
    }

    private static String posJson(Minecraft minecraft) {
        var player = minecraft.player;
        return "{\"dimension\":\"" + SimpleHttpServer.escape(player.level().dimension().location().toString())
                + "\",\"x\":" + player.getX() + ",\"y\":" + player.getY() + ",\"z\":" + player.getZ()
                + ",\"yaw\":" + player.getYRot() + ",\"pitch\":" + player.getXRot()
                + ",\"fov\":" + minecraft.options.fov().get()
                + ",\"hud\":" + !minecraft.options.hideGui + "}";
    }

    private static String camera(Minecraft minecraft, Map<String, String> query) {
        double x = Double.parseDouble(query.getOrDefault("x", String.valueOf(minecraft.player.getX())));
        double y = Double.parseDouble(query.getOrDefault("y", String.valueOf(minecraft.player.getY())));
        double z = Double.parseDouble(query.getOrDefault("z", String.valueOf(minecraft.player.getZ())));
        float yaw = Float.parseFloat(query.getOrDefault("yaw", String.valueOf(minecraft.player.getYRot())));
        float pitch = Float.parseFloat(query.getOrDefault("pitch", String.valueOf(minecraft.player.getXRot())));
        setCamera(minecraft, x, y, z, yaw, pitch);
        return "{\"ok\":true}";
    }

    private static String fov(Minecraft minecraft, Map<String, String> query) {
        int value = Integer.parseInt(query.getOrDefault("value", "70"));
        minecraft.options.fov().set(value);
        return "{\"fov\":" + minecraft.options.fov().get() + "}";
    }

    private static String hand(Minecraft minecraft, Map<String, String> query) {
        boolean hidden = Boolean.parseBoolean(query.getOrDefault("hidden", "true"));
        minecraft.gameRenderer.setRenderHand(!hidden);
        return "{\"hidden\":" + hidden + "}";
    }

    private static String hud(Minecraft minecraft, Map<String, String> query) {
        boolean hidden = Boolean.parseBoolean(query.getOrDefault("hidden", "true"));
        minecraft.options.hideGui = hidden;
        return "{\"hidden\":" + hidden + "}";
    }

    private static String reload(Minecraft minecraft) {
        minecraft.reloadResourcePacks();
        ObserverMod.LOGGER.info("[observer] resource reload requested");
        return "{\"reloading\":true}";
    }

    private static String renderDistance(Minecraft minecraft, Map<String, String> query) {
        int value = Integer.parseInt(query.getOrDefault("value", "32"));
        minecraft.options.renderDistance().set(value);
        minecraft.options.save();
        ObserverMod.LOGGER.info("[observer] render distance set to {}", value);
        return "{\"renderDistance\":" + minecraft.options.renderDistance().get() + "}";
    }

    private static String clientCommand(Minecraft minecraft, Map<String, String> query, String body) {
        String command = query.get("cmd");
        if (command == null || command.isEmpty()) {
            command = body == null ? "" : body.trim();
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("missing command");
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        return "{\"ok\":true,\"command\":\"" + SimpleHttpServer.escape(command) + "\"}";
    }
}
