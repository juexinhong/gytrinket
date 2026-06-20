package com.gytrinket.gytrinket.core.attack_mode.assault;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 攻击模式物品检测器
 * <p>
 * 统一检测玩家拥有的攻击模式（强袭、充能攻击、电能释放、点射），
 * 并更新 AttackModeManager 的模式数据。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultItemChecker {

    private AssaultItemChecker() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Player player = event.getPlayer();
        if (player == null) {
            AssaultManager.setHasAssault(playerUUID, false);
            updateCoordinatorModes(playerUUID, false, false, false, false);
            return;
        }

        boolean hasAssault = PlayerStoreUtils.hasActiveItem(player, Config::isAssaultItem);
        boolean hasChargedAttack = PlayerStoreUtils.hasActiveItem(player, Config::isChargedAttackItem);
        boolean hasElectricDischarge = PlayerStoreUtils.hasActiveItem(player, Config::isElectricDischargeItem);

        // 点射由 combo 属性决定
        boolean hasBurstFire = AttributeManager.getPlayerAttribute(playerUUID, "combo") > 0;

        AssaultManager.setHasAssault(playerUUID, hasAssault);
        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.setHasChargedAttack(playerUUID, hasChargedAttack);

        updateCoordinatorModes(playerUUID, hasBurstFire, hasAssault, hasChargedAttack, hasElectricDischarge);
    }

    private static void updateCoordinatorModes(UUID playerUUID, boolean hasBurst, boolean hasAssault, boolean hasCharged, boolean hasElectric) {
        AttackModeManager.updatePlayerModes(playerUUID, hasBurst, hasAssault, hasCharged, hasElectric);
    }
}
