package com.gytrinket.gytrinket.core.tick;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 玩家 tick 单扇出：整个模组只有这一个 {@link TickEvent.PlayerTickEvent} 订阅者。
 * <p>
 * 原来的 17 个 {@code @SubscribeEvent onPlayerTick} 在此处按 START/END 两组顺序直调；
 * 派发用数组遍历，不产生每 tick 的迭代器/装箱分配。客户端专属的
 * {@code BurstFireClientHandler} 仍保留自己的订阅（避免在专用服加载客户端类）。
 * <p>
 * 顺序说明：END 组按“攻击冷却 → 攻击模式 → 弹射物点射 → 易伤/护盾 → 世界”排列；
 * 这些处理器在旧实现里本就是同优先级、互不依赖的独立监听器，顺序不影响结果。
 */
public final class PlayerTickDispatcher {

    @FunctionalInterface
    public interface Handler {
        void tick(Player player);
    }

    private static Handler[] startHandlers = new Handler[0];
    private static Handler[] endHandlers = new Handler[0];
    private static boolean initialized;

    private PlayerTickDispatcher() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        startHandlers = new Handler[]{
                com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager::onPlayerTick
        };
        endHandlers = new Handler[]{
                com.gytrinket.gytrinket.core.attack_cooldown.AttackCooldownEfficiencyModifier::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.AttackSpeedPenaltyManager::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.AttackModeManager::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.burst_fire.BurstFireManager::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.burst_fire.ProjectileBurstManager::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager::onPlayerTick,
                com.gytrinket.gytrinket.core.attack_mode.GrudgeManager::onPlayerTick,
                com.gytrinket.gytrinket.core.damage.AdaptiveArmorManager::onPlayerTick,
                com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageManager::onPlayerTick,
                com.gytrinket.gytrinket.core.journey.JourneyManager::onPlayerTick,
                com.gytrinket.gytrinket.core.random_build.TokenSyncHandler::onPlayerTick,
                com.gytrinket.gytrinket.core.shield.ChargedShieldManager::onPlayerTick,
                com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager::onPlayerTick,
                com.gytrinket.gytrinket.core.shield.NaturalRecoveryManager::onPlayerTick,
                com.gytrinket.gytrinket.core.special_effect.weaponized_shield.WeaponizedShieldManager::onPlayerTick,
                com.gytrinket.gytrinket.core.world.IslandTeleporter::onPlayerTick
        };
        MinecraftForge.EVENT_BUS.register(PlayerTickDispatcher.class);
        com.gytrinket.gytrinket.gytrinket.LOGGER.info(
                "[gytrinket] player tick dispatcher: {} START + {} END handlers",
                startHandlers.length, endHandlers.length);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Handler[] handlers = event.phase == TickEvent.Phase.START ? startHandlers : endHandlers;
        Player player = event.player;
        for (Handler handler : handlers) {
            handler.tick(player);
        }
    }
}
