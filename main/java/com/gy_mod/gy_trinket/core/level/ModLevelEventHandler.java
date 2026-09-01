package com.gy_mod.gy_trinket.core.level;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 光点等级事件处理器
 * 光点经验来源：实体死亡掉落的经验。
 * 实体死亡时（LivingExperienceDropEvent，含抢夺等修正后的最终掉落量），
 * 将掉落经验直接平分给死亡位置 20 格内的所有存活玩家（作为光点经验），
 * 不再监听玩家的实际拾取/原版经验值变化。
 * 因此其他模组"储存经验再取出回流"的经验球、附魔瓶、熔炉/钓鱼/繁殖等
 * 非实体死亡来源的经验均不会计入光点经验，杜绝此类刷取途径。
 * 例外后门：指令给予的经验（/experience add 或 /xp add）也计入光点经验。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ModLevelEventHandler {

    private ModLevelEventHandler() {}

    /** 服务端完全启动（数据包已加载）：读取并应用运行时覆盖层 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DefsManager.applyOverrides(server);
    }

    /** 玩家登录时同步光点等级数据与定义数据到客户端（HUD、面板、提示等需要初始值） */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendModLevelSyncToPlayer(player);
            NetworkHandler.sendDefsSyncToPlayer(player);
            com.gy_mod.gy_trinket.core.random_build.RandomBuildManager.clearPlayerData(player.getUUID());
        }
    }

    /**
     * 实体死亡掉落经验：直接将掉落经验平分给死亡位置 20 格内的所有存活玩家（作为光点经验）。
     * 经验值为最终掉落量（含抢夺附魔修正），平分向下取整，余数舍弃。
     */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        int total = event.getDroppedExperience();
        if (total <= 0) {
            return;
        }

        // 收集死亡位置 20 格内的存活玩家
        List<ServerPlayer> nearby = event.getEntity().level().getEntitiesOfClass(
                ServerPlayer.class,
                event.getEntity().getBoundingBox().inflate(20.0),
                p -> p.isAlive() && p.distanceToSqr(event.getEntity()) <= 20.0 * 20.0
        );
        if (nearby.isEmpty()) {
            return;
        }

        int share = total / nearby.size();
        if (share <= 0) {
            return;
        }

        for (ServerPlayer player : nearby) {
            ModLevelManager.addUpgradeExp(player.getUUID(), share);
        }
    }

    /**
     * 后门：指令给予的经验也计入光点经验。
     * 匹配 /experience add 与 /xp add 指令（含数据包函数内调用）：
     * points 给予按点数直接入账；levels 给予按每级升级所需经验逐级换算后入账。
     */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.isCanceled()) {
            return;
        }
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            CommandContext<CommandSourceStack> ctx = parse.getContext().build(parse.getReader().getString());
            // 通过已解析的指令节点判断：根为 experience/xp 且子命令为 add
            List<String> literals = ctx.getNodes().stream()
                    .map(node -> node.getNode().getName())
                    .toList();
            if (!literals.contains("add") || !(literals.contains("experience") || literals.contains("xp"))) {
                return;
            }
            boolean levels = literals.contains("levels");
            int amount = IntegerArgumentType.getInteger(ctx, "amount");
            if (amount <= 0) {
                return;
            }
            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
                int points = levels ? levelsToPoints(player, amount) : amount;
                if (points > 0) {
                    ModLevelManager.addUpgradeExp(player.getUUID(), points);
                }
            }
        } catch (Exception ignored) {
            // 参数缺失或类型不符时静默忽略，不影响指令本身执行
        }
    }

    /** 将指令给予的等级按原版每级升级所需经验逐级换算为点数（最多累计 1000 级，防极端数值卡顿） */
    private static int levelsToPoints(ServerPlayer player, int levels) {
        int total = 0;
        int start = Math.max(0, player.experienceLevel);
        int count = Math.min(levels, 1000);
        for (int i = 0; i < count; i++) {
            int lvl = start + i;
            total += lvl >= 30 ? 112 + (lvl - 30) * 9 : (lvl >= 15 ? 37 + (lvl - 15) * 5 : 7 + lvl * 2);
        }
        return total;
    }
}
