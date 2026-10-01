package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;

import net.minecraft.world.entity.player.Player;

/**
 * 伤害通知：受击时对冷却中的护盾实例施加受击冷却延长。
 * <p>
 * 责任链按 priority 降序执行，本 Handler（30）先于 ShieldHandler（20），
 * 因此传入的伤害为护盾吸收前的减免后伤害。
 */
public class DamageNotificationHandler implements DamageHandler {

    private static final int PRIORITY = 30;

    @Override
    public void handle(DamageContext context) {
        if (InvincibilityMarkerManager.hasMarker(context.getAttackedEntity())) {
            return;
        }

        if (context.isAnySelfDamage()) {
            return;
        }

        if (!(context.getAttackedEntity() instanceof Player)) {
            return;
        }

        ShieldCooldownManager.applyHitExtension(context.getPlayer().getUUID(), context.getCurrentDamage());
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
