package com.gytrinket.gytrinket.core.entity.construct.wingman.attack_mode;

import com.gytrinket.gytrinket.core.entity.construct.drone.ModDamageSources;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.modifier.player.attack.AttackSpeedManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;

import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

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

        // 临时将武器设入主手槽，使 doHurtTarget 能读取武器属性和附魔
        ItemStack weaponCopy = weapon.copy();
        net.minecraft.world.entity.EquipmentSlot mainHand = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        ItemStack prevMainHand = attacker.getItemBySlot(mainHand);
        attacker.setItemSlot(mainHand, weaponCopy);

        boolean hit = attacker.doHurtTarget(target);
        InterceptorDebug.logAttackResult(attacker, "近战结果: hit=" + hit);

        if (hit) {
            // 拦截机不消耗武器耐久
            doSweepHurt(attacker, target, weaponCopy);
        }

        attacker.setItemSlot(mainHand, prevMainHand);
    }

    @Override
    public int calculateCooldown(WingmanConstructEntity attacker, ItemStack weapon, Player owner) {
        // 从武器属性修饰符中提取攻击速度加成（不依赖实体属性，避免未注册属性崩溃）
        double weaponAttackSpeedAdd = 0;
        for (var entry : weapon.getAttributeModifiers().modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
                weaponAttackSpeedAdd += entry.modifier().amount();
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
        // 与女仆模组一致：用 ItemAbilities.SWORD_SWEEP 判定能否横扫
        if (!weapon.canPerformAction(ItemAbilities.SWORD_SWEEP)) return;

        // 与女仆模组一致：用 Attributes.SWEEPING_DAMAGE_RATIO 属性获取横扫比例
        float sweepingDamageRatio = (float) attacker.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO);
        if (sweepingDamageRatio <= 0) return;

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
                entity.hurt(ModDamageSources.mobAttackWithGuardAggro(attacker, entity), sweepDamage);
            }
        }

        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1, 1);
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    1, 0, 0, 0, 0);
        }
    }

    double getReachDistance(WingmanConstructEntity attacker) {
        double baseReach = 3.0;
        ItemStack weapon = attacker.getInterceptorWeaponForStrategy();
        if (!weapon.isEmpty()) {
            // 1.21.1: 从 ItemAttributeModifiers 提取 ENTITY_INTERACTION_RANGE（reach 属性）
            for (var entry : weapon.getAttributeModifiers().modifiers()) {
                if (entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)) {
                    baseReach += entry.modifier().amount();
                }
            }
        }
        // 连击锁定：点射连发期间临时+3格reach
        if (attacker.isBurstLocked()) {
            baseReach += 3.0;
        }
        return baseReach;
    }

    /** 供外部调用的近战攻击距离查询 */
    public static double getReachDistanceStatic(WingmanConstructEntity attacker) {
        return new MeleeWeaponMode().getReachDistance(attacker);
    }
}
