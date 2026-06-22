package com.gytrinket.gytrinket.command;

import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ModCommands {

    private ModCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("gytrinket")
                .then(Commands.literal("resetlevel")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetLevel(context.getSource()))
                )
        );
    }

    private static int resetLevel(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.gytrinket.resetlevel.not_player"));
            return 0;
        }

        ModLevelManager.resetData(player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.gytrinket.resetlevel.success"), true);
        return 1;
    }
}
