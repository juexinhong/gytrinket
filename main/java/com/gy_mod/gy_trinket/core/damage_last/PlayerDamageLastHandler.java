package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家伤害最终减免处理器
 */
public class PlayerDamageLastHandler implements LastDamageHandler {

    private static final int PRIORITY = 100;

    @Override
    public void handle(LastDamageContext context) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        double damageReduction = AttributeManager.getPlayerAttribute(player.getUUID(), "player_damage_reduction");
        damageReduction = Math.max(damageReduction, 0.0);

        float currentDamage = context.getCurrentDamage();
        float reducedDamage = (float) (currentDamage * damageReduction);
        context.setCurrentDamage(reducedDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}