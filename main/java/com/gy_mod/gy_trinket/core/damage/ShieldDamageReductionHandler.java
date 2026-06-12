package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 护盾伤害减免处理器
 * <p>
 * 功能：
 * 当玩家护盾值大于0时，根据护盾伤害减免属性减少受到的伤害。
 * <p>
 * 优先级：50
 */
public class ShieldDamageReductionHandler implements DamageHandler {

    /** 处理器优先级 */
    private static final int PRIORITY = 50;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();
        UUID playerUUID = player.getUUID();

        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield <= 0) {
            return;
        }

        if (context.getAttackedEntity() instanceof Player attackedPlayer) {
            if (!ShieldTransferManager.shouldProtectPlayer(attackedPlayer)) {
                return;
            }
        }

        if (context.isPlayerSelfDamage()) {
            return;
        }

        double damageReduction = AttributeManager.getPlayerAttribute(playerUUID, "shield_damage_reduction");
        damageReduction = Math.max(damageReduction, 0.0);

        float currentDamage = context.getCurrentDamage();
        float reducedDamage = (float) (currentDamage * (damageReduction));
        context.setCurrentDamage(reducedDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}