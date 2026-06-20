package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 护盾自伤减免处理器
 * <p>
 * 功能：
 * 当玩家受到护盾自伤或协议护盾自伤时，根据护盾自伤减免属性减少伤害。
 * 使用经过前序处理器处理后的伤害值。
 * <p>
 * 优先级：49（在护盾伤害减免之后）
 */
public class ShieldSelfDamageReductionHandler implements DamageHandler {

    /** 处理器优先级 */
    private static final int PRIORITY = 49;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();
        UUID playerUUID = player.getUUID();

        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield <= 0) {
            return;
        }

        if (!context.isShieldSelfDamage()) {
            return;
        }

        double damageReduction = AttributeManager.getPlayerAttribute(playerUUID, "shield_self_damage_reduction");
        damageReduction = Math.max(damageReduction, 0.0);

        float reducedDamage = context.getCurrentDamage();
        float finalDamage = (float) (reducedDamage * (damageReduction));
        context.setCurrentDamage(finalDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}