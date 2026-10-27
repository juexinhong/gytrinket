package com.gytrinket.gytrinket.core.level;

import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
@EventBusSubscriber(modid = gytrinket.MODID)
public class ModLevelEventHandler {

    private ModLevelEventHandler() {}

    /**
     * 服务器启动完成：读取运行时覆盖文件并合并进定义集合。
     * 数据包重载阶段 getCurrentServer() 为 null 会跳过覆盖文件，必须在此处补一次加载，
     * 否则配置面板编辑的内容在重启后会丢失。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        DefsManager.applyOverrides(event.getServer());
    }

    /** 玩家登录时同步光点等级数据到客户端（HUD 提示等需要初始值） */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendModLevelSyncToPlayer(player);
            // 同步运行时定义覆盖层（特殊机制/护盾类型）到客户端，重启后面板与提示保持生效状态
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            // 登录时单播定义覆盖层+物品属性表，填充客户端 AttributeManager 静态属性表
            // （tooltip 与客户端属性逻辑无需玩家手动打开一次配置界面；客户端主动拉取为主路径）
            com.gytrinket.gytrinket.network.packet.ConfigDefsSyncPayload.sendDefinitionsToPlayer(player);
            com.gytrinket.gytrinket.core.random_build.RandomBuildManager.clearPlayerData(player.getUUID());
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
     * <p>
     * 注意：1.19.2/1.20.1/1.21.1 的 `/xp` 都是 Brigadier 的 {@code redirect} 到
     * `experience`，原始 ParseResults 只会记录 redirect 节点（nodes=[xp]），
     * 不包含 add/targets/amount；直接读 ctx 会静默失败。因此当节点不完整时，
     * 把首词换成规范名 `experience` 重新解析一次，两个别名共用同一套参数读取。
     */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.isCanceled()) {
            return;
        }
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            CommandSourceStack source = parse.getContext().getSource();
            String raw = parse.getReader().getString().trim();
            if (raw.startsWith("/")) {
                raw = raw.substring(1);
            }

            // 1) 常规路径：/experience add ...（以及 /execute run experience add ... 这类嵌套）
            CommandContext<CommandSourceStack> ctx = parse.getContext().build(raw);
            List<String> literals = nodeNames(ctx);
            if (literals.contains("add")
                    && (literals.contains("experience") || literals.contains("xp"))) {
                applyAddCommand(ctx, literals);
                return;
            }

            // 2) 别名路径：/xp 是 redirect，parse 只记录到 [xp]，需要换成 experience 再解析
            int space = raw.indexOf(' ');
            String head = space < 0 ? raw : raw.substring(0, space);
            if (!"xp".equals(head) && !"experience".equals(head)) {
                return;
            }
            String canonical = "experience" + (space < 0 ? "" : raw.substring(space));
            CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
            ParseResults<CommandSourceStack> resolved = dispatcher.parse(canonical, source);
            CommandContext<CommandSourceStack> resolvedCtx = resolved.getContext().build(canonical);
            List<String> resolvedNames = nodeNames(resolvedCtx);
            if (!resolvedNames.contains("add")) {
                return;
            }
            applyAddCommand(resolvedCtx, resolvedNames);
        } catch (Exception ignored) {
            // 参数缺失或类型不符时静默忽略，不影响指令本身执行
        }
    }

    private static List<String> nodeNames(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .map(node -> node.getNode().getName())
                .toList();
    }

    private static void applyAddCommand(CommandContext<CommandSourceStack> ctx, List<String> literals)
            throws CommandSyntaxException {
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
