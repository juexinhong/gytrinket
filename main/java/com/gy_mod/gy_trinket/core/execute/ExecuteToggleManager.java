
package com.gy_mod.gy_trinket.core.execute;

import com.gy_mod.gy_trinket.Config;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 斩杀归属开关管理器
 * <p>
 * 配置项 DRONE_EXECUTE_ENABLED 决定是否启用斩杀归属功能（总开关）。
 * 每个玩家可以通过 I 键在局内切换自己的斩杀归属开关。
 * 当总开关关闭时，局内开关无效；当总开关开启时，局内开关决定是否生效。
 */
public class ExecuteToggleManager {

    private static final Map<UUID, Boolean> playerToggles = new ConcurrentHashMap<>();

    /**
     * 判断指定玩家的斩杀归属是否启用
     * <p>
     * 需要同时满足：配置项启用 + 局内开关启用
     * player 为 null 时返回 false
     */
    public static boolean isExecuteEnabled(Player player) {
        if (player == null) {
            return false;
        }
        return isExecuteEnabled(player.getUUID());
    }

    public static boolean isExecuteEnabled(UUID playerUUID) {
        if (!Config.DRONE_EXECUTE_ENABLED.get()) {
            return false;
        }
        return playerToggles.getOrDefault(playerUUID, true);
    }

    /**
     * 切换玩家的局内斩杀归属开关
     *
     * @return 切换后的状态
     */
    public static boolean toggle(UUID playerUUID) {
        boolean current = playerToggles.getOrDefault(playerUUID, true);
        boolean newValue = !current;
        playerToggles.put(playerUUID, newValue);
        return newValue;
    }

    public static boolean isToggledOn(UUID playerUUID) {
        return playerToggles.getOrDefault(playerUUID, true);
    }

    public static void removePlayer(UUID playerUUID) {
        playerToggles.remove(playerUUID);
    }
}
