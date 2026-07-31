package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAggroLockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 无人机相关伤害来源
 */
public class ModDamageSources {

    public static DamageSource droneBullet(Level level, Entity bullet, @Nullable LivingEntity attacker) {
        return ModDamageTypes.getDroneBulletDamageSource(level, bullet, attacker);
    }

    /**
     * 获取无人机斩杀伤害源
     * <p>
     * 当斩杀归属启用时：伤害归属玩家（爆炸伤害源），可触发玩家击杀效果
     * 当斩杀归属禁用时：伤害量不变，但伤害源不归属玩家
     *
     * @param target      目标实体
     * @param damageOwner 伤害归属玩家（可为null）
     * @param attacker    实际攻击者（无人机实体）
     * @return 斩杀伤害源
     */
    public static DamageSource getExecuteDamageSource(LivingEntity target, @Nullable Player damageOwner, @Nullable Entity attacker) {
        if (damageOwner != null && ExecuteToggleManager.isExecuteEnabled(damageOwner)) {
            return target.damageSources().explosion(null, damageOwner);
        } else if (attacker instanceof LivingEntity livingAttacker) {
            return target.damageSources().mobAttack(livingAttacker);
        } else {
            return target.damageSources().indirectMagic(attacker, attacker);
        }
    }

    /**
     * 创建带有守卫阵列仇恨集中的无人机子弹伤害源
     * <p>
     * 守卫阵列仇恨集中：
     * - 如果攻击者是守卫阵列中的防御无人机 → 子弹归属代理到圆弧中间的防御无人机
     * - 否则 → 子弹归属为攻击者自身
     *
     * @param level    世界
     * @param bullet   子弹实体
     * @param attacker 攻击者（构造体实体）
     * @return 带有守卫阵列仇恨集中的伤害源
     */
    public static DamageSource droneBulletWithGuardAggro(Level level, Entity bullet, @Nullable LivingEntity attacker) {
        if (attacker == null) {
            return droneBullet(level, bullet, null);
        }

        // 守卫阵列仇恨集中：代理到圆弧中间的防御无人机
        LivingEntity aggroProxy = ConstructAggroLockManager.getGuardArrayAggroProxy(attacker);
        return ModDamageTypes.getDroneBulletDamageSource(level, bullet, aggroProxy);
    }

    /**
     * 创建带有守卫阵列仇恨集中的近战伤害源（用于僚机近战、蜂群电弧等）
     * <p>
     * 守卫阵列仇恨集中规则同上
     *
     * @param attacker 攻击者（构造体实体）
     * @param target   目标实体
     * @return 带有守卫阵列仇恨集中的伤害源
     */
    public static DamageSource mobAttackWithGuardAggro(LivingEntity attacker, LivingEntity target) {
        LivingEntity aggroProxy = ConstructAggroLockManager.getGuardArrayAggroProxy(attacker);

        if (aggroProxy != attacker) {
            return target.damageSources().mobAttack(aggroProxy);
        }
        return target.damageSources().mobAttack(attacker);
    }
}
