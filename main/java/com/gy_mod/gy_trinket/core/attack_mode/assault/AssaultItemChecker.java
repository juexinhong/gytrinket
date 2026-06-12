package com.gy_mod.gy_trinket.core.attack_mode.assault;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attack_mode.AttackModeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
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
 * 攻击模式物品检测器
 * <p>
 * 统一检测玩家拥有的攻击模式（强袭、充能攻击、电能释放、点射），
 * 并更新 AttackModeManager 的模式数据。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultItemChecker {

    private AssaultItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            AssaultManager.setHasAssault(playerUUID, false);
            updateCoordinatorModes(playerUUID, false, false, false, false);
            return;
        }

        boolean hasAssault = false;
        boolean hasChargedAttack = false;
        boolean hasElectricDischarge = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                if (Config.isAssaultItem(stack.getItem())) {
                    hasAssault = true;
                }
                if (Config.isChargedAttackItem(stack.getItem())) {
                    hasChargedAttack = true;
                }
                if (Config.isElectricDischargeItem(stack.getItem())) {
                    hasElectricDischarge = true;
                }
            }
        }

        // 点射由 combo 属性决定
        boolean hasBurstFire = AttributeManager.getPlayerAttribute(playerUUID, "combo") > 0;

        AssaultManager.setHasAssault(playerUUID, hasAssault);
        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.setHasChargedAttack(playerUUID, hasChargedAttack);

        updateCoordinatorModes(playerUUID, hasBurstFire, hasAssault, hasChargedAttack, hasElectricDischarge);
    }

    private static void updateCoordinatorModes(UUID playerUUID, boolean hasBurst, boolean hasAssault, boolean hasCharged, boolean hasElectric) {
        AttackModeManager.updatePlayerModes(playerUUID, hasBurst, hasAssault, hasCharged, hasElectric);
    }
}
