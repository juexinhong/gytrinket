package com.gy_mod.gy_trinket.core.level;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
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

    /**
     * 监听经验值变化事件
     * 仅在经验增加时（正值）同步增加光点经验
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
