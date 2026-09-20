package com.gytrinket.gytrinket.core.attack_cooldown;

import com.gytrinket.gytrinket.core.shield.cooldown.CooldownContext;
import com.gytrinket.gytrinket.core.shield.cooldown.IShieldCooldownModifier;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * 攻击冷却修饰器
 * <p>
 * 玩家攻击冷却期间（攻击强度 &lt; 0.9）降低护盾冷却推进速度，
 * 等效旧版冷却时长 ×1.25 的效果。
 */
public class AttackCooldownModifier implements IShieldCooldownModifier {

    /** 攻击冷却阈值：低于此值认为处于攻击冷却状态 */
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;

    /** 攻击冷却期间的推进速度因子（1/1.25） */
    private static final double ATTACK_SPEED_FACTOR = 0.8;

    @Override
    public String getName() {
        return "attack_cooldown";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public void onPreTick(CooldownContext context) {
        Player player = getPlayer(context.getPlayerUUID());
        if (player == null) {
            return;
        }
        float attackStrength = player.getAttackStrengthScale(0.0f);
        ShieldCooldownManager.setSpeedFactor(context.getPlayerUUID(),
                attackStrength < ATTACK_COOLDOWN_THRESHOLD ? ATTACK_SPEED_FACTOR : 1.0);
    }

    private Player getPlayer(UUID playerUUID) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
        }
        return null;
    }
}
