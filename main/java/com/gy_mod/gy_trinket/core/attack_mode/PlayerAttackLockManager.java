package com.gy_mod.gy_trinket.core.attack_mode;

import com.gy_mod.gy_trinket.core.attack_mode.assault.AssaultManager;
import com.gy_mod.gy_trinket.core.attack_mode.burst_fire.BurstFireManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 玩家攻击锁定管理器 — 独立系统
 * <p>
 * 当玩家的攻击被锁定时：
 * <ul>
 *   <li>对 LivingEntity 的攻击被取消</li>
 *   <li>对非 LivingEntity（船、矿车等）的攻击允许通过</li>
 *   <li>强袭、点射、充能攻击及其组合延申效果全部禁用</li>
 *   <li>电能释放正常触发（不受影响）</li>
 * </ul>
 * <p>
 * 外部系统（如督战者）通过 {@link #lockPlayer} / {@link #unlockPlayer} 设置锁定状态，
 * 各攻击模块通过 {@link #isLocked} 查询状态。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerAttackLockManager {

    /** 攻击被锁定的玩家集合 */
    private static final Set<UUID> LOCKED_PLAYERS = new CopyOnWriteArraySet<>();

    private PlayerAttackLockManager() {}

    /**
     * 锁定玩家的特殊攻击能力
     *
     * @param playerUUID 玩家UUID
     */
    public static void lockPlayer(UUID playerUUID) {
        LOCKED_PLAYERS.add(playerUUID);
    }

    /**
     * 解锁玩家的特殊攻击能力
     *
     * @param playerUUID 玩家UUID
     */
    public static void unlockPlayer(UUID playerUUID) {
        LOCKED_PLAYERS.remove(playerUUID);
    }

    /**
     * 查询玩家的攻击是否被锁定
     *
     * @param playerUUID 玩家UUID
     * @return 如果锁定返回 true
     */
    public static boolean isLocked(UUID playerUUID) {
        return LOCKED_PLAYERS.contains(playerUUID);
    }

    /**
     * 查询玩家的攻击是否被锁定
     *
     * @param player 玩家
     * @return 如果锁定返回 true
     */
    public static boolean isLocked(Player player) {
        return LOCKED_PLAYERS.contains(player.getUUID());
    }

    /**
     * 攻击锁定时，取消所有进行中的特殊攻击模式
     */
    public static void cancelActiveModes(UUID playerUUID) {
        AssaultManager.clearAssault(playerUUID);
        BurstFireManager.cancelBurstFire(playerUUID);
        ChargedAttackManager.cancelCharging(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOCKED_PLAYERS.remove(player.getUUID());
        }
    }

    public static void clearAllData() {
        LOCKED_PLAYERS.clear();
    }
}
