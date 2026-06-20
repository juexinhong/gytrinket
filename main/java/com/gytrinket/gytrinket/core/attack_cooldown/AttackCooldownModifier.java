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
 * 作为护盾冷却系统的修饰器，在玩家攻击冷却期间应用攻击冷却效果。
 * <p>
 * 工作流程：
 * 1. 在 {@link #onPreTick} 中检测玩家攻击状态
 * 2. 如果处于攻击冷却状态，创建攻击冷却上下文并应用所有效果
 * 3. 更新护盾冷却数据和上下文状态
 * <p>
 * 攻击冷却判断标准：当 {@link Player#getAttackStrengthScale(float)} 返回值 < 0.9f 时
 * 认为玩家处于攻击冷却状态。
 */
public class AttackCooldownModifier implements IShieldCooldownModifier {

    /** 攻击冷却阈值：低于此值认为处于攻击冷却状态 */
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9f;

    @Override
    public String getName() {
        return "attack_cooldown";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean onPreTick(ShieldCooldownManager.CooldownData state, CooldownContext context) {
        // 获取玩家对象
        Player player = getPlayer(context.getPlayerUUID());
        if (player == null) {
            return false;
        }

        // 获取攻击强度（0.0f ~ 1.0f）
        float attackStrength = player.getAttackStrengthScale(0.0f);

        // 判断是否处于攻击冷却状态
        boolean isInAttackCooldown = attackStrength < ATTACK_COOLDOWN_THRESHOLD;

        // 更新上下文状态
        context.setAttackStrength(attackStrength);
        context.setInAttackCooldown(isInAttackCooldown);

        // 如果不处于攻击冷却状态，恢复基础冷却时间
        if (!isInAttackCooldown) {
            Integer baseMaxCooldown = ShieldCooldownManager.BASE_MAX_COOLDOWN.get(context.getPlayerUUID());
            if (baseMaxCooldown != null && state.getMaxCooldown() != baseMaxCooldown) {
                state.updateMaxCooldown(baseMaxCooldown);
            }
            return false;
        }

        // 创建攻击冷却上下文
        AttackCooldownContext attackContext = new AttackCooldownContext(
            context.getPlayerUUID(),
            attackStrength,
            true,
            state,
            context
        );

        // 应用所有激活的攻击冷却效果
        AttackCooldownEffectManager.applyEffects(attackContext);

        return false;
    }

    @Override
    public void onPostTick(ShieldCooldownManager.CooldownData state, CooldownContext context) {
        // 预留方法，当前无额外处理逻辑
    }

    /**
     * 根据玩家UUID获取玩家对象
     *
     * @param playerUUID 玩家UUID
     * @return 玩家对象，如果未找到返回null
     */
    private Player getPlayer(UUID playerUUID) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
        }
        return null;
    }
}
