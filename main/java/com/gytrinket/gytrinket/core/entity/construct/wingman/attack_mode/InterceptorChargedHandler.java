package com.gytrinket.gytrinket.core.entity.construct.wingman.attack_mode;
import com.gytrinket.gytrinket.core.defs.DefsQueries;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.MechanicValueDefs;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModDamageSources;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.attack_mode.GrudgeManager;
import com.gytrinket.gytrinket.core.projectile.MultipartHitScan;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 拦截机充能攻击模式
 * <p>
 * 充能速率 = 20 / 武器攻击间隔（tick），
 * 攻击间隔由武器模式的 calculateCooldown 提供，已包含构造体攻击速度和模组攻击速度属性。
 * <p>
 * 积怨效果：拦截机充能期间，自身受到伤害（包括护盾移植挡伤）时，按比率增加充能值。
 * <p>
 * 伤害 = 基础伤害 × (1 + 充能值)，剑类武器触发充能横扫增强。
 */
public class InterceptorChargedHandler implements InterceptorAttackModeHandler {

    public static final String NAME = "charged";

    /** 固定充能时间（从Config读取，默认3秒 = 60tick） */
    public static int getChargeDurationTicks() {
        return Config.getInterceptorChargeDurationTicks();
    }
    /** 最大充能时间（近战等待接近，从Config读取，默认6秒 = 120tick） */
    public static int getMaxChargeDurationTicks() {
        return Config.getInterceptorMaxChargeDurationTicks();
    }

    private static final String CHARGING_KEY = "InterceptorCharging";
    private static final String CHARGE_PROGRESS_KEY = "InterceptorChargeProgress";
    /** 当前充能值（浮点） */
    private static final String CHARGE_VALUE_KEY = "InterceptorChargeValue";
    /** 充能期间的强袭触发计数器 */
    private static final String CHARGE_ASSAULT_COUNTER_KEY = "InterceptorChargeAssaultCounter";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void tick(WingmanConstructEntity wingman, Player owner) {
        if (!isCharging(wingman)) return;

        LivingEntity target = wingman.getLastTrackedTarget();
        if (target == null || !target.isAlive()) {
            cancelCharge(wingman);
            return;
        }

        // 递增充能进度
        int progress = getChargeProgress(wingman) + 1;
        setChargeProgress(wingman, progress);

        // 累积充能值（与玩家公式一致）
        double chargeValue = getChargeValue(wingman);
        double increment = calculateChargeIncrement(chargeValue, wingman, owner);
        chargeValue += increment;
        setChargeValue(wingman, chargeValue);

        // 充能期间触发强袭（按武器攻击速度频率叠加强袭层数）
        InterceptorAttackModeManager.onChargedTick(wingman, target, owner);

        // 充能完成检查
        if (progress >= getChargeDurationTicks()) {
            // 近战模式：如果目标不在攻击范围内，继续充能等待接近（最多6秒）
            if (isMeleeMode(wingman)) {
                double dist = wingman.distanceTo(target);
                double reach = getMeleeReach(wingman);
                if (dist > reach && progress < getMaxChargeDurationTicks()) {
                    // 目标超出范围，继续充能（不释放）
                } else if (progress >= getMaxChargeDurationTicks()) {
                    // 超过6秒，放弃充能
                    cancelCharge(wingman);
                } else {
                    // 目标在范围内，释放充能攻击
                    releaseChargedAttack(wingman, target, owner);
                }
            } else {
                // 弓箭模式：充能完成直接释放
                releaseChargedAttack(wingman, target, owner);
            }
        }
    }

