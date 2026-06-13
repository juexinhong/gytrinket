package com.gy_mod.gy_trinket.core.attack_mode;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击状态系统 - 服务端管理器
 * <p>
 * 存储每个玩家的左键攻击状态，供其他系统查询
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackStateManager {

    /**
     * 攻击状态枚举（服务端和客户端共用）
     */
    public enum AttackState {
        RELEASED,   // 松开
        PRESSED,    // 按下（边沿触发，仅持续1tick）
        HELD        // 按住
    }

    private static final Map<UUID, AttackState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PLAYER_HOLD_TICKS = new ConcurrentHashMap<>();

    private AttackStateManager() {}

    /**
     * 更新玩家攻击状态（由网络包调用）
     */
    public static void updatePlayerState(UUID playerUUID, int stateOrdinal, int holdTicks) {
        AttackState[] states = AttackState.values();
        if (stateOrdinal >= 0 && stateOrdinal < states.length) {
            PLAYER_STATES.put(playerUUID, states[stateOrdinal]);
            PLAYER_HOLD_TICKS.put(playerUUID, holdTicks);
        }
    }

    /**
     * 获取玩家攻击状态
     */
    public static AttackState getPlayerState(Player player) {
        return PLAYER_STATES.getOrDefault(player.getUUID(), AttackState.RELEASED);
    }

    /**
     * 获取玩家按住时长
     */
    public static int getPlayerHoldTicks(Player player) {
        return PLAYER_HOLD_TICKS.getOrDefault(player.getUUID(), 0);
    }

    /**
     * 玩家是否正在按住左键
     */
    public static boolean isPlayerHeld(Player player) {
        AttackState state = getPlayerState(player);
        return state == AttackState.PRESSED || state == AttackState.HELD;
    }

    /**
     * 玩家是否松开左键
     */
    public static boolean isPlayerReleased(Player player) {
        return getPlayerState(player) == AttackState.RELEASED;
    }

    /**
     * 玩家是否刚刚按下左键
     */
    public static boolean isPlayerJustPressed(Player player) {
        return getPlayerState(player) == AttackState.PRESSED;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_STATES.remove(uuid);
        PLAYER_HOLD_TICKS.remove(uuid);
    }

    public static void clearAllData() {
        PLAYER_STATES.clear();
        PLAYER_HOLD_TICKS.clear();
    }
}
