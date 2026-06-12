package com.gy_mod.gy_trinket.core.charged_shield;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 充能护盾物品检测器
 * <p>
 * 监听属性计算事件，检查玩家光点核心中是否有充能护盾模块
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedShieldItemChecker {

    private ChargedShieldItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            ChargedShieldManager.setHasChargedShield(playerUUID, false);
            return;
        }

        boolean hasChargedShield = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                if (Config.isChargedShieldItem(stack.getItem())) {
                    hasChargedShield = true;
                    break;
                }
            }
        }

        ChargedShieldManager.setHasChargedShield(playerUUID, hasChargedShield);
    }
}
