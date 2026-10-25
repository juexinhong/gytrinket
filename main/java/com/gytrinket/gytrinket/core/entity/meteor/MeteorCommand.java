package com.gytrinket.gytrinket.core.entity.meteor;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * 开发/管理用陨石命令：
 * <pre>
 * /gytrinket meteor &lt;small|medium|large|huge&gt; [count]
 * /gytrinket meteorcluster &lt;small|medium|large|huge&gt;
 * </pre>
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorCommand {

    private static final Collection<String> SIZE_NAMES = Arrays.stream(MeteorSize.values())
            .map(size -> size.name().toLowerCase(Locale.ROOT))
            .toList();

    private MeteorCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("gytrinket")
                .then(Commands.literal("meteor")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("size", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SIZE_NAMES, builder))
                                .executes(ctx -> spawnMeteor(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> spawnMeteor(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("meteorcluster")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("size", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SIZE_NAMES, builder))
                                .executes(MeteorCommand::spawnCluster))));
    }

    private static int spawnMeteor(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        MeteorSize size = MeteorSize.byName(StringArgumentType.getString(ctx, "size"));
        if (size == null) {
            source.sendFailure(Component.literal("Unknown meteor size"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 base = source.getPosition().add(0.0, 40.0, 0.0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 16.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 16.0;
            Vec3 center = base.add(offsetX, level.random.nextDouble() * 8.0, offsetZ);
            if (MeteorSpawner.spawnOne(level, center, size, false)) {
                spawned++;
            }
        }

        int result = spawned;
        source.sendSuccess(Component.literal(
                "Spawned " + result + " " + size.id() + " meteor(s)"), true);
        return spawned;
    }

    private static int spawnCluster(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MeteorSize size = MeteorSize.byName(StringArgumentType.getString(ctx, "size"));
        if (size == null) {
            source.sendFailure(Component.literal("Unknown meteor size"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 center = source.getPosition().add(0.0, 60.0, 0.0);
        boolean primary = MeteorSpawner.spawnOne(level, center, size, false);
        int members = MeteorSpawner.spawnCluster(level, center, size, false);

        source.sendSuccess(Component.literal(
                "Meteor cluster: primary=" + primary + ", members=" + members), true);
        return members + (primary ? 1 : 0);
    }
}