    @Override
    public boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        if (isCharging(wingman)) {
            return false;
        }
        startCharge(wingman);
        return false;
    }

    @Override
    public void onPostAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        // 充能攻击不经过此路径
    }

    @Override
    public int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner) {
        if (isCharging(wingman)) {
            return 0;
        }
        return baseCooldown;
    }

    @Override
    public float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner) {
        return baseDamage;
    }

    @Override
    public void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget, LivingEntity newTarget) {
        if (isCharging(wingman)) {
            if (newTarget == null || !newTarget.isAlive()) {
                cancelCharge(wingman);
            }
        }
    }

    @Override
    public void clearState(WingmanConstructEntity wingman) {
        cancelCharge(wingman);
    }

    // ===== 充能状态管理 =====

    public static boolean isCharging(WingmanConstructEntity wingman) {
        return wingman.getPersistentData().getBoolean(CHARGING_KEY);
    }

    private void startCharge(WingmanConstructEntity wingman) {
        wingman.getPersistentData().putBoolean(CHARGING_KEY, true);
        wingman.getPersistentData().putInt(CHARGE_PROGRESS_KEY, 0);
        wingman.getPersistentData().putDouble(CHARGE_VALUE_KEY, 0.0);
        wingman.getPersistentData().putInt(CHARGE_ASSAULT_COUNTER_KEY, 0);
        wingman.setWeaponAttackCooldown(0);

        // 初始化积怨追踪数据
        Entity owner = wingman.getOwner();
        if (owner instanceof Player player) {
            initGrudgeTracking(wingman, player);
        }
    }

    private int getChargeProgress(WingmanConstructEntity wingman) {
        return wingman.getPersistentData().getInt(CHARGE_PROGRESS_KEY);
    }

    private void setChargeProgress(WingmanConstructEntity wingman, int progress) {
        wingman.getPersistentData().putInt(CHARGE_PROGRESS_KEY, progress);
    }

    private double getChargeValue(WingmanConstructEntity wingman) {
        return wingman.getPersistentData().getDouble(CHARGE_VALUE_KEY);
    }

    private void setChargeValue(WingmanConstructEntity wingman, double value) {
        wingman.getPersistentData().putDouble(CHARGE_VALUE_KEY, value);
    }

    // ===== 充能计算 =====

    /**
     * 计算充能速率 = 20 / 武器攻击间隔（tick）
     * <p>
     * 武器攻击间隔由武器模式的 calculateCooldown 提供，
     * 已包含构造体攻击速度乘数和模组攻击速度乘数。
     */
    private double getChargeRate(WingmanConstructEntity wingman, Player owner) {
        ItemStack weapon = wingman.getInterceptorWeapon();
        if (weapon.isEmpty()) weapon = owner.getMainHandItem();

        int attackInterval = InterceptorAttackModeManager.calculateWeaponCooldown(wingman, weapon, owner);
        attackInterval = Math.max(1, attackInterval); // 防止除零
        return 20.0 / attackInterval;
    }

    /**
     * 计算充能增量（含阻力 + 积怨）
     * <pre>
     * 充能速率 = 20 / attackInterval + 积怨充能速率
     * 阻力因子 = 1 - dragCoeff * 充能值 / (充能值 + 充能速率 * dragThresholdFactor)
     * 每tick增量 = 充能速率 * max(阻力因子, 0.01)
     * </pre>
     */
    private double calculateChargeIncrement(double currentCharge, WingmanConstructEntity wingman, Player owner) {
        double chargeRate = getChargeRate(wingman, owner);

        // 添加积怨充能速率（如果玩家拥有积怨且拦截机正在充能）
        double grudgeRate = getGrudgeChargeRate(wingman, owner);
        chargeRate += grudgeRate;

        double dragCoeff = Config.getChargedAttackDragCoefficient();
        double dragThreshold = chargeRate * Config.getChargedAttackDragThresholdFactor();
        double dragFactor = 1.0 - dragCoeff * currentCharge / (currentCharge + dragThreshold);

        return chargeRate * Math.max(dragFactor, 0.01);
    }

    // ===== 积怨效果 =====

    /** 拦截机上一次记录的生命值（用于检测受伤） */
    private static final String PREV_HEALTH_KEY = "InterceptorPrevHealth";
    /** 拦截机上一次记录的护盾移植护盾值（用于检测护盾挡伤） */
    private static final String PREV_SHIELD_KEY = "InterceptorPrevShield";

    /**
     * 获取拦截机的积怨充能速率
     * <p>
     * 机制与玩家积怨一致：
     * - 拦截机自身受伤（生命值减少）→ 按转化比率增加临时充能
     * - 护盾移植挡伤（护盾值减少）→ 按转化比率增加临时充能
     * - 临时充能快速消退
     */
    private double getGrudgeChargeRate(WingmanConstructEntity wingman, Player owner) {
        if (!GrudgeManager.hasGrudge(owner)) {
            return 0;
        }

        UUID ownerUUID = owner.getUUID();
        double prevHealth = wingman.getPersistentData().getDouble(PREV_HEALTH_KEY);
        double prevShield = wingman.getPersistentData().getDouble(PREV_SHIELD_KEY);

        double currentHealth = wingman.getHealth();
        double currentShield = 0;

        // 检查拦截机是否被护盾移植保护
        if (ShieldTransferManager.isEntityProtectedByShield(wingman)) {
            currentShield = ShieldManager.getCurrentShield(ownerUUID);
        }

        double healthLost = Math.max(0, prevHealth - currentHealth);
        double shieldLost = Math.max(0, prevShield - currentShield);
        double totalLost = healthLost + shieldLost;

        double grudgeRate = 0;
        if (totalLost > 0) {
            // 物品级数值覆盖：取首个生效物品的覆盖值，未覆盖回退 Config 默认
            double conversionRatio = DefsQueries.resolveMechanicValue(owner.getServer(), ownerUUID,
                    "grudge_items", "conversion_ratio", MechanicValueDefs.defaultValueOf("grudge_items", "conversion_ratio", 0.2));
            grudgeRate = totalLost * conversionRatio;
        }

        // 更新记录值
        wingman.getPersistentData().putDouble(PREV_HEALTH_KEY, currentHealth);
        wingman.getPersistentData().putDouble(PREV_SHIELD_KEY, currentShield);

        return grudgeRate;
    }

    /** 初始化积怨追踪数据（充能开始时调用） */
    private void initGrudgeTracking(WingmanConstructEntity wingman, Player owner) {
        wingman.getPersistentData().putDouble(PREV_HEALTH_KEY, wingman.getHealth());
        double shield = 0;
        if (ShieldTransferManager.isEntityProtectedByShield(wingman)) {
            shield = ShieldManager.getCurrentShield(owner.getUUID());
        }
        wingman.getPersistentData().putDouble(PREV_SHIELD_KEY, shield);
    }

    // ===== 释放充能攻击 =====

    /**
     * 释放充能攻击
     * <p>
     * 伤害 = 基础伤害 × (1 + 充能值)，与玩家充能攻击公式一致。
     * 剑类武器触发充能横扫增强。
     */
    private void releaseChargedAttack(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        ItemStack weapon = wingman.getInterceptorWeapon();
        if (weapon.isEmpty()) {
            weapon = owner.getMainHandItem();
        }

        double chargeValue = getChargeValue(wingman);
        if (chargeValue <= 0) {
            chargeValue = 0.01; // 保底充能值
        }

        // 伤害加成 = (1 + 充能值)，与玩家公式一致
        float damageBonus = (float) chargeValue;

        // 临时提升攻击伤害属性
        var attackDamageAttr = wingman.getAttribute(Attributes.ATTACK_DAMAGE);
        net.minecraft.resources.ResourceLocation chargedModifierId =
                new net.minecraft.resources.ResourceLocation("gytrinket", "interceptor_charged_attack");
        java.util.UUID chargedModifierUuid =
                java.util.UUID.nameUUIDFromBytes(chargedModifierId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (attackDamageAttr != null) {
            attackDamageAttr.removeModifier(chargedModifierUuid);
            attackDamageAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                chargedModifierUuid,
                chargedModifierId.toString(),
                damageBonus,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }

        // 通过管理器调用武器模式执行攻击
        InterceptorAttackModeManager.executeWeaponAttack(wingman, target, weapon, owner);

        // 移除临时伤害修饰符
        if (attackDamageAttr != null) {
            attackDamageAttr.removeModifier(chargedModifierUuid);
        }

        // 剑类武器触发充能横扫增强
        if (ChargedAttackSweepHandler.isSwordItem(weapon)) {
            executeChargedSweep(wingman, target, weapon, owner, chargeValue);
        }

        // 清除充能状态
        cancelCharge(wingman);

        // 通知管理器：充能释放后，可能触发点射和强袭
        InterceptorAttackModeManager.onChargedRelease(wingman, target, owner);

        // 设置充能后的冷却
        wingman.setWeaponAttackCooldown(getChargeDurationTicks());
    }

    /**
     * 执行充能横扫攻击（剑类武器）
     * <p>
     * 与玩家充能横扫逻辑一致：
     * - 横扫伤害根据充能值提升（每点+10%，最高100%加成）
     * - 横扫范围根据充能值扩大（每点+10%，无上限）
     */
    private void executeChargedSweep(WingmanConstructEntity wingman, LivingEntity target,
                                      ItemStack weapon, Player owner, double chargeValue) {
        float baseDamage = (float) wingman.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float sweepDamageMultiplier = ChargedAttackSweepHandler.getSweepDamageMultiplier(chargeValue);
        float rangeMultiplier = ChargedAttackSweepHandler.getSweepRangeMultiplier(chargeValue);
        float sweepDamage = baseDamage * 0.15F * sweepDamageMultiplier;

        // 扇形范围搜索
        double baseRange = Config.getInterceptorChargedSweepBaseRange();
        double expandedRange = baseRange * rangeMultiplier;
        net.minecraft.world.phys.AABB searchBox = target.getBoundingBox().inflate(expandedRange);
        net.minecraft.world.phys.Vec3 wingmanPos = wingman.position();
        net.minecraft.world.phys.Vec3 lookVec = wingman.getLookAngle();

        // 多体节：体节不注册进 level 实体列表，只查父实体会漏掉伸出的身体节段；
        // 每个父实体只结算一次（命中箱取离僚机最近的那个）
        for (MultipartHitScan.Hit hit : MultipartHitScan.collectInRegion(
                wingman.getLevel(), searchBox, wingmanPos,
                entity -> entity != wingman && entity != target && entity instanceof LivingEntity living
                        && !wingman.isAlliedTo(living)
                        && wingman.canAttackType(entity.getType()),
                box -> box.getCenter().subtract(wingmanPos).dot(lookVec) > 0,
                false)) {
            LivingEntity entity = hit.target();

            // 移除无敌时间
            entity.invulnerableTime = 0;
            // 命中体节时由体节自身结算，保留各模组的体节倍率
            hit.hitbox().hurt(ModDamageSources.mobAttackWithGuardAggro(wingman, entity), sweepDamage);

            // 击退
            float yaw = wingman.getYRot() * ((float) Math.PI / 180F);
            entity.knockback(0.4F, -net.minecraft.util.Mth.sin(yaw), net.minecraft.util.Mth.cos(yaw));
        }
    }

    // ===== 辅助方法 =====

    /** 判断当前是否为近战模式 */
    private boolean isMeleeMode(WingmanConstructEntity wingman) {
        InterceptorWeaponMode weaponMode = InterceptorAttackModeManager.getCurrentWeaponMode(wingman);
        return weaponMode != null && "melee".equals(weaponMode.getSerializedName());
    }

    /** 获取近战reach距离 */
    private double getMeleeReach(WingmanConstructEntity wingman) {
        InterceptorWeaponMode weaponMode = InterceptorAttackModeManager.getCurrentWeaponMode(wingman);
        if (weaponMode instanceof MeleeWeaponMode meleeMode) {
            return meleeMode.getReachDistance(wingman);
        }
        return 3.0;
    }

    private void cancelCharge(WingmanConstructEntity wingman) {
        wingman.getPersistentData().remove(CHARGING_KEY);
        wingman.getPersistentData().remove(CHARGE_PROGRESS_KEY);
        wingman.getPersistentData().remove(CHARGE_VALUE_KEY);
        wingman.getPersistentData().remove(CHARGE_ASSAULT_COUNTER_KEY);
        wingman.getPersistentData().remove(PREV_HEALTH_KEY);
        wingman.getPersistentData().remove(PREV_SHIELD_KEY);
    }

    /**
     * 获取强袭触发计数器并递增
     */
    public static int getAndIncrementAssaultCounter(WingmanConstructEntity wingman) {
        int counter = wingman.getPersistentData().getInt(CHARGE_ASSAULT_COUNTER_KEY);
        wingman.getPersistentData().putInt(CHARGE_ASSAULT_COUNTER_KEY, counter + 1);
        return counter;
    }
}

