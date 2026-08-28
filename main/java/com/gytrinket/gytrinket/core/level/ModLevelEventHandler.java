package com.gytrinket.gytrinket.core.level;

import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 光点等级事件处理器
 * 监听原版经验获取事件，同步增加等量的光点经验
 * 玩家失去原版经验时不会影响光点经验/光点等级/升级点
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
            com.gytrinket.gytrinket.core.random_build.RandomBuildManager.clearPlayerData(player.getUUID());
        }
    }

    /**
     * 监听经验值变化事件
     * 仅在经验增加时（正值）同步增加光点经验
     * 经验减少时（负值，如附魔、铁砧等）不影响光点经验
     */
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }

        int amount = event.getAmount();
        if (amount <= 0) {
            return;
        }

        ModLevelManager.addUpgradeExp(event.getEntity().getUUID(), amount);
    }
}
