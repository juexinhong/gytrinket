package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家自伤减免处理器
 * 处理玩家自伤和协议玩家自伤伤害类型的减免
 */
public class PlayerSelfDamageLastHandler implements LastDamageHandler {

    private static final int PRIORITY = 99;

    @Override
    public void handle(LastDamageContext context) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType != ModDamageTypes.PLAYER_SELF_DAMAGE &&
            damageType != ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return;
        }

        double damageReduction = AttributeManager.getPlayerAttribute(player.getUUID(), "player_self_damage_reduction");
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
