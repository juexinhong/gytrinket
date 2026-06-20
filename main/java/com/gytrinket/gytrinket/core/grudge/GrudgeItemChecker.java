package com.gytrinket.gytrinket.core.grudge;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 积怨模块物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有积怨模块
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class GrudgeItemChecker {

    private GrudgeItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();
        if (player == null) {
            GrudgeManager.setHasGrudge(playerUUID, false);
            return;
        }

        boolean hasGrudge = PlayerStoreUtils.hasActiveItem(player, Config::isGrudgeItem);

        GrudgeManager.setHasGrudge(playerUUID, hasGrudge);
    }
}
