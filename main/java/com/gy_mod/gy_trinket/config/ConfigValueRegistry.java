package com.gy_mod.gy_trinket.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 配置项注册表（数据驱动）
 * <p>
 * 为"配置项"界面提供可在线调整的 Config 值清单：
 * 特殊机制（服务端 COMMON 配置）+ 自然恢复系统（服务端）+ 客户端 HUD（客户端配置）。
 * <p>
 * 服务端通过 {@link #applyServer} 做白名单校验并应用（配合 Config.SPEC.save() 落盘）；
 * 客户端通过 {@link #applyClientSync} 应用服务端同步值（跳过客户端专属项，防止覆盖本地 HUD 配置）。
 * <p>
 * 所有值统一以 double 传输：boolean 项为 1.0/0.0，int 项应用时四舍五入。
 */
public final class ConfigValueRegistry {
    /** 界面分组：特殊机制 */
    public static final int GROUP_MECHANICS = 0;
    /** 界面分组：自然恢复 */
    public static final int GROUP_RECOVERY = 1;
    /** 界面分组：客户端 HUD */
    public static final int GROUP_HUD = 2;

    public static final class Entry {
        /** 配置路径（section.key），同时作为翻译键后缀 */
        public final String id;
        public final int group;
        public final DoubleSupplier getter;
        public final DoubleConsumer applier;
        public final double min;
        public final double max;
        /** 布尔开关项（界面点击直接切换，值以 1.0/0.0 传输） */
        public final boolean bool;
        /** 客户端专属项（ClientConfig，不参与服务端同步，客户端修改后直接落盘） */
        public final boolean clientOnly;

        Entry(String id, int group, DoubleSupplier getter, DoubleConsumer applier,
              double min, double max, boolean bool, boolean clientOnly) {
            this.id = id;
            this.group = group;
            this.getter = getter;
            this.applier = applier;
            this.min = min;
            this.max = max;
            this.bool = bool;
            this.clientOnly = clientOnly;
        }

        public double clamp(double v) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private ConfigValueRegistry() {}

    private static void add(Entry e) {
        ENTRIES.add(e);
        BY_ID.put(e.id, e);
    }

    /** 注册服务端 double 配置项 */
    private static void d(String id, int group, DoubleSupplier getter, DoubleConsumer applier, double min, double max) {
        add(new Entry(id, group, getter, applier, min, max, false, false));
    }

    /** 注册服务端 int 配置项 */
    private static void i(String id, int group, DoubleSupplier getter, DoubleConsumer applier, int min, int max) {
        add(new Entry(id, group, getter, applier, min, max, false, false));
    }

    /** 注册服务端 boolean 配置项 */
    private static void b(String id, int group, DoubleSupplier getter, DoubleConsumer applier) {
        add(new Entry(id, group, getter, applier, 0, 1, true, false));
    }

    /** 注册客户端专属配置项 */
    private static void c(String id, int group, DoubleSupplier getter, DoubleConsumer applier,
                          double min, double max, boolean bool) {
        add(new Entry(id, group, getter, applier, min, max, bool, true));
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry byId(String id) {
        return BY_ID.get(id);
    }

    /**
     * 服务端应用配置值（白名单校验 + 范围钳制），成功返回 true。
     * 只改内存值，落盘由调用方执行 Config.SPEC.save()。
     */
    public static boolean applyServer(String id, double value) {
        Entry e = BY_ID.get(id);
        if (e == null || e.clientOnly) {
            return false;
        }
        e.applier.accept(e.clamp(value));
        return true;
    }

    /**
     * 客户端应用服务端同步值（跳过客户端专属项，防止广播覆盖本地 HUD 配置）。
     */
    public static void applyClientSync(List<String> ids, List<Double> values) {
        for (int i = 0; i < ids.size() && i < values.size(); i++) {
            Entry e = BY_ID.get(ids.get(i));
            if (e == null || e.clientOnly) {
                continue;
            }
            e.applier.accept(e.clamp(values.get(i)));
        }
    }

    static {
        // ==================== 特殊机制 ====================
        // 合成禁用
        i("crafting_disable.disableCraftingMode", GROUP_MECHANICS,
                () -> Config.DISABLE_CRAFTING_MODE.get(), v -> Config.DISABLE_CRAFTING_MODE.set((int) Math.round(v)), 0, 2);

        // 光环护盾
        d("aura_shield.auraRadius", GROUP_MECHANICS, () -> Config.AURA_RADIUS.get(), v -> Config.AURA_RADIUS.set(v), 0.0, 100.0);
        d("aura_shield.auraDamage", GROUP_MECHANICS, () -> Config.AURA_DAMAGE.get(), v -> Config.AURA_DAMAGE.set(v), 0.0, 100.0);
        i("aura_shield.auraTriggerFrequency", GROUP_MECHANICS, () -> Config.AURA_TRIGGER_FREQUENCY.get(), v -> Config.AURA_TRIGGER_FREQUENCY.set((int) Math.round(v)), 1, 200);
        d("aura_shield.auraShieldCost", GROUP_MECHANICS, () -> Config.AURA_SHIELD_COST.get(), v -> Config.AURA_SHIELD_COST.set(v), 0.0, 10.0);

        // 虹吸护盾
        d("siphon_shield.siphonRadius", GROUP_MECHANICS, () -> Config.SIPHON_RADIUS.get(), v -> Config.SIPHON_RADIUS.set(v), 0.0, 100.0);
        d("siphon_shield.siphonDamage", GROUP_MECHANICS, () -> Config.SIPHON_DAMAGE.get(), v -> Config.SIPHON_DAMAGE.set(v), 0.0, 100.0);
        i("siphon_shield.siphonTickInterval", GROUP_MECHANICS, () -> Config.SIPHON_TICK_INTERVAL.get(), v -> Config.SIPHON_TICK_INTERVAL.set((int) Math.round(v)), 1, 200);
        d("siphon_shield.siphonHealRatio", GROUP_MECHANICS, () -> Config.SIPHON_HEAL_RATIO.get(), v -> Config.SIPHON_HEAL_RATIO.set(v), 0.0, 1.0);
        i("siphon_shield.siphonDurationTicks", GROUP_MECHANICS, () -> Config.SIPHON_DURATION_TICKS.get(), v -> Config.SIPHON_DURATION_TICKS.set((int) Math.round(v)), 1, 200);
        d("siphon_shield.siphonEffectPerStack", GROUP_MECHANICS, () -> Config.SIPHON_EFFECT_PER_STACK.get(), v -> Config.SIPHON_EFFECT_PER_STACK.set(v), 0.001, 1.0);
        d("siphon_shield.siphonMaxEffect", GROUP_MECHANICS, () -> Config.SIPHON_MAX_EFFECT.get(), v -> Config.SIPHON_MAX_EFFECT.set(v), 0.01, 1.0);
        d("siphon_shield.siphonDecayRatio", GROUP_MECHANICS, () -> Config.SIPHON_DECAY_RATIO.get(), v -> Config.SIPHON_DECAY_RATIO.set(v), 0.0, 1.0);

        // 反射护盾
        d("reflect_shield.reflectRadius", GROUP_MECHANICS, () -> Config.REFLECT_RADIUS.get(), v -> Config.REFLECT_RADIUS.set(v), 0.0, 100.0);
        d("reflect_shield.reflectSpeedBaseModifier", GROUP_MECHANICS, () -> Config.REFLECT_SPEED_BASE_MODIFIER.get(), v -> Config.REFLECT_SPEED_BASE_MODIFIER.set(v), 0.0, 10.0);
        d("reflect_shield.reflectSpeedExtraModifier", GROUP_MECHANICS, () -> Config.REFLECT_SPEED_EXTRA_MODIFIER.get(), v -> Config.REFLECT_SPEED_EXTRA_MODIFIER.set(v), 0.0, 10.0);
        d("reflect_shield.reflectDamageEffectMultiplier", GROUP_MECHANICS, () -> Config.REFLECT_DAMAGE_EFFECT_MULTIPLIER.get(), v -> Config.REFLECT_DAMAGE_EFFECT_MULTIPLIER.set(v), 0.0, 10.0);

        // 增幅护盾
        d("amplification_shield.amplificationBaseAmplification", GROUP_MECHANICS, () -> Config.AMPLIFICATION_BASE_AMPLIFICATION.get(), v -> Config.AMPLIFICATION_BASE_AMPLIFICATION.set(v), 0.0, 2.0);
        d("amplification_shield.amplificationThreatAmplification", GROUP_MECHANICS, () -> Config.AMPLIFICATION_THREAT_AMPLIFICATION.get(), v -> Config.AMPLIFICATION_THREAT_AMPLIFICATION.set(v), 0.0, 1.0);
        d("amplification_shield.amplificationHealthAmplificationPerPoint", GROUP_MECHANICS, () -> Config.AMPLIFICATION_HEALTH_AMPLIFICATION_PER_POINT.get(), v -> Config.AMPLIFICATION_HEALTH_AMPLIFICATION_PER_POINT.set(v), 0.0, 0.1);
        d("amplification_shield.amplificationCheckRadius", GROUP_MECHANICS, () -> Config.AMPLIFICATION_CHECK_RADIUS.get(), v -> Config.AMPLIFICATION_CHECK_RADIUS.set(v), 1.0, 20.0);
        d("amplification_shield.amplificationMaxAmplification", GROUP_MECHANICS, () -> Config.AMPLIFICATION_MAX_AMPLIFICATION.get(), v -> Config.AMPLIFICATION_MAX_AMPLIFICATION.set(v), 0.0, 3.0);
        d("amplification_shield.amplificationMovementSpeedBonus", GROUP_MECHANICS, () -> Config.AMPLIFICATION_MOVEMENT_SPEED_BONUS.get(), v -> Config.AMPLIFICATION_MOVEMENT_SPEED_BONUS.set(v), 0.0, 2.0);

        // 跃传护盾
        i("warp_shield.warpShieldInvincibleDuration", GROUP_MECHANICS, () -> Config.WARP_SHIELD_INVINCIBLE_DURATION.get(), v -> Config.WARP_SHIELD_INVINCIBLE_DURATION.set((int) Math.round(v)), 1, 100);
        d("warp_shield.warpShieldExplosionDamage", GROUP_MECHANICS, () -> Config.WARP_SHIELD_EXPLOSION_DAMAGE.get(), v -> Config.WARP_SHIELD_EXPLOSION_DAMAGE.set(v), 0.0, 100.0);
        d("warp_shield.warpShieldExplosionRadius", GROUP_MECHANICS, () -> Config.WARP_SHIELD_EXPLOSION_RADIUS.get(), v -> Config.WARP_SHIELD_EXPLOSION_RADIUS.set(v), 1.0, 20.0);
        d("warp_shield.warpShieldWarpDistance", GROUP_MECHANICS, () -> Config.WARP_SHIELD_WARP_DISTANCE.get(), v -> Config.WARP_SHIELD_WARP_DISTANCE.set(v), 1.0, 20.0);

        // 屏障
        d("barrier.barrierMaxDamage", GROUP_MECHANICS, () -> Config.BARRIER_MAX_DAMAGE.get(), v -> Config.BARRIER_MAX_DAMAGE.set(v), 0.0, 1000.0);

        // 反射护盾伤害处理器
        d("reflect_damage.reflectDamageBaseDamage", GROUP_MECHANICS, () -> Config.REFLECT_DAMAGE_BASE_DAMAGE.get(), v -> Config.REFLECT_DAMAGE_BASE_DAMAGE.set(v), 0.0, 10.0);
        d("reflect_damage.reflectDamageRayLength", GROUP_MECHANICS, () -> Config.REFLECT_DAMAGE_RAY_LENGTH.get(), v -> Config.REFLECT_DAMAGE_RAY_LENGTH.set(v), 1.0, 20.0);

        // 易爆护盾
        d("explosive_shield.explosiveShieldDamage", GROUP_MECHANICS, () -> Config.EXPLOSIVE_SHIELD_DAMAGE.get(), v -> Config.EXPLOSIVE_SHIELD_DAMAGE.set(v), 0.0, 100.0);
        d("explosive_shield.explosiveShieldRadius", GROUP_MECHANICS, () -> Config.EXPLOSIVE_SHIELD_RADIUS.get(), v -> Config.EXPLOSIVE_SHIELD_RADIUS.set(v), 0.0, 10.0);

        // 电能释放
        d("electric_discharge.electricDischargeBurnCharge", GROUP_MECHANICS, () -> Config.ELECTRIC_DISCHARGE_BURN_CHARGE.get(), v -> Config.ELECTRIC_DISCHARGE_BURN_CHARGE.set(v), 0.1, 10.0);
        i("electric_discharge.electricDischargeBurnDuration", GROUP_MECHANICS, () -> Config.ELECTRIC_DISCHARGE_BURN_DURATION.get(), v -> Config.ELECTRIC_DISCHARGE_BURN_DURATION.set((int) Math.round(v)), 1, 200);

        // 武器化护盾
        d("weaponized_shield.weaponizedShieldVulnerability", GROUP_MECHANICS, () -> Config.WEAPONIZED_SHIELD_VULNERABILITY.get(), v -> Config.WEAPONIZED_SHIELD_VULNERABILITY.set(v), 0.0, 10.0);
        d("weaponized_shield.weaponizedShieldRadius", GROUP_MECHANICS, () -> Config.WEAPONIZED_SHIELD_RADIUS.get(), v -> Config.WEAPONIZED_SHIELD_RADIUS.set(v), 1.0, 20.0);

        // 镀层
        d("coating_system.coatingReductionPerLayer", GROUP_MECHANICS, () -> Config.COATING_REDUCTION_PER_LAYER.get(), v -> Config.COATING_REDUCTION_PER_LAYER.set(v), 0.0, 10.0);

        // 适应性装甲
        i("adaptive_armor.adaptiveArmorDuration", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_DURATION.get(), v -> Config.ADAPTIVE_ARMOR_DURATION.set((int) Math.round(v)), 1, 6000);
        i("adaptive_armor.adaptiveArmorMaxLayersPerHit", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.get(), v -> Config.ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.set((int) Math.round(v)), 1, 10000);
        d("adaptive_armor.adaptiveArmorLayersPerDamage", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.get(), v -> Config.ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.set(v), 0.1, 10.0);

        // 弧形屏障
        d("arc_barrier.positionDeviationThreshold", GROUP_MECHANICS, () -> Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get(), v -> Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.set(v), 0.5, 10.0);

        // 反制脉冲
        i("counter_pulse.cooldown", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_COOLDOWN.get(), v -> Config.COUNTER_PULSE_COOLDOWN.set((int) Math.round(v)), 20, 600);
        d("counter_pulse.baseExplosionRadius", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get(), v -> Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.set(v), 0.5, 10.0);
        d("counter_pulse.baseExplosionDamage", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get(), v -> Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.set(v), 0.1, 100.0);
        i("counter_pulse.chargeInterval", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_CHARGE_INTERVAL.get(), v -> Config.COUNTER_PULSE_CHARGE_INTERVAL.set((int) Math.round(v)), 1, 100);
        i("counter_pulse.maxChargeLevel", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.get(), v -> Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.set((int) Math.round(v)), 10, 10000);

        // 重塑
        d("reshaping.healRate", GROUP_MECHANICS, () -> Config.RESHAPING_HEAL_RATE.get(), v -> Config.RESHAPING_HEAL_RATE.set(v), 0.0, 1.0);
        d("reshaping.baseDamageReduction", GROUP_MECHANICS, () -> Config.RESHAPING_BASE_DAMAGE_REDUCTION.get(), v -> Config.RESHAPING_BASE_DAMAGE_REDUCTION.set(v), 0.0, 100.0);
        i("reshaping.damageReductionDuration", GROUP_MECHANICS, () -> Config.RESHAPING_DAMAGE_REDUCTION_DURATION.get(), v -> Config.RESHAPING_DAMAGE_REDUCTION_DURATION.set((int) Math.round(v)), 20, 6000);

        // 自毁装置
        d("self_destruct.selfDestructBaseDamage", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_BASE_DAMAGE.get(), v -> Config.SELF_DESTRUCT_BASE_DAMAGE.set(v), 0.0, 1000.0);
        d("self_destruct.selfDestructBaseRadius", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_BASE_RADIUS.get(), v -> Config.SELF_DESTRUCT_BASE_RADIUS.set(v), 0.0, 100.0);
        d("self_destruct.selfDestructDamagePerMaxHealth", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.get(), v -> Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.set(v), 0.0, 100.0);
        d("self_destruct.selfDestructRadiusPerMaxHealth", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.get(), v -> Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.set(v), 0.0, 10.0);

        // 积怨
        d("grudge.conversionRatio", GROUP_MECHANICS, () -> Config.GRUDGE_CONVERSION_RATIO.get(), v -> Config.GRUDGE_CONVERSION_RATIO.set(v), 0.001, 1.0);
        d("grudge.fadeBase", GROUP_MECHANICS, () -> Config.GRUDGE_FADE_BASE.get(), v -> Config.GRUDGE_FADE_BASE.set(v), 0.0, 10.0);
        d("grudge.fadePercent", GROUP_MECHANICS, () -> Config.GRUDGE_FADE_PERCENT.get(), v -> Config.GRUDGE_FADE_PERCENT.set(v), 0.0, 1.0);
        d("grudge.movementSpeedPenalty", GROUP_MECHANICS, () -> Config.GRUDGE_MOVEMENT_SPEED_PENALTY.get(), v -> Config.GRUDGE_MOVEMENT_SPEED_PENALTY.set(v), -0.99, 0.0);

        // 幽灵机身
        d("ghost_fuselage.stealthSpeedBonusPerLevel", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_STEALTH_SPEED_BONUS_PER_LEVEL.get(), v -> Config.GHOST_FUSELAGE_STEALTH_SPEED_BONUS_PER_LEVEL.set(v), 0.0, 1.0);
        d("ghost_fuselage.maxBonusPerLevel", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_MAX_BONUS_PER_LEVEL.get(), v -> Config.GHOST_FUSELAGE_MAX_BONUS_PER_LEVEL.set(v), 0.0, 1.0);
        d("ghost_fuselage.baseMaxDamageBonus", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_BASE_MAX_DAMAGE_BONUS.get(), v -> Config.GHOST_FUSELAGE_BASE_MAX_DAMAGE_BONUS.set(v), 0.0, 100.0);
        d("ghost_fuselage.moveSpeedThreshold", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_MOVE_SPEED_THRESHOLD.get(), v -> Config.GHOST_FUSELAGE_MOVE_SPEED_THRESHOLD.set(v), 0.0, 10.0);
        d("ghost_fuselage.moveSpeedReduction", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_MOVE_SPEED_REDUCTION.get(), v -> Config.GHOST_FUSELAGE_MOVE_SPEED_REDUCTION.set(v), 0.0, 1.0);
        d("ghost_fuselage.decayRate", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_DECAY_RATE.get(), v -> Config.GHOST_FUSELAGE_DECAY_RATE.set(v), 0.01, 0.99);
        d("ghost_fuselage.minDecay", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_MIN_DECAY.get(), v -> Config.GHOST_FUSELAGE_MIN_DECAY.set(v), 0.0, 0.1);
        i("ghost_fuselage.fullStealthTicks", GROUP_MECHANICS, () -> Config.GHOST_FUSELAGE_FULL_STEALTH_TICKS.get(), v -> Config.GHOST_FUSELAGE_FULL_STEALTH_TICKS.set((int) Math.round(v)), 1, 600);

        // 充能护盾
        d("charged_shield.chargeRatio", GROUP_MECHANICS, () -> Config.CHARGED_SHIELD_CHARGE_RATIO.get(), v -> Config.CHARGED_SHIELD_CHARGE_RATIO.set(v), 0.01, 1.0);
        d("charged_shield.maxBonus", GROUP_MECHANICS, () -> Config.CHARGED_SHIELD_MAX_BONUS.get(), v -> Config.CHARGED_SHIELD_MAX_BONUS.set(v), 0.1, 5.0);
        d("charged_shield.decayRate", GROUP_MECHANICS, () -> Config.CHARGED_SHIELD_DECAY_RATE.get(), v -> Config.CHARGED_SHIELD_DECAY_RATE.set(v), 0.005, 0.5);
        d("charged_shield.movementSpeedPenalty", GROUP_MECHANICS, () -> Config.CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY.get(), v -> Config.CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY.set(v), -0.99, 0.0);

        // 强袭
        d("assault.attackSpeedPerStack", GROUP_MECHANICS, () -> Config.ASSAULT_ATTACK_SPEED_PER_STACK.get(), v -> Config.ASSAULT_ATTACK_SPEED_PER_STACK.set(v), 0.01, 1.0);
        i("assault.durationTicks", GROUP_MECHANICS, () -> Config.ASSAULT_DURATION_TICKS.get(), v -> Config.ASSAULT_DURATION_TICKS.set((int) Math.round(v)), 10, 200);
        d("assault.selfDamagePerStack", GROUP_MECHANICS, () -> Config.ASSAULT_SELF_DAMAGE_PER_STACK.get(), v -> Config.ASSAULT_SELF_DAMAGE_PER_STACK.set(v), 0.01, 10.0);
        d("assault.movementSpeedPenalty", GROUP_MECHANICS, () -> Config.ASSAULT_MOVEMENT_SPEED_PENALTY.get(), v -> Config.ASSAULT_MOVEMENT_SPEED_PENALTY.set(v), -0.99, 0.0);
        d("assault.overflowDamageEfficiency", GROUP_MECHANICS, () -> Config.ASSAULT_OVERFLOW_DAMAGE_EFFICIENCY.get(), v -> Config.ASSAULT_OVERFLOW_DAMAGE_EFFICIENCY.set(v), 0.0, 1.0);

        // 远征
        d("journey.attackSpeedPerStack", GROUP_MECHANICS, () -> Config.JOURNEY_ATTACK_SPEED_PER_STACK.get(), v -> Config.JOURNEY_ATTACK_SPEED_PER_STACK.set(v), 0.001, 0.1);
        d("journey.movementSpeedPerStack", GROUP_MECHANICS, () -> Config.JOURNEY_MOVEMENT_SPEED_PER_STACK.get(), v -> Config.JOURNEY_MOVEMENT_SPEED_PER_STACK.set(v), 0.001, 0.1);
        i("journey.durationTicks", GROUP_MECHANICS, () -> Config.JOURNEY_DURATION_TICKS.get(), v -> Config.JOURNEY_DURATION_TICKS.set((int) Math.round(v)), 1, 600);
        i("journey.maxStacks", GROUP_MECHANICS, () -> Config.JOURNEY_MAX_STACKS.get(), v -> Config.JOURNEY_MAX_STACKS.set((int) Math.round(v)), 1, 100);
        i("journey.decayIntervalTicks", GROUP_MECHANICS, () -> Config.JOURNEY_DECAY_INTERVAL_TICKS.get(), v -> Config.JOURNEY_DECAY_INTERVAL_TICKS.set((int) Math.round(v)), 1, 100);
        i("journey.decayPerInterval", GROUP_MECHANICS, () -> Config.JOURNEY_DECAY_PER_INTERVAL.get(), v -> Config.JOURNEY_DECAY_PER_INTERVAL.set((int) Math.round(v)), 1, 40);

        // 蓄能攻击
        d("charged_attack.baseChargeRate", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_BASE_CHARGE_RATE.get(), v -> Config.CHARGED_ATTACK_BASE_CHARGE_RATE.set(v), 0.0, 10.0);
        d("charged_attack.speedScaleFactor", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_SPEED_SCALE_FACTOR.get(), v -> Config.CHARGED_ATTACK_SPEED_SCALE_FACTOR.set(v), 0.0, 10.0);
        d("charged_attack.dragCoefficient", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_DRAG_COEFFICIENT.get(), v -> Config.CHARGED_ATTACK_DRAG_COEFFICIENT.set(v), 0.0, 10.0);
        d("charged_attack.dragThresholdFactor", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.get(), v -> Config.CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.set(v), 0.1, 100.0);
        d("charged_attack.movementSpeedPenalty", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.get(), v -> Config.CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.set(v), -0.99, 0.0);
        d("charged_attack.itemUseChargeDefaultSpeedModifier", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER.get(), v -> Config.CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER.set(v), -4.0, 0.0);

        // 护盾转移
        d("shield_transfer.effectPenaltyPerEntity", GROUP_MECHANICS, () -> Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get(), v -> Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.set(v), 0.0, 1.0);

        // 转换
        d("conversion.conversionRatio", GROUP_MECHANICS, () -> Config.CONVERSION_RATIO.get(), v -> Config.CONVERSION_RATIO.set(v), 0.0, 1.0);

        // 濒死保护
        i("near_death_protection.nearDeathProtectionCooldown", GROUP_MECHANICS, () -> Config.NEAR_DEATH_PROTECTION_COOLDOWN.get(), v -> Config.NEAR_DEATH_PROTECTION_COOLDOWN.set((int) Math.round(v)), 20, 6000);
        i("near_death_protection.nearDeathProtectionInvincibleDuration", GROUP_MECHANICS, () -> Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get(), v -> Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.set((int) Math.round(v)), 1, 200);

        // 濒死爆炸
        i("near_death_explosion.nearDeathExplosionInvincibleDuration", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get(), v -> Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.set((int) Math.round(v)), 20, 6000);
        d("near_death_explosion.nearDeathExplosionCoefficient", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get(), v -> Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.set(v), 0.1, 100.0);
        d("near_death_explosion.nearDeathExplosionRadius", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_RADIUS.get(), v -> Config.NEAR_DEATH_EXPLOSION_RADIUS.set(v), 0.5, 20.0);
        d("near_death_explosion.nearDeathExplosionSearchRadius", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.get(), v -> Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.set(v), 5.0, 100.0);
        d("near_death_explosion.nearDeathExplosionInitialSpeed", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_INITIAL_SPEED.get(), v -> Config.NEAR_DEATH_EXPLOSION_INITIAL_SPEED.set(v), 0.01, 2.0);
        d("near_death_explosion.nearDeathExplosionSpeedAcceleration", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION.get(), v -> Config.NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION.set(v), 0.001, 1.0);

        // 无人机处决
        b("drone_execute.droneExecuteEnabled", GROUP_MECHANICS, () -> Config.DRONE_EXECUTE_ENABLED.get() ? 1.0 : 0.0, v -> Config.DRONE_EXECUTE_ENABLED.set(v != 0.0));
        // 高级工程学
        d("advanced_engineering.advancedEngineeringBonusPerLevel", GROUP_MECHANICS, () -> Config.ADVANCED_ENGINEERING_BONUS_PER_LEVEL.get(), v -> Config.ADVANCED_ENGINEERING_BONUS_PER_LEVEL.set(v), 0.0, 1.0);
        // 精密构造
        d("precision_construct.precisionConstructBonusPerLevel", GROUP_MECHANICS, () -> Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get(), v -> Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.set(v), 0.0, 1.0);
        // 敌对目标标记
        i("hostile_target.markDuration", GROUP_MECHANICS, () -> Config.HOSTILE_TARGET_MARK_DURATION.get(), v -> Config.HOSTILE_TARGET_MARK_DURATION.set((int) Math.round(v)), 1, 10000);
        // 格挡无敌帧
        i("blockInvulnerableTicks", GROUP_MECHANICS, () -> Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get(), v -> Config.SHIELD_BLOCK_INVULNERABLE_TICKS.set((int) Math.round(v)), 0, 100);

        // 点燃系统
        d("ignite_system.igniteDefaultDamage", GROUP_MECHANICS, () -> Config.IGNITE_DEFAULT_DAMAGE.get(), v -> Config.IGNITE_DEFAULT_DAMAGE.set(v), 0.0, 100.0);
        i("ignite_system.igniteDefaultDuration", GROUP_MECHANICS, () -> Config.IGNITE_DEFAULT_DURATION.get(), v -> Config.IGNITE_DEFAULT_DURATION.set((int) Math.round(v)), 1, 600);

        // 二次伤害合并
        b("secondary_damage_merge.secondaryDamageMergeEnabled", GROUP_MECHANICS, () -> Config.SECONDARY_DAMAGE_MERGE_ENABLED.get() ? 1.0 : 0.0, v -> Config.SECONDARY_DAMAGE_MERGE_ENABLED.set(v != 0.0));
        i("secondary_damage_merge.secondaryDamageMergeWindowTicks", GROUP_MECHANICS, () -> Config.SECONDARY_DAMAGE_MERGE_WINDOW_TICKS.get(), v -> Config.SECONDARY_DAMAGE_MERGE_WINDOW_TICKS.set((int) Math.round(v)), 1, 100);

        // 二次爆炸
        d("secondary_explosion.secondaryExplosionDamageFraction", GROUP_MECHANICS, () -> Config.SECONDARY_EXPLOSION_DAMAGE_FRACTION.get(), v -> Config.SECONDARY_EXPLOSION_DAMAGE_FRACTION.set(v), 0.0, 1.0);
        d("secondary_explosion.secondaryExplosionRadiusBase", GROUP_MECHANICS, () -> Config.SECONDARY_EXPLOSION_RADIUS_BASE.get(), v -> Config.SECONDARY_EXPLOSION_RADIUS_BASE.set(v), 0.0, 32.0);
        d("secondary_explosion.secondaryExplosionRadiusDamageFraction", GROUP_MECHANICS, () -> Config.SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION.get(), v -> Config.SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION.set(v), 0.0, 8.0);

        // ==================== 自然恢复 ====================
        d("shield_natural_recovery.naturalRecoveryShieldRecoveryPerTick", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK.get(), v -> Config.NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK.set(v), 0.0, 0.1);
        d("shield_natural_recovery.naturalRecoveryShieldPresentHealthModifier", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER.get(), v -> Config.NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER.set(v), 0.0, 1.0);
        d("shield_natural_recovery.naturalRecoveryShieldPresentShieldModifier", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER.get(), v -> Config.NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER.set(v), 0.0, 2.0);

        b("natural_recovery.playerHealthEnabled", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED.get() ? 1.0 : 0.0, v -> Config.NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED.set(v != 0.0));
        d("natural_recovery.naturalRecoveryPlayerHealth", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_PLAYER_HEALTH.get(), v -> Config.NATURAL_RECOVERY_PLAYER_HEALTH.set(v), 0.0, 10.0);
        d("natural_recovery.naturalRecoveryShield", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_SHIELD.get(), v -> Config.NATURAL_RECOVERY_SHIELD.set(v), 0.0, 10.0);
        d("natural_recovery.naturalRecoveryAttackCooldownPenalty", GROUP_RECOVERY, () -> Config.NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY.get(), v -> Config.NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY.set(v), 0.0, 1.0);

        // ==================== 客户端 HUD（客户端专属，不走网络同步） ====================
        c("shield_idle_particle.enabled", GROUP_HUD, () -> ClientConfig.SHIELD_IDLE_PARTICLE_ENABLED.get() ? 1.0 : 0.0, v -> ClientConfig.SHIELD_IDLE_PARTICLE_ENABLED.set(v != 0.0), 0, 1, true);
        c("shield_particle_rendering.volumetricRendering", GROUP_HUD, () -> ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.get() ? 1.0 : 0.0, v -> ClientConfig.SHIELD_PARTICLE_VOLUMETRIC_RENDERING.set(v != 0.0), 0, 1, true);

        c("shield_hud.vanillaStyle", GROUP_HUD, () -> ClientConfig.VANILLA_STYLE_HUD.get() ? 1.0 : 0.0, v -> ClientConfig.VANILLA_STYLE_HUD.set(v != 0.0), 0, 1, true);

        c("shield_hud.default_style.hudDefaultOffsetX", GROUP_HUD, () -> ClientConfig.HUD_DEFAULT_OFFSET_X.get(), v -> ClientConfig.HUD_DEFAULT_OFFSET_X.set((int) Math.round(v)), -500, 500, false);
        c("shield_hud.default_style.hudDefaultOffsetY", GROUP_HUD, () -> ClientConfig.HUD_DEFAULT_OFFSET_Y.get(), v -> ClientConfig.HUD_DEFAULT_OFFSET_Y.set((int) Math.round(v)), 0, 500, false);
        c("shield_hud.default_style.hudBarWidth", GROUP_HUD, () -> ClientConfig.HUD_DEFAULT_BAR_WIDTH.get(), v -> ClientConfig.HUD_DEFAULT_BAR_WIDTH.set((int) Math.round(v)), 10, 500, false);
        c("shield_hud.default_style.hudBarHeight", GROUP_HUD, () -> ClientConfig.HUD_DEFAULT_BAR_HEIGHT.get(), v -> ClientConfig.HUD_DEFAULT_BAR_HEIGHT.set((int) Math.round(v)), 1, 50, false);
        c("shield_hud.default_style.hudCooldownHeight", GROUP_HUD, () -> ClientConfig.HUD_DEFAULT_COOLDOWN_HEIGHT.get(), v -> ClientConfig.HUD_DEFAULT_COOLDOWN_HEIGHT.set((int) Math.round(v)), 1, 50, false);

        c("shield_hud.vanilla_style.hudVanillaScale", GROUP_HUD, () -> ClientConfig.VANILLA_STYLE_HUD_SCALE.get(), v -> ClientConfig.VANILLA_STYLE_HUD_SCALE.set(v), 0.1, 3.0, false);
        c("shield_hud.vanilla_style.hudVanillaOffsetX", GROUP_HUD, () -> ClientConfig.HUD_VANILLA_OFFSET_X.get(), v -> ClientConfig.HUD_VANILLA_OFFSET_X.set((int) Math.round(v)), -500, 500, false);
        c("shield_hud.vanilla_style.hudVanillaOffsetY", GROUP_HUD, () -> ClientConfig.HUD_VANILLA_OFFSET_Y.get(), v -> ClientConfig.HUD_VANILLA_OFFSET_Y.set((int) Math.round(v)), -500, 500, false);
        c("shield_hud.vanilla_style.hudVanillaCooldownAlpha", GROUP_HUD, () -> ClientConfig.HUD_VANILLA_COOLDOWN_ALPHA.get(), v -> ClientConfig.HUD_VANILLA_COOLDOWN_ALPHA.set(v), 0.0, 1.0, false);
        c("shield_hud.vanilla_style.hudVanillaTextOffsetX", GROUP_HUD, () -> ClientConfig.HUD_VANILLA_TEXT_OFFSET_X.get(), v -> ClientConfig.HUD_VANILLA_TEXT_OFFSET_X.set((int) Math.round(v)), -500, 500, false);
        c("shield_hud.vanilla_style.hudVanillaTextOffsetY", GROUP_HUD, () -> ClientConfig.HUD_VANILLA_TEXT_OFFSET_Y.get(), v -> ClientConfig.HUD_VANILLA_TEXT_OFFSET_Y.set((int) Math.round(v)), -500, 500, false);
    }
}