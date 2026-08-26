package com.gy_mod.gy_trinket.core.random_build;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代币数量同步（服务端）
 * <p>
 * 代币机制启用时，基于玩家刻（PlayerTickEvent 的 END 阶段）每 20 tick（约 1 秒）检查一次
 * 玩家背包中的代币数量（对比缓存），仅在数量变化（背包内容变动）时将最新代币数量
 * 同步到客户端，供 HUD 升级提醒与玩家面板显示使用。
 * 未启用代币机制时不做任何事。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class TokenSyncHandler {

    private static final java.util.Map<UUID, Integer> LAST_TOKEN_COUNT = new ConcurrentHashMap<>();

    private TokenSyncHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!Config.isRandomBuildEnabled() || !Config.isRandomBuildTokenEnabled()) {
            LAST_TOKEN_COUNT.remove(player.getUUID());
            return;
        }
        // 每 20 tick（约 1 秒）检查一次背包代币数量，降低服务端负担；
        // 仅在数量变化（背包内容变动）时同步到客户端
        if (player.tickCount % 20 != 0) return;
        int count = RandomBuildManager.countTokens(player);
        Integer last = LAST_TOKEN_COUNT.get(player.getUUID());
        if (last == null || last != count) {
            LAST_TOKEN_COUNT.put(player.getUUID(), count);
            NetworkHandler.sendTokenCountToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_TOKEN_COUNT.remove(player.getUUID());
        }
    }
}
