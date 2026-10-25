package com.gytrinket.gytrinket.observer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("scan")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> scan(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                BlockPosArgument.getLoadedBlockPos(context, "to"))))))
                .then(Commands.literal("top")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> top(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                BlockPosArgument.getLoadedBlockPos(context, "to"), 2))
                                        .then(Commands.argument("scale", IntegerArgumentType.integer(1, 8))
                                                .executes(context -> top(context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "to"),
                                                        IntegerArgumentType.getInteger(context, "scale")))))))
                .then(Commands.literal("dump")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> dump(context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "to"),
                                                        StringArgumentType.getString(context, "name")))))))
                .then(Commands.literal("exec")
                        .then(Commands.argument("file", StringArgumentType.word())
                                .executes(context -> exec(context.getSource(),
                                        StringArgumentType.getString(context, "file"))))));
    }

    private static int status(CommandSourceStack source) {
        var server = source.getServer();
        // 1.20.1 的 MinecraftServer#getAverageTickTime() 返回「毫秒/刻」（1.21.1 是 getAverageTickTimeNanos()）
        double millisPerTick = Math.max(1.0D, server.getAverageTickTime());
        double tps = Math.min(20.0D, 1000.0D / millisPerTick);
        StringBuilder out = new StringBuilder();
        out.append("tps=").append(String.format("%.2f", tps)).append('\n');
        out.append("players=").append(server.getPlayerList().getPlayerCount()).append('\n');
        out.append("dimension=").append(source.getLevel().dimension().location()).append('\n');
        out.append("time=").append(source.getLevel().getDayTime()).append('\n');
        out.append("day=").append(source.getLevel().getDayTime() / 24000L).append('\n');
        out.append("raining=").append(source.getLevel().isRaining()).append('\n');
        source.sendSuccess(Component.literal(out.toString()), false);
        return 1;
    }

    private static int block(CommandSourceStack source, BlockPos pos) {
        BlockState state = source.getLevel().getBlockState(pos);
        String text = pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + ObserverRenderer.stateString(state);
        source.sendSuccess(Component.literal(text), false);
        return 1;
    }

    private static int scan(CommandSourceStack source, BlockPos from, BlockPos to) {
        ServerLevel level = source.getLevel();
        Map<String, Integer> histogram = ObserverRenderer.scan(level,
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), 1_000_000);
        String text = ObserverRenderer.histogramText(histogram, 25);
        source.sendSuccess(Component.literal(text), false);
        return 1;
    }

    private static int top(CommandSourceStack source, BlockPos from, BlockPos to, int scale) {
        try {
            ServerLevel level = source.getLevel();
            var image = ObserverRenderer.top(level, from.getX(), from.getZ(), to.getX(), to.getZ(),
                    Math.min(from.getY(), to.getY()), Math.max(from.getY(), to.getY()), scale);
            // 1.20.1：MinecraftServer#getServerDirectory() 返回 java.io.File（1.21.1 是 Path）
            File out = new File(level.getServer().getServerDirectory(), "obs_top.png");
            ImageIO.write(image, "png", out);
            source.sendSuccess(Component.literal("wrote " + out.getAbsolutePath()), false);
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
            File out = new File(source.getServer().getServerDirectory(), "obs_" + name + ".nbt");
            Files.write(out.toPath(), bytes);
            source.sendSuccess(Component.literal("wrote " + out.getAbsolutePath() + " (" + bytes.length + " bytes)"), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("dump failed: " + exception));
            return 0;
        }
    }

    private static int exec(CommandSourceStack source, String name) {
        File file = new File(source.getServer().getServerDirectory(), "obs_" + name + ".txt");
        Path path = file.toPath();
        try {
            List<String> lines = Files.readAllLines(path);
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
            source.sendSuccess(Component.literal("executed " + total + " commands from " + path), false);
            return count;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("exec failed: " + exception));
            return 0;
        }
    }
}
