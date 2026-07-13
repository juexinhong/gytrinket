package com.gy_mod.gy_trinket.core.grudge;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 积怨模块物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有积怨模块
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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
