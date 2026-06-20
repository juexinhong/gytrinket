package com.gytrinket.gytrinket.core.charged_shield;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 充能护盾物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有充能护盾模块
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedShieldItemChecker {

    private ChargedShieldItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();
        if (player == null) {
            ChargedShieldManager.setHasChargedShield(playerUUID, false);
            return;
        }

        boolean hasChargedShield = PlayerStoreUtils.hasActiveItem(player, Config::isChargedShieldItem);

        ChargedShieldManager.setHasChargedShield(playerUUID, hasChargedShield);
    }
}
