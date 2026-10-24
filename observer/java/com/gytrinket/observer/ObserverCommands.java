package com.gytrinket.observer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** /obs 服务端命令：状态、方块查询、区域统计、俯视渲染、结构导出、脚本执行。 */
public final class ObserverCommands {

    private ObserverCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("obs")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("block")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> block(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("scan")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> scan(context.getSource(),
                                                BlockPosArgument.getBlockPos(context, "from"),
                                                BlockPosArgument.getBlockPos(context, "to"))))))
                .then(Commands.literal("top")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> top(context.getSource(),
                                                BlockPosArgument.getBlockPos(context, "from"),
                                                BlockPosArgument.getBlockPos(context, "to"), 2))
                                        .then(Commands.argument("scale", IntegerArgumentType.integer(1, 8))
                                                .executes(context -> top(context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to"),
                                                        IntegerArgumentType.getInteger(context, "scale")))))))
                .then(Commands.literal("dump")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> dump(context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to"),
                                                        StringArgumentType.getString(context, "name")))))))
                .then(Commands.literal("exec")
                        .then(Commands.argument("file", StringArgumentType.word())
                                .executes(context -> exec(context.getSource(),
                                        StringArgumentType.getString(context, "file")))))
                .then(Commands.literal("defs")
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(context -> defs(context.getSource(),
                                        StringArgumentType.getString(context, "item")))))
                .then(Commands.literal("reloaddefs")
                        .executes(context -> reloadDefs(context.getSource()))));
    }

    /** 从当前 ResourceManager 重读 item_definitions 并立即应用（开发期改数据文件后无需重启） */
    private static int reloadDefs(CommandSourceStack source) {
        com.gytrinket.gytrinket.core.defs.DefsManager.applyOverrides(source.getServer());
        source.sendSuccess(() -> Component.literal("item_definitions 已从资源管理器重新读取并应用"), false);
        return 1;
    }

    /**
     * 打印物品的数据驱动定义解析结果（服务端视角）：
     * 护盾类型列表 + 每个护盾类型参数的实际取值 + 机制集合 + 机制参数实际取值。
     * fallback 用 NaN 标记，解析结果仍为 NaN 表示数据文件/类型定义/UI 覆写都没有声明该键。
     */
    private static int defs(CommandSourceStack source, String item) {
        var server = source.getServer();
        StringBuilder out = new StringBuilder("item=").append(item).append('\n');
        List<String> types = com.gytrinket.gytrinket.core.defs.DefsManager.getItemShieldTypes()
                .getOrDefault(item, List.of());
        out.append("shieldTypes=").append(types).append('\n');
        for (String type : types) {
            for (var def : com.gytrinket.gytrinket.core.defs.ShieldValueDefs.getParams(type)) {
                double value = com.gytrinket.gytrinket.core.defs.DefsManager.resolveShieldTypeValueForItem(
                        server, item, type, def.key(), Double.NaN);
                out.append("  shield.").append(type).append('.').append(def.key()).append('=')
                        .append(Double.isNaN(value) ? "unset" : Double.toString(value)).append('\n');
            }
        }
        List<String> sets = com.gytrinket.gytrinket.core.defs.DefsManager
                .getEffectiveSpecialMechanicSets(server, item);
        out.append("mechanicSets=").append(sets).append('\n');
        for (String set : sets) {
            for (var def : com.gytrinket.gytrinket.core.defs.MechanicValueDefs.getParams(set)) {
                double value = com.gytrinket.gytrinket.core.defs.DefsManager.resolveMechanicValueForItem(
                        server, item, set, def.key(), Double.NaN);
                out.append("  mechanic.").append(set).append('.').append(def.key()).append('=')
                        .append(Double.isNaN(value) ? "unset" : Double.toString(value)).append('\n');
            }
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        var server = source.getServer();
        double tps = Math.min(20.0, 1_000_000_000.0 / Math.max(1.0, server.getAverageTickTimeNanos()));
        StringBuilder out = new StringBuilder();
        out.append("tps=").append(String.format("%.2f", tps)).append('\n');
        out.append("players=").append(server.getPlayerList().getPlayerCount()).append('\n');
        out.append("dimension=").append(source.getLevel().dimension().location()).append('\n');
        out.append("time=").append(source.getLevel().getDayTime()).append('\n');
        out.append("day=").append(source.getLevel().getDayTime() / 24000L).append('\n');
        out.append("raining=").append(source.getLevel().isRaining()).append('\n');
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int block(CommandSourceStack source, BlockPos pos) {
        BlockState state = source.getLevel().getBlockState(pos);
        String text = pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + ObserverRenderer.stateString(state);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int scan(CommandSourceStack source, BlockPos from, BlockPos to) {
        ServerLevel level = source.getLevel();
        Map<String, Integer> histogram = ObserverRenderer.scan(level,
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), 1_000_000);
        String text = ObserverRenderer.histogramText(histogram, 25);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int top(CommandSourceStack source, BlockPos from, BlockPos to, int scale) {
        try {
            ServerLevel level = source.getLevel();
            var image = ObserverRenderer.top(level, from.getX(), from.getZ(), to.getX(), to.getZ(),
                    Math.min(from.getY(), to.getY()), Math.max(from.getY(), to.getY()), scale);
            Path out = level.getServer().getServerDirectory().resolve("obs_top.png");
            ImageIO.write(image, "png", out.toFile());
            source.sendSuccess(() -> Component.literal("wrote " + out.toAbsolutePath()), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("top failed: " + exception));
            return 0;
        }
    }

    private static int dump(CommandSourceStack source, BlockPos from, BlockPos to, String name) {
        try {
            byte[] bytes = ObserverRenderer.dump(source.getLevel(),
                    from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
            Path out = source.getServer().getServerDirectory().resolve("obs_" + name + ".nbt");
            Files.write(out, bytes);
            source.sendSuccess(() -> Component.literal("wrote " + out.toAbsolutePath() + " (" + bytes.length + " bytes)"), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("dump failed: " + exception));
            return 0;
        }
    }

    private static int exec(CommandSourceStack source, String name) {
        Path file = source.getServer().getServerDirectory().resolve("obs_" + name + ".txt");
        try {
            List<String> lines = Files.readAllLines(file);
            int count = 0;
            for (String line : lines) {
                String command = line.trim();
                if (command.isEmpty() || command.startsWith("#")) {
                    continue;
                }
                source.getServer().getCommands().performPrefixedCommand(source, command);
                count++;
            }
            int total = count;
            source.sendSuccess(() -> Component.literal("executed " + total + " commands from " + file), false);
            return count;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("exec failed: " + exception));
            return 0;
        }
    }
}
