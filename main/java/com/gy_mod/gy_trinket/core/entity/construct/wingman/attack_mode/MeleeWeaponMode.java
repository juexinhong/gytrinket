package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.drone.ModDamageSources;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 近战武器模式
 * <p>
 * 使用武器近身攻击目标，支持横扫之刃。
 * executeAttack 不设置 weaponAttackCooldown，由模块模式管理器负责冷却。
 */
public class MeleeWeaponMode implements InterceptorWeaponMode {

    @Override
    public void executeAttack(WingmanConstructEntity attacker, LivingEntity target, ItemStack weapon, Player owner) {
        double reachDistance = getReachDistance(attacker);
        double distance = attacker.distanceTo(target);
        if (distance > reachDistance) {
            InterceptorDebug.logFast(attacker, "melee_oor_" + attacker.getId(),
                    "近战超出范围: dist=" + String.format("%.1f", distance) + " reach=" + String.format("%.1f", reachDistance));
            return;
        }

        InterceptorDebug.logAttackStep(attacker, "melee_exec", "近战攻击: weapon=" + weapon.getItem()
                + " dist=" + String.format("%.1f", distance));

        ItemStack weaponCopy = weapon.copy();
        ItemStack prevMainHand = attacker.getItemBySlot(EquipmentSlot.MAINHAND);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, weaponCopy);

        // 双保险：命中前清除目标无敌帧（LivingIncomingDamageEvent 的清零兜底之外，直接在此保证高频近战不被吞伤）
        target.invulnerableTime = 0;

        boolean hit = attacker.doHurtTarget(target);
        InterceptorDebug.logAttackResult(attacker, "近战结果: hit=" + hit);

        if (hit) {
            doSweepHurt(attacker, target, weaponCopy);
        }

        attacker.setItemSlot(EquipmentSlot.MAINHAND, prevMainHand);
    }

    @Override
    public int calculateCooldown(WingmanConstructEntity attacker, ItemStack weapon, Player owner) {
        double weaponAttackSpeedAdd = 0;
        var modifiers = weapon.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (modifiers.containsKey(Attributes.ATTACK_SPEED)) {
            for (var modifier : modifiers.get(Attributes.ATTACK_SPEED)) {
                weaponAttackSpeedAdd += modifier.getAmount();
            }
        }
        double baseAttackSpeed = 4.0 + weaponAttackSpeedAdd;
        double modMultiplier = AttackSpeedManager.getInterceptorAttackSpeedMultiplier(owner.getUUID());
        baseAttackSpeed *= modMultiplier;
        double interceptorAttackSpeed = baseAttackSpeed * 0.2;
        if (interceptorAttackSpeed <= 0) interceptorAttackSpeed = 0.1;
        interceptorAttackSpeed *= attacker.getAttackSpeedMultiplier();
        interceptorAttackSpeed *= attacker.getWeaponAttackSpeedMultiplier();
        int cooldownTicks = (int) (20.0 / interceptorAttackSpeed);
        return Math.max(2, cooldownTicks);
    }

    @Override
    public double[] getIdealDistanceRange(WingmanConstructEntity attacker) {
        double reach = getReachDistance(attacker);
        // 近战：靠近到交互距离附近，留出小幅缓冲
        return new double[]{reach - 1.0, reach, reach + 0.5};
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public String getSerializedName() {
        return "melee";
    }

    private void doSweepHurt(WingmanConstructEntity attacker, LivingEntity target, ItemStack weapon) {
        float sweepingDamageRatio = EnchantmentHelper.getSweepingDamageRatio(attacker);
        if (sweepingDamageRatio <= 0) return;

        boolean canSweep = weapon.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP);
        if (!canSweep) return;

        float baseDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float sweepDamage = 1.0f + sweepingDamageRatio * baseDamage;

        AABB sweepRange = target.getBoundingBox().inflate(1.0, 0.25, 1.0);
        List<LivingEntity> hurtEntities = attacker.level().getEntitiesOfClass(LivingEntity.class, sweepRange);

        for (LivingEntity entity : hurtEntities) {
            if (entity != attacker && entity != target && !attacker.isAlliedTo(entity)
                    && attacker.canAttackType(entity.getType())) {
                float posX = Mth.sin(attacker.getYRot() * ((float) Math.PI / 180F));
                float posY = -Mth.cos(attacker.getYRot() * ((float) Math.PI / 180F));
                entity.knockback(0.4, posX, posY);
                // 横扫目标同样清除无敌帧，保证多目标横扫不被原版无敌帧吞伤
                entity.invulnerableTime = 0;
                entity.hurt(ModDamageSources.mobAttackWithGuardAggro(attacker, entity), sweepDamage);
            }
        }

        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1, 1);
        if (attacker.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    1, 0, 0, 0, 0);
        }
    }

    double getReachDistance(WingmanConstructEntity attacker) {
        double baseReach = 3.0;
        ItemStack weapon = attacker.getInterceptorWeaponForStrategy();
        if (!weapon.isEmpty()) {
            var modifiers = weapon.getAttributeModifiers(EquipmentSlot.MAINHAND);
            var reachAttr = net.minecraftforge.common.ForgeMod.ENTITY_REACH.get();
            if (modifiers.containsKey(reachAttr)) {
                for (var modifier : modifiers.get(reachAttr)) {
                    baseReach += modifier.getAmount();
                }
            }
        }
        // 连击锁定：点射连发期间临时+3格reach
        if (attacker.isBurstLocked()) {
            baseReach += 3.0;
        }
        return baseReach;
    }

    /** 供外部调用的近战攻击距离查询（public静态包装） */
    public static double getReachDistanceStatic(WingmanConstructEntity attacker) {
        return new MeleeWeaponMode().getReachDistance(attacker);
    }
}
