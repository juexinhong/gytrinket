package com.gytrinket.gytrinket.core.level;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * 光点等级事件处理器
 * 监听原版经验获取事件，同步增加等量的光点经验
 * 玩家失去原版经验时不会影响光点经验/光点等级/升级点
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ModLevelEventHandler {

    private ModLevelEventHandler() {}

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
