package com.gytrinket.gytrinket.config;

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
        /** 多选一选项数（0 = 普通数值，点击打开数值编辑框；>0 = 点击在 0..choices-1 之间循环） */
        public final int choices;

        Entry(String id, int group, DoubleSupplier getter, DoubleConsumer applier,
              double min, double max, boolean bool, boolean clientOnly, int choices) {
            this.id = id;
            this.group = group;
            this.getter = getter;
            this.applier = applier;
            this.min = min;
            this.max = max;
            this.bool = bool;
            this.clientOnly = clientOnly;
            this.choices = choices;
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
        add(new Entry(id, group, getter, applier, min, max, false, false, 0));
    }

    /** 注册服务端 int 配置项 */
    private static void i(String id, int group, DoubleSupplier getter, DoubleConsumer applier, int min, int max) {
        add(new Entry(id, group, getter, applier, min, max, false, false, 0));
    }

    /** 注册服务端 boolean 配置项 */
    private static void b(String id, int group, DoubleSupplier getter, DoubleConsumer applier) {
        add(new Entry(id, group, getter, applier, 0, 1, true, false, 0));
    }

    /** 注册客户端专属配置项 */
    private static void c(String id, int group, DoubleSupplier getter, DoubleConsumer applier,
                          double min, double max, boolean bool) {
        add(new Entry(id, group, getter, applier, min, max, bool, true, 0));
    }

    /** 注册客户端专属的「多选一」配置项：界面点击在 0..choices-1 之间循环 */
    private static void ce(String id, int group, DoubleSupplier getter, DoubleConsumer applier, int choices) {
        add(new Entry(id, group, getter, applier, 0, choices - 1, false, true, choices));
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

        // 随机构建系统（构建池）
        b("random_build.enabled", GROUP_MECHANICS, () -> Config.RANDOM_BUILD_ENABLED.get() ? 1.0 : 0.0, v -> Config.RANDOM_BUILD_ENABLED.set(v != 0.0));
        i("random_build.upgradePointsMultiplier", GROUP_MECHANICS, () -> Config.RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER.get(), v -> Config.RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER.set((int) Math.round(v)), 1, 100);
        b("random_build.showUpgradeReminderHud", GROUP_MECHANICS, () -> Config.SHOW_UPGRADE_REMINDER_HUD.get() ? 1.0 : 0.0, v -> Config.SHOW_UPGRADE_REMINDER_HUD.set(v != 0.0));
        b("random_build.tokenEnabled", GROUP_MECHANICS, () -> Config.RANDOM_BUILD_TOKEN_ENABLED.get() ? 1.0 : 0.0, v -> Config.RANDOM_BUILD_TOKEN_ENABLED.set(v != 0.0));

        // 光环 / 虹吸 / 反射 / 增幅 / 跃传护盾：
        // 数值已迁移到 item_definitions 物品级定义（shieldValues），无全局配置项

        // 屏障 / 易爆护盾 / 电能释放 / 武器化护盾：
        // 数值已迁移到 item_definitions 的 values 段，无全局配置项

        // 反射护盾伤害处理器
        d("reflect_damage.reflectDamageBaseDamage", GROUP_MECHANICS, () -> Config.REFLECT_DAMAGE_BASE_DAMAGE.get(), v -> Config.REFLECT_DAMAGE_BASE_DAMAGE.set(v), 0.0, 10.0);
        d("reflect_damage.reflectDamageRayLength", GROUP_MECHANICS, () -> Config.REFLECT_DAMAGE_RAY_LENGTH.get(), v -> Config.REFLECT_DAMAGE_RAY_LENGTH.set(v), 1.0, 20.0);

        // 镀层
        d("coating_system.coatingReductionPerLayer", GROUP_MECHANICS, () -> Config.COATING_REDUCTION_PER_LAYER.get(), v -> Config.COATING_REDUCTION_PER_LAYER.set(v), 0.0, 10.0);

        // 适应性装甲
        i("adaptive_armor.adaptiveArmorDuration", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_DURATION.get(), v -> Config.ADAPTIVE_ARMOR_DURATION.set((int) Math.round(v)), 1, 6000);
        i("adaptive_armor.adaptiveArmorMaxLayersPerHit", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.get(), v -> Config.ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.set((int) Math.round(v)), 1, 10000);
        d("adaptive_armor.adaptiveArmorLayersPerDamage", GROUP_MECHANICS, () -> Config.ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.get(), v -> Config.ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.set(v), 0.1, 10.0);

        // 弧形屏障
        d("arc_barrier.positionDeviationThreshold", GROUP_MECHANICS, () -> Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get(), v -> Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.set(v), 0.5, 10.0);

        // 反制脉冲：爆炸半径/伤害/冷却已迁移到 values 段；充能间隔/最大层数仍为全局
        i("counter_pulse.chargeInterval", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_CHARGE_INTERVAL.get(), v -> Config.COUNTER_PULSE_CHARGE_INTERVAL.set((int) Math.round(v)), 1, 100);
        i("counter_pulse.maxChargeLevel", GROUP_MECHANICS, () -> Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.get(), v -> Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.set((int) Math.round(v)), 10, 10000);

        // 重塑
        d("reshaping.healRate", GROUP_MECHANICS, () -> Config.RESHAPING_HEAL_RATE.get(), v -> Config.RESHAPING_HEAL_RATE.set(v), 0.0, 1.0);
        d("reshaping.baseDamageReduction", GROUP_MECHANICS, () -> Config.RESHAPING_BASE_DAMAGE_REDUCTION.get(), v -> Config.RESHAPING_BASE_DAMAGE_REDUCTION.set(v), 0.0, 100.0);
        i("reshaping.damageReductionDuration", GROUP_MECHANICS, () -> Config.RESHAPING_DAMAGE_REDUCTION_DURATION.get(), v -> Config.RESHAPING_DAMAGE_REDUCTION_DURATION.set((int) Math.round(v)), 20, 6000);

        // 自毁装置：基础伤害/半径已迁移到 values 段；每点最大生命增量仍为全局
        d("self_destruct.selfDestructDamagePerMaxHealth", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.get(), v -> Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.set(v), 0.0, 100.0);
        d("self_destruct.selfDestructRadiusPerMaxHealth", GROUP_MECHANICS, () -> Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.get(), v -> Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.set(v), 0.0, 10.0);

        // 积怨：数值已迁移到 values 段，无全局配置项

        // 幽灵机身：数值已迁移到 values 段，无全局配置项

        // 充能护盾：数值已迁移到 values 段，无全局配置项

        // 强袭：数值已迁移到 values 段，无全局配置项

        // 远征：数值已迁移到 values 段，无全局配置项

        // 蓄能攻击
        d("charged_attack.baseChargeRate", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_BASE_CHARGE_RATE.get(), v -> Config.CHARGED_ATTACK_BASE_CHARGE_RATE.set(v), 0.0, 10.0);
        d("charged_attack.speedScaleFactor", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_SPEED_SCALE_FACTOR.get(), v -> Config.CHARGED_ATTACK_SPEED_SCALE_FACTOR.set(v), 0.0, 10.0);
        d("charged_attack.dragCoefficient", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_DRAG_COEFFICIENT.get(), v -> Config.CHARGED_ATTACK_DRAG_COEFFICIENT.set(v), 0.0, 10.0);
        d("charged_attack.dragThresholdFactor", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.get(), v -> Config.CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.set(v), 0.1, 100.0);
        d("charged_attack.movementSpeedPenalty", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.get(), v -> Config.CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.set(v), -0.99, 0.0);
        d("charged_attack.itemUseChargeDefaultSpeedModifier", GROUP_MECHANICS, () -> Config.CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER.get(), v -> Config.CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER.set(v), -4.0, 0.0);

        // 护盾转移：数值已迁移到 item_definitions 的 values 段，无全局配置项

        // 转换：数值已迁移到 values 段，无全局配置项

        // 濒死保护：数值已迁移到 values 段，无全局配置项

        // 濒死爆炸
        i("near_death_explosion.nearDeathExplosionInvincibleDuration", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get(), v -> Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.set((int) Math.round(v)), 20, 6000);
        d("near_death_explosion.nearDeathExplosionCoefficient", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get(), v -> Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.set(v), 0.1, 100.0);
        d("near_death_explosion.nearDeathExplosionRadius", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_RADIUS.get(), v -> Config.NEAR_DEATH_EXPLOSION_RADIUS.set(v), 0.5, 20.0);
        d("near_death_explosion.nearDeathExplosionSearchRadius", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.get(), v -> Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.set(v), 5.0, 100.0);
        d("near_death_explosion.nearDeathExplosionInitialSpeed", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_INITIAL_SPEED.get(), v -> Config.NEAR_DEATH_EXPLOSION_INITIAL_SPEED.set(v), 0.01, 2.0);
        d("near_death_explosion.nearDeathExplosionSpeedAcceleration", GROUP_MECHANICS, () -> Config.NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION.get(), v -> Config.NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION.set(v), 0.001, 1.0);

        // 追击阵列/突击无人机：基础属性已迁移到 values 段；仅跟随范围仍为全局
        d("pursuit_array.droneFollowRange", GROUP_MECHANICS, () -> Config.DRONE_FOLLOW_RANGE.get(), v -> Config.DRONE_FOLLOW_RANGE.set(v), 4.0, 64.0);

        // 无人机处决
        b("drone_execute.droneExecuteEnabled", GROUP_MECHANICS, () -> Config.DRONE_EXECUTE_ENABLED.get() ? 1.0 : 0.0, v -> Config.DRONE_EXECUTE_ENABLED.set(v != 0.0));

        // 指挥官
        i("commander.commanderMaxCount", GROUP_MECHANICS, () -> Config.COMMANDER_MAX_COUNT.get(), v -> Config.COMMANDER_MAX_COUNT.set((int) Math.round(v)), 1, 10);
        i("commander.commanderAppointDelay", GROUP_MECHANICS, () -> Config.COMMANDER_APPOINT_DELAY.get(), v -> Config.COMMANDER_APPOINT_DELAY.set((int) Math.round(v)), 10, 200);
        d("commander.commanderVulnerability", GROUP_MECHANICS, () -> Config.COMMANDER_VULNERABILITY.get(), v -> Config.COMMANDER_VULNERABILITY.set(v), 0.001, 1.0);

        // 僚机：基础生命/数量/间隔/范围/爆破弹伤害已迁移到 values 段
        i("wingman.wingmanExplosiveCount", GROUP_MECHANICS, () -> Config.WINGMAN_EXPLOSIVE_COUNT.get(), v -> Config.WINGMAN_EXPLOSIVE_COUNT.set((int) Math.round(v)), 1, 20);
        d("wingman.wingmanExplosionDamage", GROUP_MECHANICS, () -> Config.WINGMAN_EXPLOSION_DAMAGE.get(), v -> Config.WINGMAN_EXPLOSION_DAMAGE.set(v), 0.01, 100.0);
        d("wingman.wingmanExplosionRadius", GROUP_MECHANICS, () -> Config.WINGMAN_EXPLOSION_RADIUS.get(), v -> Config.WINGMAN_EXPLOSION_RADIUS.set(v), 0.1, 10.0);
        i("wingman.wingmanInterceptorChargeDurationTicks", GROUP_MECHANICS, () -> Config.WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS.get(), v -> Config.WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS.set((int) Math.round(v)), 10, 600);
        i("wingman.wingmanInterceptorMaxChargeDurationTicks", GROUP_MECHANICS, () -> Config.WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS.get(), v -> Config.WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS.set((int) Math.round(v)), 20, 1200);
        d("wingman.wingmanInterceptorChargedSweepBaseRange", GROUP_MECHANICS, () -> Config.WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE.get(), v -> Config.WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE.set(v), 0.5, 10.0);
        d("wingman.wingmanShockwaveDamageMultiplier", GROUP_MECHANICS, () -> Config.WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER.get(), v -> Config.WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER.set(v), 1.0, 10.0);
        d("wingman.wingmanShockwaveSplashLengthMultiplier", GROUP_MECHANICS, () -> Config.WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER.get(), v -> Config.WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER.set(v), 1.0, 10.0);

        // 蜂群：基础属性/构建时间已迁移到 values 段；数量极限/移速/升阶等仍为全局
        i("swarm.swarmCountLimit", GROUP_MECHANICS, () -> Config.SWARM_COUNT_LIMIT.get(), v -> Config.SWARM_COUNT_LIMIT.set((int) Math.round(v)), 0, 100);
        d("swarm.swarmMoveSpeed", GROUP_MECHANICS, () -> Config.SWARM_MOVE_SPEED.get(), v -> Config.SWARM_MOVE_SPEED.set(v), 0.01, 10.0);
        d("swarm.swarmTierUpgradeChanceStandard", GROUP_MECHANICS, () -> Config.SWARM_TIER_UPGRADE_CHANCE_STANDARD.get(), v -> Config.SWARM_TIER_UPGRADE_CHANCE_STANDARD.set(v), 0.0, 1.0);
        d("swarm.swarmTierUpgradeChanceAdvanced", GROUP_MECHANICS, () -> Config.SWARM_TIER_UPGRADE_CHANCE_ADVANCED.get(), v -> Config.SWARM_TIER_UPGRADE_CHANCE_ADVANCED.set(v), 0.0, 1.0);
        d("swarm.swarmVulnerabilityValue", GROUP_MECHANICS, () -> Config.SWARM_VULNERABILITY_VALUE.get(), v -> Config.SWARM_VULNERABILITY_VALUE.set(v), 0.0, 10.0);
        d("swarm.swarmShieldRepairMultiplier", GROUP_MECHANICS, () -> Config.SWARM_SHIELD_REPAIR_MULTIPLIER.get(), v -> Config.SWARM_SHIELD_REPAIR_MULTIPLIER.set(v), 0.0, 100.0);
        // 高级工程学 / 精密构造：数值已迁移到 values 段，无全局配置项
        // 敌对目标标记
        i("hostile_target.markDuration", GROUP_MECHANICS, () -> Config.HOSTILE_TARGET_MARK_DURATION.get(), v -> Config.HOSTILE_TARGET_MARK_DURATION.set((int) Math.round(v)), 1, 10000);
        // 格挡无敌帧
        i("blockInvulnerableTicks", GROUP_MECHANICS, () -> Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get(), v -> Config.SHIELD_BLOCK_INVULNERABLE_TICKS.set((int) Math.round(v)), 0, 100);

        // 点燃系统
        d("ignite_system.igniteDefaultDamage", GROUP_MECHANICS, () -> Config.IGNITE_DEFAULT_DAMAGE.get(), v -> Config.IGNITE_DEFAULT_DAMAGE.set(v), 0.0, 100.0);
        i("ignite_system.igniteDefaultDuration", GROUP_MECHANICS, () -> Config.IGNITE_DEFAULT_DURATION.get(), v -> Config.IGNITE_DEFAULT_DURATION.set((int) Math.round(v)), 1, 600);

        // 次级伤害合并
        b("secondary_damage_merge.secondaryDamageMergeEnabled", GROUP_MECHANICS, () -> Config.SECONDARY_DAMAGE_MERGE_ENABLED.get() ? 1.0 : 0.0, v -> Config.SECONDARY_DAMAGE_MERGE_ENABLED.set(v != 0.0));
        i("secondary_damage_merge.secondaryDamageMergeWindowTicks", GROUP_MECHANICS, () -> Config.SECONDARY_DAMAGE_MERGE_WINDOW_TICKS.get(), v -> Config.SECONDARY_DAMAGE_MERGE_WINDOW_TICKS.set((int) Math.round(v)), 1, 100);

        // 弹射物爆炸：数值已迁移到 values 段，无全局配置项

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
        ce("shield_particle_rendering.style", GROUP_HUD, () -> (double) ClientConfig.SHIELD_PARTICLE_STYLE.get(), v -> ClientConfig.SHIELD_PARTICLE_STYLE.set((int) Math.round(v)), 3);

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
