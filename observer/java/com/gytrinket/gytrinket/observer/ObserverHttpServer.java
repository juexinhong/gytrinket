package com.gytrinket.gytrinket.observer;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 服务端 HTTP 观察/操控接口（默认 127.0.0.1:25566）。 */
public final class ObserverHttpServer {

    private static SimpleHttpServer http;

    private ObserverHttpServer() {
    }

    public static synchronized void start(MinecraftServer server) {
        if (http != null) {
            return;
        }
        int port = Integer.getInteger("observer.http.port", 25566);
        Map<String, SimpleHttpServer.Handler> routes = new HashMap<>();
        routes.put("/status", (query, body) -> SimpleHttpServer.Response.json(onServer(server, () -> statusJson(server))));
        routes.put("/players", (query, body) -> SimpleHttpServer.Response.json(onServer(server, () -> playersJson(server))));
        routes.put("/block", (query, body) -> SimpleHttpServer.Response.json(onServer(server, () -> blockJson(server, query))));
        routes.put("/region", (query, body) -> SimpleHttpServer.Response.json(onServer(server, () -> regionJson(server, query))));
        routes.put("/command", (query, body) -> SimpleHttpServer.Response.json(onServer(server, () -> commandJson(server, query, body))));
        routes.put("/top", (query, body) -> new SimpleHttpServer.Response(200, "image/png",
                onServer(server, () -> renderTop(server, query))));
        routes.put("/dump", (query, body) -> new SimpleHttpServer.Response(200, "application/x-nbt",
                onServer(server, () -> dumpNbt(server, query))));
        try {
            http = new SimpleHttpServer(port, routes);
        } catch (Exception exception) {
            ObserverMod.LOGGER.error("[observer] failed to start HTTP server", exception);
        }
    }

    public static synchronized void stop() {
        if (http != null) {
            http.close();
            http = null;
        }
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static ServerLevel level(MinecraftServer server, Map<String, String> query) {
        String id = query.getOrDefault("dim", "minecraft:overworld");
        // 1.20.1：ResourceLocation 只有构造器，没有 fromNamespaceAndPath / parse
        ServerLevel level = server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(id)));
        if (level == null) {
            throw new IllegalArgumentException("unknown dimension: " + id);
        }
        return level;
    }

    private static int intParam(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter: " + name);
        }
        return Integer.parseInt(value);
    }

    private static String statusJson(MinecraftServer server) {
        // 1.20.1 只有 MinecraftServer#getAverageTickTime()（毫秒/刻），没有纳秒版本
        double tps = Math.min(20.0D, 1000.0D / Math.max(1.0D, server.getAverageTickTime()));
        StringJoiner dimensions = new StringJoiner(",");
        for (ServerLevel level : server.getAllLevels()) {
            dimensions.add("\"" + SimpleHttpServer.escape(level.dimension().location().toString()) + "\"");
        }
        return "{\"tps\":" + String.format("%.2f", tps)
                + ",\"players\":" + server.getPlayerList().getPlayerCount()
                + ",\"time\":" + server.overworld().getDayTime()
                + ",\"day\":" + (server.overworld().getDayTime() / 24000L)
                + ",\"raining\":" + server.overworld().isRaining()
                + ",\"thundering\":" + server.overworld().isThundering()
                + ",\"dimensions\":[" + dimensions + "]}";
    }

    private static String playersJson(MinecraftServer server) {
        StringJoiner players = new StringJoiner(",");
        server.getPlayerList().getPlayers().forEach(player -> players.add("{\"name\":\""
                + SimpleHttpServer.escape(player.getGameProfile().getName()) + "\",\"dimension\":\""
                + SimpleHttpServer.escape(player.getLevel().dimension().location().toString()) + "\",\"x\":"
                + player.getX() + ",\"y\":" + player.getY() + ",\"z\":" + player.getZ() + "}"));
        return "[" + players + "]";
    }

    private static String blockJson(MinecraftServer server, Map<String, String> query) {
        ServerLevel level = level(server, query);
        int x = intParam(query, "x");
        int y = intParam(query, "y");
        int z = intParam(query, "z");
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return "{\"dim\":\"" + SimpleHttpServer.escape(level.dimension().location().toString())
                + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                + ",\"state\":\"" + SimpleHttpServer.escape(ObserverRenderer.stateString(state)) + "\"}";
    }

    private static String regionJson(MinecraftServer server, Map<String, String> query) {
        ServerLevel level = level(server, query);
        int x0 = intParam(query, "x0");
        int y0 = intParam(query, "y0");
        int z0 = intParam(query, "z0");
        int x1 = intParam(query, "x1");
        int y1 = intParam(query, "y1");
        int z1 = intParam(query, "z1");
        int limit = Math.min(1_000_000, Integer.parseInt(query.getOrDefault("limit", "200000")));
        Map<String, Integer> histogram = ObserverRenderer.scan(level, x0, y0, z0, x1, y1, z1, limit);
        int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        StringJoiner blocks = new StringJoiner(",");
        for (Map.Entry<String, Integer> entry : ObserverRenderer.sorted(histogram)) {
            blocks.add("{\"id\":\"" + SimpleHttpServer.escape(entry.getKey()) + "\",\"n\":" + entry.getValue() + "}");
        }
        return "{\"count\":" + total + ",\"blocks\":[" + blocks + "]}";
    }

    private static String commandJson(MinecraftServer server, Map<String, String> query, String body) {
        String command = query.get("cmd");
        if (command == null || command.isEmpty()) {
            command = body == null ? "" : body.trim();
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("missing command");
        }
        String finalCommand = command.startsWith("/") ? command.substring(1) : command;
        int result;
        try {
            result = server.getCommands().getDispatcher().execute(finalCommand, server.createCommandSourceStack());
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("bad command: " + exception.getMessage());
        }
        return "{\"result\":" + result + ",\"command\":\"" + SimpleHttpServer.escape(finalCommand) + "\"}";
    }

    private static byte[] renderTop(MinecraftServer server, Map<String, String> query) {
        ServerLevel level = level(server, query);
        int x0 = intParam(query, "x0");
        int z0 = intParam(query, "z0");
        int x1 = intParam(query, "x1");
        int z1 = intParam(query, "z1");
        int y0 = Integer.parseInt(query.getOrDefault("y0", String.valueOf(level.getMinBuildHeight())));
        int y1 = Integer.parseInt(query.getOrDefault("y1", String.valueOf(level.getMaxBuildHeight() - 1)));
        int scale = Math.min(8, Math.max(1, Integer.parseInt(query.getOrDefault("scale", "2"))));
        try {
            return ObserverRenderer.png(ObserverRenderer.top(level, x0, z0, x1, z1, y0, y1, scale));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static byte[] dumpNbt(MinecraftServer server, Map<String, String> query) {
        ServerLevel level = level(server, query);
        int x0 = intParam(query, "x0");
        int y0 = intParam(query, "y0");
        int z0 = intParam(query, "z0");
        int x1 = intParam(query, "x1");
        int y1 = intParam(query, "y1");
        int z1 = intParam(query, "z1");
        try {
            return ObserverRenderer.dump(level, x0, y0, z0, x1, y1, z1);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /** 供命令/外部调用的维度解析。 */
    public static ServerLevel level(MinecraftServer server, String dimension) {
        return server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimension)));
    }

    /** 保留以方便调试：列出所有维度 id。 */
    public static List<String> dimensions(MinecraftServer server) {
        List<String> ids = new ArrayList<>();
        for (Level level : server.getAllLevels()) {
            ids.add(level.dimension().location().toString());
        }
        return ids;
    }
}
