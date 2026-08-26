package com.gy_mod.gy_trinket.core.ghost_fuselage;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 幽灵机身物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有幽灵机身物品
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class GhostFuselageItemChecker {

    private GhostFuselageItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        boolean hasGhostFuselage = PlayerStoreUtils.hasActiveItem(player, Config::isGhostFuselageItem);

        GhostFuselageManager.setHasGhostFuselage(player, hasGhostFuselage);
    }
}
