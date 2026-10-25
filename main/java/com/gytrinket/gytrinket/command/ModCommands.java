package com.gytrinket.gytrinket.command;

import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 正式发布命令：只保留玩家使用的 {@code /gytrinket resetlevel}。
 * <p>
 * render/top/view/dump/samples/build/buildtower 等开发辅助命令已迁移到
 * observer 开发模组的 {@code DevCommands}，不再进入正式 jar。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    private ModCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gytrinket")
                .then(Commands.literal("resetlevel")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetLevel(context.getSource()))));
    }

    private static int resetLevel(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.gytrinket.resetlevel.not_player"));
            return 0;
        }
        ModLevelManager.resetData(player.getUUID());
        source.sendSuccess(Component.translatable("command.gytrinket.resetlevel.success"), true);
        return 1;
    }
}
