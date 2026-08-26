package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.level.ModLevelManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PrecisionConstructEventHandler {

    private static final String NAMESPACE = "precision_construct";

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            applyPrecisionConstructBonus(player);
        }
    }

    public static void applyPrecisionConstructBonus(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (!hasRequiredItem(playerUUID)) {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, "construct_build_speed_independent");
            return;
        }

        int level = Math.max(0, ModLevelManager.getModLevel(playerUUID));
        double bonus = level * Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get();

        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, "construct_build_speed_independent", bonus);
    }

    private static boolean hasRequiredItem(UUID playerUUID) {
        Set<String> requiredItems = DefsManager.getItemSet("precision_construct_items");
        if (requiredItems.isEmpty()) {
            return false;
        }

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        Set<String> ownedItemIds = PlayerStoreUtils.getAllEquippedItemIds(playerUUID);

        for (String requiredId : requiredItems) {
            if (ownedItemIds.contains(requiredId)) {
                return true;
            }
        }

        return false;
    }
}
