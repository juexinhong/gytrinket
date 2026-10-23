package com.gytrinket.observer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 临时死亡诊断工具。
 * <p>
 * 用于排查“玩家血量到 0 但不死/退出重进仍然不死”的整合包问题。
 * 命令需要 OP/作弊权限 2：
 * <ul>
 *   <li>/deathdiag status [玩家]</li>
 *   <li>/deathdiag watch &lt;秒&gt; [玩家]</li>
 *   <li>/deathdiag fix [玩家]</li>
 *   <li>/deathdiag kill [玩家]</li>
 *   <li>/deathdiag survival [玩家]</li>
 *   <li>/deathdiag uncancel on|off</li>
 * </ul>
 */
public final class DeathDiagnostic {

    private static final Map<UUID, Integer> WATCH_UNTIL = new ConcurrentHashMap<>();
    private static volatile boolean forceDeath = false;

    private DeathDiagnostic() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deathdiag")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("watch")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(ctx -> watch(ctx.getSource(), self(ctx.getSource()),
                                        IntegerArgumentType.getInteger(ctx, "seconds")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> watch(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("fix")
                        .executes(ctx -> fix(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> fix(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("kill")
                        .executes(ctx -> kill(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> kill(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("survival")
                        .executes(ctx -> survival(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> survival(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("uncancel")
                        .then(Commands.literal("on").executes(ctx -> setForceDeath(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setForceDeath(ctx.getSource(), false)))));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            logStatus(player, "login");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean canceled = event.isCanceled();
        boolean watched = isWatched(player);
        if (canceled || event.getAmount() <= 0.0F || watched) {
            ObserverMod.LOGGER.warn(
                    "[deathdiag] incoming player={} amount={} original={} canceled={} source={}",
                    player.getName().getString(), event.getAmount(), event.getOriginalAmount(),
                    canceled, event.getSource());
        }
        if (forceDeath) {
            if (canceled) {
                event.setCanceled(false);
                ObserverMod.LOGGER.warn("[deathdiag] force-uncanceled incoming damage for {}",
                        player.getName().getString());
            }
            if (event.getAmount() <= 0.0F) {
                event.setAmount(1.0F);
                ObserverMod.LOGGER.warn("[deathdiag] forced incoming damage amount to 1.0 for {}",
                        player.getName().getString());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float newDamage = event.getNewDamage();
        if (newDamage <= 0.0F || isWatched(player) || forceDeath) {
            ObserverMod.LOGGER.warn(
                    "[deathdiag] damage-pre player={} original={} new={} health={} source={}",
                    player.getName().getString(), event.getOriginalDamage(), newDamage,
                    player.getHealth(), event.getSource());
        }
        if (forceDeath && newDamage <= 0.0F) {
            event.setNewDamage(1.0F);
            ObserverMod.LOGGER.warn("[deathdiag] forced damage-pre newDamage to 1.0 for {}",
                    player.getName().getString());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && isWatched(player)) {
            ObserverMod.LOGGER.warn(
                    "[deathdiag] damage-post player={} original={} new={} blocked={} shield={} health={} source={}",
                    player.getName().getString(), event.getOriginalDamage(), event.getNewDamage(),
                    event.getBlockedDamage(), event.getShieldDamage(), player.getHealth(), event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean canceled = event.isCanceled();
        ObserverMod.LOGGER.warn("[deathdiag] death event player={} canceled={} source={}",
                player.getName().getString(), canceled, event.getSource());
        logStatus(player, "death-event");
        if (forceDeath && canceled) {
            event.setCanceled(false);
            ObserverMod.LOGGER.warn("[deathdiag] force-uncanceled death event for {}",
                    player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int now = player.server.getTickCount();
        Integer until = WATCH_UNTIL.get(player.getUUID());
        if (until != null && now > until) {
            WATCH_UNTIL.remove(player.getUUID());
            until = null;
        }

        boolean anomaly = player.getHealth() <= 0.0F
                || player.isDeadOrDying()
                || player.deathTime > 0;
        boolean watchTick = until != null && now <= until && now % 5 == 0;
        if (watchTick || (anomaly && now % 20 == 0)) {
            logStatus(player, watchTick ? "watch" : "anomaly");
        }
    }

    private static ServerPlayer self(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        String text = describe(player);
        ObserverMod.LOGGER.info("[deathdiag] status\n{}", text);
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int watch(CommandSourceStack source, ServerPlayer player, int seconds) {
        int until = source.getServer().getTickCount() + seconds * 20;
        WATCH_UNTIL.put(player.getUUID(), until);
        source.sendSuccess(() -> Component.literal(
                "[deathdiag] 正在观察 " + player.getName().getString() + " " + seconds + " 秒"), false);
        ObserverMod.LOGGER.info("[deathdiag] watch {} for {}s", player.getName().getString(), seconds);
        return 1;
    }

    private static int fix(CommandSourceStack source, ServerPlayer player) {
        player.deathTime = 0;
        player.hurtTime = 0;
        player.invulnerableTime = 0;
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        player.setHealth(Math.max(1.0F, player.getMaxHealth()));
        player.setRemainingFireTicks(0);
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setFoodLevel(20);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());

        source.sendSuccess(() -> Component.literal(
                "[deathdiag] 已重置 " + player.getName().getString() + " 的生命/无敌状态"), false);
        logStatus(player, "fix");
        return 1;
    }

    private static int kill(CommandSourceStack source, ServerPlayer player) {
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        source.sendSuccess(() -> Component.literal(
                "[deathdiag] 已对 " + player.getName().getString() + " 执行 genericKill"), false);
        logStatus(player, "kill");
        return 1;
    }

    private static int survival(CommandSourceStack source, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        source.sendSuccess(() -> Component.literal(
                "[deathdiag] 已将 " + player.getName().getString() + " 切换为生存模式"), false);
        logStatus(player, "survival");
        return 1;
    }

    private static int setForceDeath(CommandSourceStack source, boolean enabled) {
        forceDeath = enabled;
        source.sendSuccess(() -> Component.literal(
                "[deathdiag] 强制取消死亡/伤害取消 = " + enabled), false);
        ObserverMod.LOGGER.warn("[deathdiag] forceDeath={}", enabled);
        return 1;
    }

    private static boolean isWatched(ServerPlayer player) {
        Integer until = WATCH_UNTIL.get(player.getUUID());
        return until != null && player.server.getTickCount() <= until;
    }

    private static void logStatus(ServerPlayer player, String reason) {
        ObserverMod.LOGGER.warn("[deathdiag] {}\n{}", reason, describe(player));
    }

    private static String describe(ServerPlayer player) {
        String effects = player.getActiveEffects().stream()
                .map(DeathDiagnostic::describeEffect)
                .collect(Collectors.joining(","));
        return "player=" + player.getName().getString()
                + " uuid=" + player.getUUID()
                + " dim=" + player.level().dimension().location()
                + " pos=" + String.format("%.2f/%.2f/%.2f", player.getX(), player.getY(), player.getZ())
                + " gameMode=" + player.gameMode.getGameModeForPlayer()
                + "\n  health=" + player.getHealth() + "/" + player.getMaxHealth()
                + " absorption=" + player.getAbsorptionAmount()
                + " deadOrDying=" + player.isDeadOrDying()
                + " alive=" + player.isAlive()
                + " removed=" + player.isRemoved()
                + " deathTime=" + player.deathTime
                + " hurtTime=" + player.hurtTime
                + " invulnerableTime=" + player.invulnerableTime
                + "\n  invulnerable=" + player.isInvulnerable()
                + " abilityInvulnerable=" + player.getAbilities().invulnerable
                + " creative=" + player.isCreative()
                + " spectator=" + player.isSpectator()
                + " lastDamageSource=" + player.getLastDamageSource()
                + "\n  effects=[" + effects + "]"
                + "\n  persistentData=" + player.getPersistentData();
    }

    private static String describeEffect(MobEffectInstance effect) {
        return effect.getEffect().value().getDisplayName().getString()
                + "(" + effect.getDuration() + "t," + effect.getAmplifier() + ")";
    }
}
