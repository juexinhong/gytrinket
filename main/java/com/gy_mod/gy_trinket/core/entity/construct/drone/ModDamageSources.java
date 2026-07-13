package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.execute.ExecuteToggleManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
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
}