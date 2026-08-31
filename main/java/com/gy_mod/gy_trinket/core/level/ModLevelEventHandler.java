package com.gy_mod.gy_trinket.core.level;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 光点等级事件处理器
 * 监听原版经验获取事件，同步增加等量的光点经验
 * 玩家失去原版经验时不会影响光点经验/光点等级/升级点
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
     * 监听经验值变化事件
     * 仅在经验增加时（正值）同步增加光点经验
     * 经验减少时（负值，如附魔、铁砧等）不影响光点经验
     */
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        int amount = event.getAmount();
        if (amount <= 0) {
            return;
        }

        ModLevelManager.addUpgradeExp(serverPlayer.getUUID(), amount);
    }
}
