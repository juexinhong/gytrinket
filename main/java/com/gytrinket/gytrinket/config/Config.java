package com.gytrinket.gytrinket.config;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.attribute.AttributeType;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * 模组配置类
 * 负责管理模组的配置项和属性注册
 */
@EventBusSubscriber(modid = gytrinket.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== 1. 护盾基础属性 (attributes) =====

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_ATTRIBUTES_CONFIG;

    // ===== 合成禁用 (crafting_disable) =====
    /** 合成禁用模式：0=不禁用，1=禁用本模组命名空间下注册了实际效果的物品合成，2=禁用所有注册了实际效果的物品合成 */
    public static final ModConfigSpec.IntValue DISABLE_CRAFTING_MODE;

    // ===== 33.5 随机构建系统 (random_build) =====
    public static final ModConfigSpec.BooleanValue RANDOM_BUILD_ENABLED;
    public static final ModConfigSpec.BooleanValue SHOW_UPGRADE_REMINDER_HUD;
    /** 从随机池获取物品时的升级点消耗倍数（升级点惩罚，默认 5 倍） */
    public static final ModConfigSpec.IntValue RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER;
    /** 代币机制：启用后随机池兑换消耗背包代币而非升级点 */
    public static final ModConfigSpec.BooleanValue RANDOM_BUILD_TOKEN_ENABLED;
    /** 代币物品 ID（可替换为其他模组的物品） */
    public static final ModConfigSpec.ConfigValue<String> RANDOM_BUILD_TOKEN_ITEM;

    // ===== 1. 光环护盾 (aura_shield) =====
    public static final ModConfigSpec.DoubleValue AURA_RADIUS;
    public static final ModConfigSpec.DoubleValue AURA_DAMAGE;
    public static final ModConfigSpec.IntValue AURA_TRIGGER_FREQUENCY;
    public static final ModConfigSpec.DoubleValue AURA_SHIELD_COST;

    // ===== 2. 虹吸护盾 (siphon_shield) =====
    public static final ModConfigSpec.DoubleValue SIPHON_RADIUS;
    public static final ModConfigSpec.DoubleValue SIPHON_DAMAGE;
    public static final ModConfigSpec.IntValue SIPHON_TICK_INTERVAL;
    public static final ModConfigSpec.DoubleValue SIPHON_HEAL_RATIO;
    public static final ModConfigSpec.IntValue SIPHON_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue SIPHON_EFFECT_PER_STACK;
    public static final ModConfigSpec.DoubleValue SIPHON_MAX_EFFECT;
    public static final ModConfigSpec.DoubleValue SIPHON_DECAY_RATIO;

    // ===== 3. 反射护盾类型 (reflect_shield) =====
    public static final ModConfigSpec.DoubleValue REFLECT_RADIUS;
    public static final ModConfigSpec.DoubleValue REFLECT_SPEED_BASE_MODIFIER;
    public static final ModConfigSpec.DoubleValue REFLECT_SPEED_EXTRA_MODIFIER;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_EFFECT_MULTIPLIER;

    // ===== 4. 增幅护盾 (amplification_shield) =====

    public static final ModConfigSpec.DoubleValue AMPLIFICATION_BASE_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_THREAT_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_CHECK_RADIUS;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_MAX_AMPLIFICATION;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_MOVEMENT_SPEED_BONUS;
    public static final ModConfigSpec.DoubleValue AMPLIFICATION_HEALTH_AMPLIFICATION_PER_POINT;

    // ===== 5. 跃传护盾 (warp_shield) =====
    public static final ModConfigSpec.IntValue WARP_SHIELD_INVINCIBLE_DURATION;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue WARP_SHIELD_WARP_DISTANCE;

    // ===== 6. 屏障 (barrier) =====

    public static final ModConfigSpec.DoubleValue BARRIER_MAX_DAMAGE;

    // ===== 7. 反射护盾 (reflect_damage) =====

    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_RAY_LENGTH;

    // ===== 8. 易爆护盾 (explosive_shield) =====

    public static final ModConfigSpec.DoubleValue EXPLOSIVE_SHIELD_DAMAGE;
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_SHIELD_RADIUS;

    // ===== 9. 电能释放 (electric_discharge) =====

    public static final ModConfigSpec.DoubleValue ELECTRIC_DISCHARGE_BURN_CHARGE;
    public static final ModConfigSpec.IntValue ELECTRIC_DISCHARGE_BURN_DURATION;

    // ===== 10. 武器化护盾 (weaponized_shield) =====

    public static final ModConfigSpec.DoubleValue WEAPONIZED_SHIELD_VULNERABILITY;
    public static final ModConfigSpec.DoubleValue WEAPONIZED_SHIELD_RADIUS;

    // ===== 11. 镀层 (coating_system) =====

    public static final ModConfigSpec.DoubleValue COATING_REDUCTION_PER_LAYER;

    // ===== 12. 适应性装甲 (adaptive_armor) =====

    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_DURATION;
    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT;
    public static final ModConfigSpec.DoubleValue ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE;

    // ===== 13. 再生护盾 (shield_natural_recovery) =====

    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER;

    // ===== 14. 效率 (attack_cooldown_efficiency) =====

    // ===== 15. 转化 (conversion) =====

    public static final ModConfigSpec.DoubleValue CONVERSION_RATIO;

    // ===== 16. 二原协议 (binary_protocol) =====

    // ===== 17. 强袭 (assault) =====

    public static final ModConfigSpec.DoubleValue ASSAULT_ATTACK_SPEED_PER_STACK;
    public static final ModConfigSpec.IntValue ASSAULT_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue ASSAULT_SELF_DAMAGE_PER_STACK;
    public static final ModConfigSpec.DoubleValue ASSAULT_MOVEMENT_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue ASSAULT_OVERFLOW_DAMAGE_EFFICIENCY;

    // ===== 18. 充能攻击 (charged_attack) =====

    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_BASE_CHARGE_RATE;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_SPEED_SCALE_FACTOR;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR;
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY;
    /** 充能物品白名单（长按右键充能）：物品注册名=攻击速度修正值 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHARGED_ATTACK_ITEM_USE_WHITELIST;
    /** 充能物品白名单未注册物品的默认攻击速度修正值 */
    public static final ModConfigSpec.DoubleValue CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER;

    // ===== 18.5 弹射物黑名单 (projectile_blacklist) =====
    /** 弹射物黑名单：不参与充能攻击增幅与点射复制的实体类型注册名 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_BLACKLIST;

    // ===== 17.5 征途 (journey) =====

    public static final ModConfigSpec.DoubleValue JOURNEY_ATTACK_SPEED_PER_STACK;
    public static final ModConfigSpec.DoubleValue JOURNEY_MOVEMENT_SPEED_PER_STACK;
    public static final ModConfigSpec.IntValue JOURNEY_DURATION_TICKS;
    public static final ModConfigSpec.IntValue JOURNEY_MAX_STACKS;
    public static final ModConfigSpec.IntValue JOURNEY_DECAY_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue JOURNEY_DECAY_PER_INTERVAL;

    // ===== 19. 精密构造 (precision_construct) =====

    public static final ModConfigSpec.DoubleValue PRECISION_CONSTRUCT_BONUS_PER_LEVEL;

    // ===== 20. 护盾移植 (shield_transfer) =====

    public static final ModConfigSpec.DoubleValue SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY;

    // ===== 21. 追击阵列/突击无人机 (pursuit_array) =====



    public static final ModConfigSpec.DoubleValue DRONE_BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue DRONE_BASE_DAMAGE;
    public static final ModConfigSpec.IntValue DRONE_MAX_COUNT;
    public static final ModConfigSpec.DoubleValue DRONE_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue ORBIT_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue ORBIT_ATTACK_RANGE;
    public static final ModConfigSpec.DoubleValue PURSUIT_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue PURSUIT_ATTACK_RANGE;

    // ===== 21.5 无人机斩杀机制 (drone_execute) =====
    public static final ModConfigSpec.BooleanValue DRONE_EXECUTE_ENABLED;

    // ===== 21.6 僚机 (wingman) =====

    public static final ModConfigSpec.DoubleValue WINGMAN_BASE_HEALTH;
    public static final ModConfigSpec.IntValue WINGMAN_MAX_COUNT;
    public static final ModConfigSpec.DoubleValue WINGMAN_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue WINGMAN_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue WINGMAN_EXPLOSIVE_COUNT;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSIVE_DAMAGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_RADIUS;

    // ===== 拦截机/进化/纳米再生/震撼弹模块 =====

    public static final ModConfigSpec.IntValue WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE;

    public static final ModConfigSpec.DoubleValue WINGMAN_EVOLUTION_BONUS_PER_LEVEL;

    public static final ModConfigSpec.DoubleValue WINGMAN_NANO_REGEN_PERCENT;

    public static final ModConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER;

    // ===== 21.7 蜂群 (swarm) =====

    public static final ModConfigSpec.DoubleValue SWARM_BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue SWARM_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SWARM_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue SWARM_ATTACK_RANGE;
    public static final ModConfigSpec.DoubleValue SWARM_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue SWARM_MAX_COUNT;
    public static final ModConfigSpec.IntValue SWARM_COUNT_LIMIT;
    public static final ModConfigSpec.DoubleValue SWARM_MOVE_SPEED;
    public static final ModConfigSpec.IntValue SWARM_BUILD_TIME;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_STANDARD;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_ADVANCED;
    public static final ModConfigSpec.DoubleValue SWARM_VULNERABILITY_VALUE;
    public static final ModConfigSpec.DoubleValue SWARM_SHIELD_REPAIR_MULTIPLIER;

    // ===== 22. 宽限协议 (near_death_protection) =====

    public static final ModConfigSpec.IntValue NEAR_DEATH_PROTECTION_COOLDOWN;
    public static final ModConfigSpec.IntValue NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION;

    // ===== 23. 高等工程 (advanced_engineering) =====

    public static final ModConfigSpec.DoubleValue ADVANCED_ENGINEERING_BONUS_PER_LEVEL;

    // ===== 24. 最后指令 (near_death_explosion) =====

    public static final ModConfigSpec.IntValue NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_INITIAL_SPEED;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_TURN_SPEED_BASE;
    public static final ModConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_TURN_SPEED_GROWTH_PER_SECOND;

    // ===== 24.5 自毁装置 (self_destruct) =====

    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH;

    // ===== 24.5.2 次级攻击伤害合并 (secondary_damage_merge) =====

    public static final ModConfigSpec.BooleanValue SECONDARY_DAMAGE_MERGE_ENABLED;
    public static final ModConfigSpec.IntValue SECONDARY_DAMAGE_MERGE_WINDOW_TICKS;

    /** 次级爆炸（爆炸半径模块特殊机制）：爆炸伤害占弹射物伤害的比例 */
    public static final ModConfigSpec.DoubleValue SECONDARY_EXPLOSION_DAMAGE_FRACTION;
    /** 次级爆炸（爆炸半径模块特殊机制）：爆炸半径基础值 */
    public static final ModConfigSpec.DoubleValue SECONDARY_EXPLOSION_RADIUS_BASE;
    /** 次级爆炸（爆炸半径模块特殊机制）：每点爆炸伤害增加的爆炸半径 */
    public static final ModConfigSpec.DoubleValue SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION;

    // ===== 24.5.1 炉心融解模块 (furnace_core) =====

    // ===== 24.6 督战者 (taskmaster) =====

    // ===== 25. 列队阵列 (formation_array) =====

    public static final ModConfigSpec.DoubleValue FORMATION_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue FORMATION_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue FORMATION_ATTACK_PASS_DELAY;

    // ===== 26. 指挥官 (commander) =====

    public static final ModConfigSpec.IntValue COMMANDER_MAX_COUNT;
    public static final ModConfigSpec.IntValue COMMANDER_APPOINT_DELAY;
    public static final ModConfigSpec.DoubleValue COMMANDER_VULNERABILITY;

    // ===== 27. 守卫阵列/防御无人机 (guard_array) =====


    public static final ModConfigSpec.DoubleValue GUARD_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue GUARD_ATTACK_RANGE;

    // ===== 28. 弧形屏障 (arc_barrier) =====

    public static final ModConfigSpec.DoubleValue ARC_BARRIER_POSITION_DEVIATION_THRESHOLD;

    // ===== 29. 反制脉冲 (counter_pulse) =====

    public static final ModConfigSpec.IntValue COUNTER_PULSE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue COUNTER_PULSE_BASE_EXPLOSION_RADIUS;
    public static final ModConfigSpec.DoubleValue COUNTER_PULSE_BASE_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_CHARGE_INTERVAL;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_MAX_CHARGE_LEVEL;

    // ===== 30. 重塑 (reshaping) =====

    public static final ModConfigSpec.DoubleValue RESHAPING_HEAL_RATE;
    public static final ModConfigSpec.DoubleValue RESHAPING_BASE_DAMAGE_REDUCTION;
    public static final ModConfigSpec.IntValue RESHAPING_DAMAGE_REDUCTION_DURATION;

    // ===== 31. 充能护盾 (charged_shield) =====

    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_CHARGE_RATIO;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_MAX_BONUS;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY;

    // ===== 35. 幽灵机身 (ghost_fuselage) =====

    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_STEALTH_SPEED_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_MAX_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_BASE_MAX_DAMAGE_BONUS;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_MOVE_SPEED_THRESHOLD;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_MOVE_SPEED_REDUCTION;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue GHOST_FUSELAGE_MIN_DECAY;
    public static final ModConfigSpec.IntValue GHOST_FUSELAGE_FULL_STEALTH_TICKS;

    // ===== 36. 积怨 (grudge) =====

    public static final ModConfigSpec.DoubleValue GRUDGE_CONVERSION_RATIO;
    public static final ModConfigSpec.DoubleValue GRUDGE_FADE_BASE;
    public static final ModConfigSpec.DoubleValue GRUDGE_FADE_PERCENT;
    public static final ModConfigSpec.DoubleValue GRUDGE_MOVEMENT_SPEED_PENALTY;

    // ===== 32. 升级系统 (upgrade_system) =====
    public static final ModConfigSpec.ConfigValue<Boolean> UPGRADE_SYSTEM_ENABLED;

    // ===== 33. 快速装备 (quick_equip) =====
    public static final ModConfigSpec.IntValue QUICK_EQUIP_UPGRADE_POINTS_COST;

    // ===== 34. 其他通用设置 =====
    public static final ModConfigSpec.ConfigValue<Boolean> HARDCORE_MODE_ENABLED;
    public static final ModConfigSpec.IntValue SHIELD_BLOCK_INVULNERABLE_TICKS;
    public static final ModConfigSpec.ConfigValue<String> SHIELD_HIT_SOUND;
    public static final ModConfigSpec.DoubleValue IGNITE_DEFAULT_DAMAGE;
    public static final ModConfigSpec.IntValue IGNITE_DEFAULT_DURATION;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_PLAYER_HEALTH;
    public static final ModConfigSpec.ConfigValue<Boolean> NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD;
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY;
    public static final ModConfigSpec.IntValue HOSTILE_TARGET_MARK_DURATION;

    public static final ModConfigSpec SPEC;

    private static boolean initialized = false;

    static {
        // ===== 1. 护盾基础属性 =====
        BUILDER.comment("属性系统配置").push("attributes");

        ITEM_ATTRIBUTES_CONFIG = BUILDER.comment(
            "物品属性配置",
            "格式：物品ID|属性名=数值|属性名=数值",
            "使用 | 分隔物品ID和属性，使用 = 分隔属性名和值",
            "每个物品单独占一行",
            "示例：minecraft:diamond|shield_base=10.0|shield_percent=0.1"
        ).defineListAllowEmpty("itemAttributes",
            List.of(
                "gytrinket:shield_gy|shield_base=6.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy1|shield_base=7.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy2|shield_base=8.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_gy3|shield_base=9.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_aura_ring|shield_base=8.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring1|shield_base=12.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring2|shield_base=16.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_aura_ring3|shield_base=20.0|shield_cooldown_time=6.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_siphon|shield_base=8.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.3",
                "gytrinket:shield_siphon1|shield_base=12.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.2",
                "gytrinket:shield_siphon2|shield_base=16.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=-0.1",
                "gytrinket:shield_siphon3|shield_base=20.0|shield_cooldown_time=7|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1|shield_effect_percent=0.0",

                "gytrinket:shield_reflect|shield_base=9.6|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect1|shield_base=14.4|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect2|shield_base=19.2|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_reflect3|shield_base=24.0|shield_cooldown_time=7.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_amplifier|shield_base=4.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier1|shield_base=6.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier2|shield_base=8.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_amplifier3|shield_base=10.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_warp|shield_base=18.0|shield_cooldown_time=10.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp1|shield_base=18.0|shield_cooldown_time=8.5|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp2|shield_base=18.0|shield_cooldown_time=7.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",
                "gytrinket:shield_warp3|shield_base=18.0|shield_cooldown_time=6.0|shield_hit_cooldown_extend=40|shield_hit_cooldown_extend_multiplier=0.1",

                "gytrinket:shield_amplifier_module|shield_percent=0.2",
                "gytrinket:barrier_shield_module|shield_percent=0.05|shield_cooldown_reduction_percent=-0.05",
                "gytrinket:reflect_shield_module|shield_percent=0.05",
                "gytrinket:ultimate_shield_module|shield_base=11.0|shield_damage_reduction=-0.15|shield_effect_percent=0.15|shield_hit_cooldown_extend_final_multiplier=-0.65|player_health_independent=-0.85",

                "gytrinket:shield_cooldown_reduction_module|shield_cooldown_reduction_percent=0.2",
                "gytrinket:shield_quick_charge_module|shield_cooldown_reduction_percent=0.25|shield_independent=-0.25",
                "gytrinket:explosive_shield_module|shield_percent=0.05",

                "gytrinket:shield_effect_boost_module|shield_effect_percent=0.1|shield_effect_radius=0.25",
                "gytrinket:divergent_shield_module|shield_effect_radius=0.5|shield_percent=-0.1|shield_cooldown_reduction_independent=-0.1",
                "gytrinket:focused_shield_module|shield_effect_percent=0.25|shield_percent=0.05|shield_effect_radius=-0.2",

                "gytrinket:health_boost_module|player_health_percent=0.2",
                "gytrinket:coating_module|coating=3",
                "gytrinket:colossus_module|player_health_percent=0.25|player_knockback_percent=0.4|knockback_resistance=0.2|movement_speed_independent=-0.25",

                "gytrinket:bond_module|adaptive_armor_duration=0.2",
                "gytrinket:core_armor_module|shield_base=1.0|player_health=1.0|shield_self_damage_reduction=-0.2|player_self_damage_reduction=-0.2|adaptive_armor_duration=0.2",

                "gytrinket:regen_module|recovery_efficiency_percent=0.4",
                "gytrinket:regen_shield_module|recovery_efficiency_percent=0.1",

                "gytrinket:transformation_module|shield_base=1.0|player_health=1.0",

                "gytrinket:fast_shooting_module|attack_speed_percent=0.15",
                "gytrinket:burst_fire_module|attack_speed_percent=0.2|combo=2",

                "gytrinket:explosion_radius_module|explosion_radius_independent=0.1",
                "gytrinket:high_explosive_module|explosion_radius_independent=0.2",
                "gytrinket:implosion_module|explosion_damage_percent=0.35|explosion_radius_independent=-0.3",
                "gytrinket:giant_star_module|attack_damage_percent=0.15|weapon_projectile_size_percent=0.2|explosion_radius_independent=0.05",
                "gytrinket:overcharge_module|attack_damage_percent=0.3|weapon_projectile_size_percent=0.12|explosion_radius_independent=0.05|attack_speed_percent=-0.12",

                "gytrinket:thrust_boost_module|movement_speed_percent=0.25",
                "gytrinket:aerodynamic_framework_module|movement_speed_percent=0.25|player_health_independent=-0.1|player_knockback_percent=-0.2|knockback_resistance=-0.2",

                "gytrinket:precision_construct_module|construct_health_percent=0.25|construct_build_speed_percent=0.10",
                "gytrinket:shield_transfer_module|shield_damage_reduction=-0.5|player_damage_reduction=-0.1|player_health=2|player_health_percent=0.1",

                "gytrinket:drone_module|",
                "gytrinket:advanced_engineering_module|construct_drone_count_base=1",

                "gytrinket:assault_drone_module|construct_drone_assault_attack_speed_percent=0.2|construct_drone_count_base=1",
                "gytrinket:wing_commander_module|construct_drone_count_base=1|construct_commander_health_percent=2.0|construct_commander_damage_percent=2.0",

                "gytrinket:defense_drone_module|construct_drone_defense_health_percent=1.5",

                "gytrinket:interceptor_module|construct_wingman_explosive_count_base=1",
                "gytrinket:evolution_module|construct_wingman_explosive_count_base=1",
                "gytrinket:suppression_module|construct_wingman_weapon_attack_speed_percent=0.5|construct_wingman_explosive_count_base=2",

                "gytrinket:self_destruct_module|",
                "gytrinket:taskmaster_module|construct_standard_non_weapon_count_percent=1|construct_basic_non_weapon_count_percent=1|construct_advanced_count_base=1",

                "gytrinket:guardian|shield_effect_percent=0.10|shield_effect_radius=0.25|shield_damage_reduction=-0.1|attack_speed_percent=-0.1",

                "gytrinket:mothership_body|shield_percent=0.15|player_health_percent=0.15|movement_speed_percent=0.4|knockback_resistance=0.2",

                "gytrinket:engineering_fuselage|construct_standard_count_base=2|construct_advanced_count_base=1|shield_percent=-0.10|player_health=-2",

                "gytrinket:grudge_module|player_health_percent=0.05|attack_damage_percent=0.05|movement_speed_independent=-0.1",

                "gytrinket:apex_apparatus_module|construct_attack_speed_percent=0.30|construct_health_percent=0.30|shield_effect_percent=0.20|construct_build_speed_independent=-0.75",

                "gytrinket:furnace_core_module|construct_non_shield_build_speed_percent=0.30|construct_attack_speed_percent=0.30|construct_move_speed_percent=0.30|construct_orbit_speed_percent=0.30|construct_rotation_speed_percent=0.30",

                "gytrinket:quick_reconstruction_module|recovery_efficiency_percent=1.0|player_health=10|coating=2"
            ),
            s -> true
        );

        BUILDER.pop();

        // ===== 合成禁用 (crafting_disable) =====
        BUILDER.comment("合成禁用模式：0=不禁用合成，1=禁用本模组命名空间下注册了本模组实际效果（属性或特殊机制）的物品的合成，2=禁用所有注册了本模组实际效果的物品的合成")
            .push("crafting_disable");
        DISABLE_CRAFTING_MODE = BUILDER.comment("0=不禁用，1=仅本模组物品，2=全部注册物品")
            .defineInRange("disableCraftingMode", 1, 0, 2);
        BUILDER.pop();

        // ===== 33.5 随机构建系统 =====
        BUILDER.comment("随机构建系统配置").push("random_build");

        RANDOM_BUILD_ENABLED = BUILDER.comment(
            "是否启用随机构建系统",
            "启用后：玩家面板经验条上方出现3x3随机池，",
            "可用升级点（× 配置的消耗倍数）兑换随机物品装备到光点核心。"
        ).define("enabled", true);

        RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER = BUILDER.comment(
            "从随机池获取物品时的升级点消耗倍数（升级点惩罚）",
            "从随机池获取 1 件物品消耗的升级点 = 基础 1 点 × 该倍数",
            "默认 8（即每次获取消耗 8 点升级点）；范围 1~100",
            "代币机制启用时代币消耗不受该倍数影响（每次仍消耗 1 个代币）"
        ).defineInRange("upgradePointsMultiplier", 8, 1, 100);

        SHOW_UPGRADE_REMINDER_HUD = BUILDER.comment(
            "是否显示升级提醒 HUD",
            "当玩家有未使用的升级点时，在物品栏上方显示按下G键的提示",
            "光点核心已满时不显示，默认 true"
        ).define("showUpgradeReminderHud", true);

        RANDOM_BUILD_TOKEN_ENABLED = BUILDER.comment(
            "是否启用代币机制（归属随机构建）",
            "启用后：从随机池获取物品时改为消耗玩家背包中的代币（每次 1 个），",
            "升级点消耗倍数惩罚在升级点模式下仍生效，不会因代币启用而取消。"
        ).define("tokenEnabled", false);

        RANDOM_BUILD_TOKEN_ITEM = BUILDER.comment(
            "代币物品 ID（可替换为其他模组的物品）",
            "从随机池获取物品时，会从玩家背包中扣除该物品 1 个",
            "默认 gytrinket:token（本模组代币物品）"
        ).define("tokenItem", "gytrinket:token");

        BUILDER.pop();

        // ===== 1. 光环护盾 =====
        BUILDER.comment("光环护盾配置").push("aura_shield");

        AURA_RADIUS = BUILDER.comment("光环护盾半径").defineInRange("auraRadius", 3.5, 0.0, 100.0);
        AURA_DAMAGE = BUILDER.comment("光环护盾伤害").defineInRange("auraDamage", 0.75, 0.0, 100.0);
        AURA_TRIGGER_FREQUENCY = BUILDER.comment("光环护盾触发频率（刻）").defineInRange("auraTriggerFrequency", 5, 1, 200);
        AURA_SHIELD_COST = BUILDER.comment("光环护盾消耗护盾值").defineInRange("auraShieldCost", 0.042, 0.0, 10.0);

        BUILDER.pop();

        // ===== 2. 虹吸护盾 =====
        BUILDER.comment("虹吸护盾配置").push("siphon_shield");

        SIPHON_RADIUS = BUILDER.comment("虹吸护盾基础半径").defineInRange("siphonRadius", 4.0, 0.0, 100.0);
        SIPHON_DAMAGE = BUILDER.comment("虹吸护盾基础伤害量").defineInRange("siphonDamage", 0.3, 0.0, 100.0);
        SIPHON_TICK_INTERVAL = BUILDER.comment("虹吸护盾伤害频率（刻）").defineInRange("siphonTickInterval", 5, 1, 200);
        SIPHON_HEAL_RATIO = BUILDER.comment("虹吸护盾伤害恢复护盾比例").defineInRange("siphonHealRatio", 0.3, 0.0, 1.0);
        SIPHON_DURATION_TICKS = BUILDER.comment("虹吸效果持续时间（刻）").defineInRange("siphonDurationTicks", 20, 1, 200);
        SIPHON_EFFECT_PER_STACK = BUILDER.comment("每层虹吸效果提供的百分比加成").defineInRange("siphonEffectPerStack", 0.025, 0.001, 1.0);
        SIPHON_MAX_EFFECT = BUILDER.comment("虹吸效果最大百分比加成").defineInRange("siphonMaxEffect", 0.4, 0.01, 1.0);
        SIPHON_DECAY_RATIO = BUILDER.comment("虹吸效果消退比率").defineInRange("siphonDecayRatio", 0.03, 0.0, 1.0);

        BUILDER.pop();

        // ===== 3. 反射护盾类型 =====
        BUILDER.comment("反射护盾配置").push("reflect_shield");

        REFLECT_RADIUS = BUILDER.comment("反射护盾半径").defineInRange("reflectRadius", 40.0, 0.0, 100.0);
        REFLECT_SPEED_BASE_MODIFIER = BUILDER.comment("反射弹射物速度基础系数").defineInRange("reflectSpeedBaseModifier", 1.5, 0.0, 10.0);
        REFLECT_SPEED_EXTRA_MODIFIER = BUILDER.comment("反射弹射物速度额外系数（决定护盾效果半径属性能够生效多少.1就是100%）").defineInRange("reflectSpeedExtraModifier", 1.0, 0.0, 10.0);
        REFLECT_DAMAGE_EFFECT_MULTIPLIER = BUILDER.comment("反射弹射物伤害护盾效果系数（乘以护盾效果属性）").defineInRange("reflectDamageEffectMultiplier", 1.0, 0.0, 10.0);

        BUILDER.pop();

        // ===== 4. 增幅护盾 =====
        BUILDER.comment("增幅护盾系统配置").push("amplification_shield");

        AMPLIFICATION_BASE_AMPLIFICATION = BUILDER.comment(
            "增幅护盾基础增幅值",
            "当玩家有护盾值时提供的基础攻击伤害加成（独立乘区）",
            "例如：0.2 表示增加20%"
        ).defineInRange("amplificationBaseAmplification", 0.2, 0.0, 2.0);

        AMPLIFICATION_THREAT_AMPLIFICATION = BUILDER.comment(
            "增幅护盾威胁增幅值",
            "每个敌人或危险物提供的固定攻击伤害加成（独立乘区）",
            "例如：0.2 表示每个威胁增加20%",
            "范围：0.0 ~ 1.0"
        ).defineInRange("amplificationThreatAmplification", 0.2, 0.0, 1.0);

        AMPLIFICATION_HEALTH_AMPLIFICATION_PER_POINT = BUILDER.comment(
            "增幅护盾敌人最大生命增幅值",
            "敌人最大生命每点提供的攻击伤害加成（独立乘区）",
            "例如：0.01 表示每点最大生命增加1%，僵尸(20生命)提供20%增幅",
            "该增幅计入每个敌人的贡献，总量不能超出最大增幅限制",
            "范围：0.0 ~ 0.1"
        ).defineInRange("amplificationHealthAmplificationPerPoint", 0.02, 0.0, 0.1);

        AMPLIFICATION_CHECK_RADIUS = BUILDER.comment(
            "增幅护盾威胁检测半径（格）",
            "检测玩家周围危险目标的基础半径",
            "该值会受护盾效果半径属性影响"
        ).defineInRange("amplificationCheckRadius", 5.0, 1.0, 20.0);

        AMPLIFICATION_MAX_AMPLIFICATION = BUILDER.comment(
            "增幅护盾最大增幅值",
            "攻击伤害加成的上限（独立乘区）",
            "例如：1.0 表示最大增加100%"
        ).defineInRange("amplificationMaxAmplification", 1.0, 0.0, 3.0);

        AMPLIFICATION_MOVEMENT_SPEED_BONUS = BUILDER.comment(
            "增幅护盾移动速度加成",
            "当有护盾值时，为玩家或被护盾保护的实体提供的移动速度独立乘区加成",
            "该值受护盾效果属性影响，但不受危险物数量提升机制影响",
            "例如：0.2 表示增加20%"
        ).defineInRange("amplificationMovementSpeedBonus", 0.2, 0.0, 2.0);

        BUILDER.pop();

        // ===== 5. 跃传护盾 =====
        BUILDER.comment("跃传护盾系统配置").push("warp_shield");

        WARP_SHIELD_INVINCIBLE_DURATION = BUILDER.comment(
            "跃传护盾玩家无敌时间（刻）",
            "护盾破裂后玩家进入无敌状态的持续时间",
            "例如：15 表示持续15刻（0.75秒）"
        ).defineInRange("warpShieldInvincibleDuration", 15, 1, 100);

        WARP_SHIELD_EXPLOSION_DAMAGE = BUILDER.comment(
            "跃传护盾爆炸基础伤害",
            "护盾破裂时产生的爆炸基础伤害值",
            "该值会受护盾效果属性组影响"
        ).defineInRange("warpShieldExplosionDamage", 7.5, 0.0, 100.0);

        WARP_SHIELD_EXPLOSION_RADIUS = BUILDER.comment(
            "跃传护盾爆炸半径（格）",
            "护盾破裂时爆炸的基础半径",
            "该值会受护盾效果半径属性组影响"
        ).defineInRange("warpShieldExplosionRadius", 3.3, 1.0, 20.0);

        WARP_SHIELD_WARP_DISTANCE = BUILDER.comment(
            "跃传护盾传送距离（格）",
            "护盾破裂时玩家/被保护实体被传送的基础距离",
            "该值会受护盾效果半径属性组影响"
        ).defineInRange("warpShieldWarpDistance", 5.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 6. 屏障 =====
        BUILDER.comment("屏障系统配置").push("barrier");

        BARRIER_MAX_DAMAGE = BUILDER.comment(
            "屏障限制伤害最大值",
            "当伤害超过此值时，将被限制为此值",
            "示例：5.0"
        ).defineInRange("barrierMaxDamage", 5.0, 0.0, 1000.0);

        BUILDER.pop();

        // ===== 7. 反射护盾 =====
        BUILDER.comment("反射护盾伤害处理器配置").push("reflect_damage");

        REFLECT_DAMAGE_BASE_DAMAGE = BUILDER.comment(
            "反射护盾基础伤害值",
            "该伤害会受护盾效果属性影响",
            "示例：1.0"
        ).defineInRange("reflectDamageBaseDamage", 0.7, 0.0, 10.0);

        REFLECT_DAMAGE_RAY_LENGTH = BUILDER.comment(
            "反射护盾射线基础长度（格）",
            "该长度会受护盾效果半径属性影响",
            "示例：5.0"
        ).defineInRange("reflectDamageRayLength", 2.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 8. 易爆护盾 =====
        BUILDER.comment("易爆护盾系统配置").push("explosive_shield");

        EXPLOSIVE_SHIELD_DAMAGE = BUILDER.comment(
            "易爆护盾默认伤害值",
            "该伤害会受护盾效果属性影响",
            "示例：10.0"
        ).defineInRange("explosiveShieldDamage", 10.0, 0.0, 100.0);

        EXPLOSIVE_SHIELD_RADIUS = BUILDER.comment(
            "易爆护盾默认半径（格）",
            "该半径会受护盾效果半径属性影响",
            "示例：5.0"
        ).defineInRange("explosiveShieldRadius", 4.0, 0.0, 10.0);

        BUILDER.pop();

        // ===== 9. 电能释放 =====
        BUILDER.comment("电能释放系统配置").push("electric_discharge");

        ELECTRIC_DISCHARGE_BURN_CHARGE = BUILDER.comment(
            "电能释放基础灼烧充能量",
            "每次闪电命中目标时施加的灼烧充能量",
            "该值会受到玩家攻击速度的影响：攻击速度越快，灼烧充能越少",
            "范围：0.1 ~ 10.0"
        ).defineInRange("electricDischargeBurnCharge", 0.5, 0.1, 10.0);

        ELECTRIC_DISCHARGE_BURN_DURATION = BUILDER.comment(
            "电能释放灼烧持续时间（刻）",
            "每次闪电命中后灼烧效果的持续时间",
            "范围：1 ~ 200"
        ).defineInRange("electricDischargeBurnDuration", 10, 1, 200);

        BUILDER.pop();

        // ===== 10. 武器化护盾 =====
        BUILDER.comment("武器化护盾系统配置").push("weaponized_shield");

        WEAPONIZED_SHIELD_VULNERABILITY = BUILDER.comment(
            "武器化护盾基础易伤值",
            "该值会受到护盾效果属性组影响",
            "范围：0.0 ~ 1.0",
            "示例：0.20"
        ).defineInRange("weaponizedShieldVulnerability", 0.20, 0.0, 10.0);

        WEAPONIZED_SHIELD_RADIUS = BUILDER.comment(
            "武器化护盾基础作用半径（格）",
            "该值会受到护盾效果半径属性组影响",
            "示例：4.0"
        ).defineInRange("weaponizedShieldRadius", 4.0, 1.0, 20.0);

        BUILDER.pop();

        // ===== 11. 镀层 =====
        BUILDER.comment("镀层系统配置").push("coating_system");

        COATING_REDUCTION_PER_LAYER = BUILDER.comment("每层镀层减少的伤害量").defineInRange("coatingReductionPerLayer", 0.2, 0.0, 10.0);

        BUILDER.pop();

        // ===== 12. 适应性装甲 =====
        BUILDER.comment("适应性装甲系统配置").push("adaptive_armor");

        ADAPTIVE_ARMOR_DURATION = BUILDER.comment(
            "适应性装甲叠层持续时间（刻）",
            "每批叠层单独计时"
        ).defineInRange("adaptiveArmorDuration", 50, 1, 6000);

        ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT = BUILDER.comment(
            "单次受到攻击最多添加的装甲叠层数"
        ).defineInRange("adaptiveArmorMaxLayersPerHit", 1000, 1, 10000);

        ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE = BUILDER.comment(
            "每点伤害转化为多少装甲叠层",
            "例如：设为2.0时，受到5点伤害会添加10层装甲叠层"
        ).defineInRange("adaptiveArmorLayersPerDamage", 2.0, 0.1, 10.0);

        BUILDER.pop();

        // ===== 13. 再生护盾 =====
        BUILDER.comment("护盾自然恢复系统配置").push("shield_natural_recovery");
        
        NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK = BUILDER.comment(
            "再生护盾基础恢复值（每次恢复的比例，不是每刻）",
            "装备再生护盾模块后，每次自然恢复额外增加的最大护盾比例",
            "恢复频率：每4刻执行一次（每秒5次），每次恢复量 = naturalRecoveryShield/5 + 该值",
            "实际恢复还会乘恢复效率属性和护盾存在修正系数，以最大护盾值为基数（有限资源制）",
            "默认 0.004 = 每次 0.4%（折合每秒 2%）",
            "范围：0.0 ~ 0.1"
        ).defineInRange("naturalRecoveryShieldRecoveryPerTick", 0.004, 0.0, 0.1);

        NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER = BUILDER.comment(
            "护盾存在时的玩家生命恢复修正值",
            "当护盾冷却完成后，玩家生命恢复会乘以此系数",
            "例如：0.5 表示生命恢复降低到50%"
        ).defineInRange("naturalRecoveryShieldPresentHealthModifier", 0.5, 0.0, 1.0);

        NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER = BUILDER.comment(
            "护盾存在时的护盾自然恢复修正值",
            "当护盾冷却完成后，护盾恢复会乘以此系数",
            "例如：0.75 表示护盾恢复为75%"
        ).defineInRange("naturalRecoveryShieldPresentShieldModifier", 0.75, 0.0, 2.0);

        BUILDER.pop();

        // ===== 14. 效率 =====
        BUILDER.comment("攻击冷却效率系统配置").push("attack_cooldown_efficiency");

        

        BUILDER.pop();

        // ===== 15. 转化 =====
        BUILDER.comment("转化效果配置").push("conversion");

        

        CONVERSION_RATIO = BUILDER.comment(
            "转化效果的转化比例",
            "较低资源的此比例会被转化为较高资源",
            "例如：0.3 表示将较低资源的30%转化给较高资源",
            "取值范围：0.0 - 1.0"
        ).defineInRange("conversionRatio", 0.3, 0.0, 1.0);

        BUILDER.pop();

        // ===== 17. 强袭 =====
        BUILDER.comment("强袭系统配置").push("assault");

        

        ASSAULT_ATTACK_SPEED_PER_STACK = BUILDER.comment(
            "每层强袭提供的攻击速度独立乘区加成",
            "默认0.1（即10%）",
            "范围：0.01 ~ 1.0"
        ).defineInRange("attackSpeedPerStack", 0.1, 0.01, 1.0);

        ASSAULT_DURATION_TICKS = BUILDER.comment(
            "强袭层数持续时间（刻）",
            "默认40tick（2秒），重复叠加刷新时间",
            "范围：10 ~ 200"
        ).defineInRange("durationTicks", 40, 10, 200);

        ASSAULT_SELF_DAMAGE_PER_STACK = BUILDER.comment(
            "每层强袭对玩家自身造成的伤害",
            "默认0.1",
            "范围：0.01 ~ 10.0"
        ).defineInRange("selfDamagePerStack", 0.05, 0.01, 10.0);

        ASSAULT_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "强袭期间的移动速度独立乘区惩罚",
            "处于强袭时（按住左键期间）施加的减速比例",
            "默认-0.6（即-60%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.6, -0.99, 0.0);

        ASSAULT_OVERFLOW_DAMAGE_EFFICIENCY = BUILDER.comment(
            "强袭攻击速度撞墙转化效率",
            "攻击频率存在上限（每刻最多攻击一次，即20.0攻击速度），",
            "当强袭提供的攻击速度溢出该上限时，按比例等价换算为伤害百分比属性：",
            "溢出伤害% = 溢出攻速 / 20.0 × 效率",
            "（攻击速度即每秒攻击次数，溢出1.0攻速相对上限为1/20=5%频率提升，等价于+5%基础伤害）",
            "效率1.0 = 完全等价转化（被上限浪费的频率全额补偿为伤害，DPS不损失），0.5 = 只补偿一半",
            "默认1.0，范围：0.0 ~ 1.0"
        ).defineInRange("overflowDamageEfficiency", 1.0, 0.0, 1.0);

        BUILDER.pop();

        // ===== 17.5 征途 =====
        BUILDER.comment("征途系统配置").push("journey");

        JOURNEY_ATTACK_SPEED_PER_STACK = BUILDER.comment(
            "每层战意提供的攻击速度独立乘区加成",
            "默认0.0075（即0.75%），40层满层为+30%",
            "范围：0.001 ~ 0.1"
        ).defineInRange("attackSpeedPerStack", 0.0075, 0.001, 0.1);

        JOURNEY_MOVEMENT_SPEED_PER_STACK = BUILDER.comment(
            "每层战意提供的移动速度独立乘区加成",
            "默认0.0075（即0.75%），40层满层为+30%",
            "该加成与增幅护盾一致，带镜头修正",
            "范围：0.001 ~ 0.1"
        ).defineInRange("movementSpeedPerStack", 0.0075, 0.001, 0.1);

        JOURNEY_DURATION_TICKS = BUILDER.comment(
            "战意持续时间（刻）",
            "叠加时刷新持续时间到满值，期间层数保持不变",
            "默认40tick（2秒）"
        ).defineInRange("durationTicks", 100, 1, 600);

        JOURNEY_MAX_STACKS = BUILDER.comment(
            "战意最大叠层数",
            "默认40层，达到上限后新击杀不再增加层数（仅刷新持续时间）",
            "范围：1 ~ 100"
        ).defineInRange("maxStacks", 40, 1, 100);

        JOURNEY_DECAY_INTERVAL_TICKS = BUILDER.comment(
            "战意消退间隔（刻）",
            "持续时间耗尽后，每多少刻消退一批战意层数",
            "默认3刻"
        ).defineInRange("decayIntervalTicks", 3, 1, 100);

        JOURNEY_DECAY_PER_INTERVAL = BUILDER.comment(
            "战意每批消退层数",
            "持续时间耗尽后，每个消退间隔消退的层数",
            "默认2层"
        ).defineInRange("decayPerInterval", 2, 1, 40);

        BUILDER.pop();

        // ===== 18. 充能攻击 =====
        BUILDER.comment("充能攻击系统配置").push("charged_attack");

        

        CHARGED_ATTACK_BASE_CHARGE_RATE = BUILDER.comment(
            "充能攻击基础充能速率（每tick充能值）",
            "实际充能速率 = 基础速率 * 攻击速度加成",
            "默认0.05",
            "范围：0.01 ~ 10.0"
        ).defineInRange("baseChargeRate", 0.05, 0.0, 10.0);

        CHARGED_ATTACK_SPEED_SCALE_FACTOR = BUILDER.comment(
            "攻击速度对充能速率的影响系数",
            "充能速率额外乘区 = 攻击速度 * 此系数",
            "默认0.15",
            "范围：0.0 ~ 1.0"
        ).defineInRange("speedScaleFactor", 1.0, 0.0, 10.0);

        CHARGED_ATTACK_DRAG_COEFFICIENT = BUILDER.comment(
            "充能阻力系数",
            "充能值越大，阻力越大",
            "实际充能增量 = 基础增量 * (1 - 阻力系数 * 充能值 / (充能值 + 阈值))",
            "默认0.8",
            "范围：0.0 ~ 1.0"
        ).defineInRange("dragCoefficient", 1.0, 0.0, 10.0);

        CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR = BUILDER.comment(
            "充能阻力阈值修正系数",
            "动态阈值 = 玩家攻击速度 * 此系数",
            "阈值越大，阻力效果越晚显现，充能前期增长越快",
            "默认1.0",
            "范围：0.1 ~ 10.0"
        ).defineInRange("dragThresholdFactor", 5.0, 0.1, 100.0);

        CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "充能期间的移动速度独立乘区惩罚",
            "处于充能状态时施加的减速比例",
            "默认-0.2（即-20%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.2, -0.99, 0.0);

        CHARGED_ATTACK_ITEM_USE_WHITELIST = BUILDER.comment(
            "充能物品白名单（长按右键充能）",
            "格式：物品注册名=攻击速度修正值",
            "非武器物品没有攻击速度属性修正，长按右键充能时充能会过快，",
            "充能期间该修正值会以原版攻击速度修饰符（加法）形式临时施加在玩家身上，",
            "与其他攻击速度修饰符正常叠加，由原版属性系统计算最终攻速",
            "未在白名单中的物品使用默认修正值 itemUseChargeDefaultSpeedModifier",
            "武器类物品（剑/三叉戟）与工具类武器（镐/斧/铲/锄）不受此限制，使用实际攻击速度属性",
            "示例：minecraft:stick=-3.0"
        ).defineListAllowEmpty("itemUseChargeWhitelist",
            List.of(),
            s -> true
        );

        CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER = BUILDER.comment(
            "充能物品白名单未注册物品的默认攻击速度修正值",
            "以加法修饰符施加，默认-3.0（基础攻速4.0-3.0=1.0）",
            "范围：-4.0 ~ 0.0"
        ).defineInRange("itemUseChargeDefaultSpeedModifier", -3.0, -4.0, 0.0);

        BUILDER.pop();

        // ===== 18.5 弹射物黑名单 =====
        BUILDER.comment("弹射物黑名单配置").push("projectile_blacklist");

        PROJECTILE_BLACKLIST = BUILDER.comment(
            "弹射物黑名单（实体类型注册名）",
            "名单中的弹射物不参与本模组的弹射物系统：不会被充能攻击增幅，也不会被点射复制",
            "默认仅末影珍珠（点射复制会导致多次瞬移，语义混乱且不可控）",
            "示例：minecraft:ender_pearl"
        ).defineListAllowEmpty("projectileBlacklist",
            List.of("minecraft:ender_pearl"),
            s -> true
        );

        BUILDER.pop();

        // ===== 19. 精妙构造 =====
        BUILDER.comment("精妙构造系统配置").push("precision_construct");

        

        PRECISION_CONSTRUCT_BONUS_PER_LEVEL = BUILDER.comment(
            "精妙构造每级提供的构建速度独立乘区加成",
            "0.0025表示每级0.25%",
            "默认0.0025"
        ).defineInRange("precisionConstructBonusPerLevel", 0.0025, 0.0, 1.0);

        BUILDER.pop();

        // ===== 20. 护盾移植 =====
        BUILDER.comment("护盾移植系统配置").push("shield_transfer");

        

        SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY = BUILDER.comment(
            "护盾移植每保护一个实体降低的护盾效果和护盾效果半径百分比",
            "0.03表示每保护一个实体降低3%",
            "降低值之间相乘计算，例如3个实体：0.97*0.97*0.97=0.91，降低0.09",
            "默认0.04"
        ).defineInRange("effectPenaltyPerEntity", 0.04, 0.0, 1.0);

        BUILDER.pop();

        // ===== 21. 追击阵列/突击无人机 =====
        BUILDER.comment("追击阵列/突击无人机配置").push("pursuit_array");

        

        

        

        DRONE_BASE_HEALTH = BUILDER.comment(
            "无人机基础最大生命值"
        ).defineInRange("droneBaseHealth", 5.0, 1.0, 1000.0);

        DRONE_BASE_DAMAGE = BUILDER.comment(
            "无人机基础伤害（每颗子弹）"
        ).defineInRange("droneBaseDamage", 0.3, 0.01, 100.0);

        DRONE_MAX_COUNT = BUILDER.comment(
            "无人机最大数量"
        ).defineInRange("droneMaxCount", 3, 1, 20);

        DRONE_FOLLOW_RANGE = BUILDER.comment(
            "无人机跟随范围（格）"
        ).defineInRange("droneFollowRange", 16.0, 4.0, 64.0);

        ORBIT_ATTACK_INTERVAL = BUILDER.comment(
            "环绕阵列攻击间隔（秒）"
        ).defineInRange("orbitAttackInterval", 0.5, 0.05, 10.0);

        ORBIT_ATTACK_RANGE = BUILDER.comment(
            "环绕阵列攻击范围（格）"
        ).defineInRange("orbitAttackRange", 8.0, 1.0, 64.0);

        PURSUIT_ATTACK_INTERVAL = BUILDER.comment(
            "追击阵列攻击间隔（秒）"
        ).defineInRange("pursuitAttackInterval", 0.33, 0.05, 10.0);

        PURSUIT_ATTACK_RANGE = BUILDER.comment(
            "追击阵列攻击范围（格）"
        ).defineInRange("pursuitAttackRange", 20.0, 1.0, 64.0);

        BUILDER.pop();

        // ===== 21.5 斩杀机制 =====
        BUILDER.comment("斩杀机制配置").push("drone_execute");

        DRONE_EXECUTE_ENABLED = BUILDER.comment(
            "是否启用斩杀机制",
            "启用时：当目标生命值低于本模组非玩家伤害，伤害归属玩家（爆炸伤害源）",
            "禁用时：伤害量不变，但伤害源不归属玩家（不会触发玩家的击杀效果）"
        ).define("droneExecuteEnabled", true);

        BUILDER.pop();

        // ===== 21.6 僚机 =====
        BUILDER.comment("僚机构造体配置").push("wingman");

        

        WINGMAN_BASE_HEALTH = BUILDER.comment(
            "僚机基础最大生命值"
        ).defineInRange("wingmanBaseHealth", 24.0, 1.0, 1000.0);

        WINGMAN_MAX_COUNT = BUILDER.comment(
            "僚机最大数量"
        ).defineInRange("wingmanMaxCount", 1, 1, 20);

        WINGMAN_ATTACK_INTERVAL = BUILDER.comment(
            "僚机攻击间隔（秒）"
        ).defineInRange("wingmanAttackInterval", 1.0, 0.05, 100.0);

        WINGMAN_ATTACK_RANGE = BUILDER.comment(
            "僚机攻击范围（格）"
        ).defineInRange("wingmanAttackRange", 20.0, 1.0, 64.0);

        WINGMAN_EXPLOSIVE_COUNT = BUILDER.comment(
            "每次攻击发射的爆破弹数量"
        ).defineInRange("wingmanExplosiveCount", 3, 1, 20);

        WINGMAN_EXPLOSIVE_DAMAGE = BUILDER.comment(
            "每颗爆破弹命中伤害"
        ).defineInRange("wingmanExplosiveDamage", 0.5, 0.01, 100.0);

        WINGMAN_EXPLOSION_DAMAGE = BUILDER.comment(
            "爆破弹销毁时爆炸伤害"
        ).defineInRange("wingmanExplosionDamage", 0.5, 0.01, 100.0);

        WINGMAN_EXPLOSION_RADIUS = BUILDER.comment(
            "爆破弹销毁时爆炸半径（格）"
        ).defineInRange("wingmanExplosionRadius", 2.0, 0.1, 10.0);

        

        WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS = BUILDER.comment(
            "拦截机充能攻击基础充能时间（tick）",
            "默认60（3秒），范围：10 ~ 600"
        ).defineInRange("wingmanInterceptorChargeDurationTicks", 60, 10, 600);

        WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS = BUILDER.comment(
            "拦截机充能攻击最大充能时间（tick），近战模式等待接近的超时上限",
            "默认120（6秒），范围：20 ~ 1200"
        ).defineInRange("wingmanInterceptorMaxChargeDurationTicks", 120, 20, 1200);

        WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE = BUILDER.comment(
            "拦截机充能横扫基础范围（格）",
            "实际范围 = 基础范围 × 充能值倍率",
            "默认2.0，范围：0.5 ~ 10.0"
        ).defineInRange("wingmanInterceptorChargedSweepBaseRange", 2.0, 0.5, 10.0);

        

        WINGMAN_EVOLUTION_BONUS_PER_LEVEL = BUILDER.comment(
            "僚机进化：每级光点等级提供的属性加成百分比",
            "默认：0.00625（0.625%），范围：0.0 ~ 1.0"
        ).defineInRange("wingmanEvolutionBonusPerLevel", 0.00625, 0.0, 1.0);

        

        WINGMAN_NANO_REGEN_PERCENT = BUILDER.comment(
            "僚机纳米再生：每秒恢复最大生命值的百分比",
            "默认：0.02（2%），范围：0.0 ~ 1.0"
        ).defineInRange("wingmanNanoRegenPercent", 0.02, 0.0, 1.0);

        

        WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER = BUILDER.comment(
            "震撼弹模块：爆破弹爆炸伤害倍率",
            "默认：2.0（+100%），范围：1.0 ~ 10.0"
        ).defineInRange("wingmanShockwaveDamageMultiplier", 2.0, 1.0, 10.0);

        WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER = BUILDER.comment(
            "震撼弹模块：爆破弹溅射长度倍率",
            "默认：1.5（+50%），范围：1.0 ~ 10.0"
        ).defineInRange("wingmanShockwaveSplashLengthMultiplier", 1.5, 1.0, 10.0);

        BUILDER.pop();

        // ===== 21.7 蜂群 (swarm) =====
        BUILDER.comment("蜂群构造体配置").push("swarm");

        

        SWARM_BASE_HEALTH = BUILDER.comment(
            "蜂群基础最大生命值（支持小数）"
        ).defineInRange("swarmBaseHealth", 1.6, 0.1, 1000.0);

        SWARM_BASE_DAMAGE = BUILDER.comment(
            "蜂群基础攻击伤害（电弧单次伤害）"
        ).defineInRange("swarmBaseDamage", 0.08, 0.01, 100.0);

        SWARM_ATTACK_INTERVAL = BUILDER.comment(
            "蜂群攻击间隔（秒），1.5/s 对应约0.667秒"
        ).defineInRange("swarmAttackInterval", 1.5, 0.05, 100.0);

        SWARM_ATTACK_RANGE = BUILDER.comment(
            "蜂群攻击范围（格），电弧生效半径"
        ).defineInRange("swarmAttackRange", 4.0, 0.5, 32.0);

        SWARM_SEARCH_RANGE = BUILDER.comment(
            "蜂群索敌范围（格）"
        ).defineInRange("swarmSearchRange", 20.0, 1.0, 64.0);

        SWARM_MAX_COUNT = BUILDER.comment(
            "蜂群最大数量"
        ).defineInRange("swarmMaxCount", 4, 1, 100);

        SWARM_COUNT_LIMIT = BUILDER.comment(
            "蜂群数量极限值",
            "当蜂群数量超过此值时，",
            "不再增加蜂群数量上限，而是提升每只蜂群的基础属性和易伤值",
            "确保实际效果基本等价于原本没有蜂群极限值的效果",
            "设为0表示不限制"
        ).defineInRange("swarmCountLimit", 35, 0, 100);

        SWARM_MOVE_SPEED = BUILDER.comment(
            "蜂群移动速度（格/刻）"
        ).defineInRange("swarmMoveSpeed", 0.25, 0.01, 10.0);

        SWARM_BUILD_TIME = BUILDER.comment(
            "蜂群构建时间（tick），20tick=1秒"
        ).defineInRange("swarmBuildTime", 20, 1, 6000);

        SWARM_TIER_UPGRADE_CHANCE_STANDARD = BUILDER.comment(
            "蜂群构建时升阶为标准的概率（0~1）"
        ).defineInRange("swarmTierUpgradeChanceStandard", 0.1, 0.0, 1.0);

        SWARM_TIER_UPGRADE_CHANCE_ADVANCED = BUILDER.comment(
            "蜂群构建时升阶为高阶的概率（0~1）"
        ).defineInRange("swarmTierUpgradeChanceAdvanced", 0.03, 0.0, 1.0);

        SWARM_VULNERABILITY_VALUE = BUILDER.comment(
            "蜂群攻击施加的易伤值（0.0025=0.25%），可叠加"
        ).defineInRange("swarmVulnerabilityValue", 0.0025, 0.0, 10.0);

        SWARM_SHIELD_REPAIR_MULTIPLIER = BUILDER.comment(
            "蜂群修复模式伤害->护盾恢复转化倍率（5=5倍伤害值）"
        ).defineInRange("swarmShieldRepairMultiplier", 3.0, 0.0, 100.0);

        BUILDER.pop();

        // ===== 22. 宽限协议 =====
        BUILDER.comment("濒死保护系统配置").push("near_death_protection");

        

        NEAR_DEATH_PROTECTION_COOLDOWN = BUILDER.comment(
            "宽限协议冷却时间（tick）",
            "触发濒死保护后的冷却时间，期间无法再次触发",
            "默认300tick（15秒）"
        ).defineInRange("nearDeathProtectionCooldown", 300, 20, 6000);

        NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION = BUILDER.comment(
            "宽限协议无敌持续时间（tick）",
            "触发濒死保护后的无敌持续时间",
            "默认20tick（1秒）"
        ).defineInRange("nearDeathProtectionInvincibleDuration", 20, 1, 200);

        BUILDER.pop();

        // ===== 23. 高等工程 =====
        BUILDER.comment("高等工程系统配置").push("advanced_engineering");

        

        ADVANCED_ENGINEERING_BONUS_PER_LEVEL = BUILDER.comment(
            "高等工程每级提供的无人机生命和伤害独立乘区加成",
            "0.01表示每级1%",
            "默认0.01"
        ).defineInRange("advancedEngineeringBonusPerLevel", 0.01, 0.0, 1.0);

        BUILDER.pop();

        // ===== 24. 最后指令 =====
        BUILDER.comment("濒死自爆系统配置").push("near_death_explosion");

        

        NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION = BUILDER.comment(
            "最后指令无敌持续时间（tick）",
            "触发濒死自爆后的无敌持续时间（也是自爆飞行时间）",
            "默认100tick（5秒）"
        ).defineInRange("nearDeathExplosionInvincibleDuration", 100, 20, 6000);

        NEAR_DEATH_EXPLOSION_COEFFICIENT = BUILDER.comment(
            "最后指令爆炸系数",
            "爆炸伤害 = 无人机最大生命值 × 当前速度 × 爆炸系数"
        ).defineInRange("nearDeathExplosionCoefficient", 2.0, 0.1, 100.0);

        NEAR_DEATH_EXPLOSION_RADIUS = BUILDER.comment(
            "最后指令爆炸半径（格）"
        ).defineInRange("nearDeathExplosionRadius", 3.0, 0.5, 20.0);

        NEAR_DEATH_EXPLOSION_SEARCH_RADIUS = BUILDER.comment(
            "最后指令搜索危险物半径（格）"
        ).defineInRange("nearDeathExplosionSearchRadius", 50.0, 5.0, 100.0);

        NEAR_DEATH_EXPLOSION_INITIAL_SPEED = BUILDER.comment(
            "最后指令初始速度（格/tick）"
        ).defineInRange("nearDeathExplosionInitialSpeed", 0.15, 0.01, 2.0);

        NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION = BUILDER.comment(
            "最后指令速度加速度（格/tick²）",
            "每tick增加的速度"
        ).defineInRange("nearDeathExplosionSpeedAcceleration", 0.05, 0.001, 1.0);

        NEAR_DEATH_EXPLOSION_TURN_SPEED_BASE = BUILDER.comment(
            "最后指令基础转向速度（度/tick）"
        ).defineInRange("nearDeathExplosionTurnSpeedBase", 20.0, 1.0, 90.0);

        NEAR_DEATH_EXPLOSION_TURN_SPEED_GROWTH_PER_SECOND = BUILDER.comment(
            "最后指令转向速度每秒提升比例",
            "实际转向速度 = 基础转向速度 × (1 + 每秒提升比例 × 已过去秒数)",
            "默认0.1（每秒提升10%）"
        ).defineInRange("nearDeathExplosionTurnSpeedGrowthPerSecond", 0.1, 0.0, 2.0);

        BUILDER.pop();

        // ===== 24.5 自毁装置 =====
        BUILDER.comment("自毁装置系统配置").push("self_destruct");

        

        SELF_DESTRUCT_BASE_DAMAGE = BUILDER.comment(
            "自毁装置基础爆炸伤害",
            "构造体被摧毁时的基础爆炸伤害",
            "默认1.0"
        ).defineInRange("selfDestructBaseDamage", 1.0, 0.0, 1000.0);

        SELF_DESTRUCT_BASE_RADIUS = BUILDER.comment(
            "自毁装置基础爆炸半径（格）",
            "构造体被摧毁时的基础爆炸半径",
            "默认1.0"
        ).defineInRange("selfDestructBaseRadius", 1.0, 0.0, 100.0);

        SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH = BUILDER.comment(
            "自毁装置每点最大生命值增加的爆炸伤害",
            "默认1.0"
        ).defineInRange("selfDestructDamagePerMaxHealth", 1.0, 0.0, 100.0);

        SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH = BUILDER.comment(
            "自毁装置每点最大生命值增加的爆炸半径（格）",
            "默认0.3"
        ).defineInRange("selfDestructRadiusPerMaxHealth", 0.3, 0.0, 10.0);

        BUILDER.pop();

        // ===== 24.5.2 次级攻击伤害合并 =====
        BUILDER.comment("次级攻击伤害合并系统配置").push("secondary_damage_merge");

        SECONDARY_DAMAGE_MERGE_ENABLED = BUILDER.comment(
            "启用次级攻击伤害合并",
            "启用后，无人机子弹/僚机爆破弹/模拟爆炸/能量波爆炸等次级攻击的伤害",
            "在同一目标的时间窗口内累积合并，时间结束后一次性施加，降低实体受击频率"
        ).define("secondaryDamageMergeEnabled", true);

        SECONDARY_DAMAGE_MERGE_WINDOW_TICKS = BUILDER.comment(
            "次级攻击伤害合并时间窗口（tick）",
            "同一目标在同类型伤害的时间窗口内累积合并",
            "默认10tick（0.5秒）"
        ).defineInRange("secondaryDamageMergeWindowTicks", 10, 1, 100);

        BUILDER.pop();

        // ===== 24.5.3 弹射物次级爆炸（爆炸半径模块特殊机制） =====
        BUILDER.comment("弹射物次级爆炸配置（爆炸半径模块特殊机制）").push("secondary_explosion");

        SECONDARY_EXPLOSION_DAMAGE_FRACTION = BUILDER.comment(
            "次级爆炸伤害占弹射物伤害的比例",
            "弹射物次级爆炸：归属玩家的弹射物造成伤害后从世界移除时爆炸",
            "爆炸伤害 = 已记录最高弹射物伤害 × 该比例",
            "默认0.15"
        ).defineInRange("secondaryExplosionDamageFraction", 0.15, 0.0, 1.0);

        SECONDARY_EXPLOSION_RADIUS_BASE = BUILDER.comment(
            "次级爆炸半径基础值（格）",
            "次级爆炸半径 = 基础值 + 爆炸伤害 × 每点伤害半径增量，之后经爆炸半径属性组增幅",
            "默认2.0"
        ).defineInRange("secondaryExplosionRadiusBase", 2.0, 0.0, 32.0);

        SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION = BUILDER.comment(
            "次级爆炸每点爆炸伤害增加的爆炸半径（格）",
            "默认0.5"
        ).defineInRange("secondaryExplosionRadiusDamageFraction", 0.5, 0.0, 8.0);

        BUILDER.pop();

        // ===== 24.5.1 炉心融解模块（配置挂载于自毁装置段，无独立 push） =====

        // ===== 25. 列队阵列 =====
        BUILDER.comment("列队阵列配置").push("formation_array");

        

        FORMATION_ATTACK_INTERVAL = BUILDER.comment(
            "列队阵列攻击间隔（秒）"
        ).defineInRange("formationAttackInterval", 1.0, 0.05, 10.0);

        FORMATION_ATTACK_RANGE = BUILDER.comment(
            "列队阵列攻击范围（格）"
        ).defineInRange("formationAttackRange", 15.0, 1.0, 64.0);

        FORMATION_ATTACK_PASS_DELAY = BUILDER.comment(
            "列队阵列攻击传递延迟（tick）"
        ).defineInRange("formationAttackPassDelay", 3, 1, 60);

        BUILDER.pop();

        // ===== 26. 指挥官 =====
        BUILDER.comment("指挥官系统配置").push("commander");

        

        COMMANDER_MAX_COUNT = BUILDER.comment(
            "指挥官最大数量",
            "同一玩家的无人机中最多同时存在的指挥官数量"
        ).defineInRange("commanderMaxCount", 2, 1, 10);

        COMMANDER_APPOINT_DELAY = BUILDER.comment(
            "指挥官任命延迟（tick）",
            "指挥官数量未达上限时，等待指定tick后自动任命状态最好的无人机"
        ).defineInRange("commanderAppointDelay", 40, 10, 200);

        COMMANDER_VULNERABILITY = BUILDER.comment(
            "指挥官攻击易伤值",
            "指挥官无人机每次攻击命中时施加的易伤值（可叠加）",
            "0.01 = 1%易伤"
        ).defineInRange("commanderVulnerability", 0.01, 0.001, 1.0);

        BUILDER.pop();

        // ===== 27. 守卫阵列/防御无人机 =====
        BUILDER.comment("守卫阵列/防御无人机配置").push("guard_array");

        

        

        GUARD_ATTACK_INTERVAL = BUILDER.comment(
            "守卫阵列攻击间隔（秒）"
        ).defineInRange("guardAttackInterval", 0.5, 0.05, 10.0);

        GUARD_ATTACK_RANGE = BUILDER.comment(
            "守卫阵列攻击范围（格）"
        ).defineInRange("guardAttackRange", 8.0, 1.0, 64.0);

        BUILDER.pop();

        // ===== 28. 弧形屏障 =====
        BUILDER.comment("弧形屏障系统配置").push("arc_barrier");

        

        ARC_BARRIER_POSITION_DEVIATION_THRESHOLD = BUILDER.comment(
            "弧形屏障位置偏差阈值（格）",
            "用于判断防御无人机是否在玩家与伤害源之间",
            "无人机到玩家与伤害源连线的垂直距离小于此值时视为在中间",
            "默认1.0格"
        ).defineInRange("positionDeviationThreshold", 1.0, 0.5, 10.0);

        BUILDER.pop();

        // ===== 29. 反制脉冲 =====
        BUILDER.comment("反制脉冲系统配置").push("counter_pulse");

        

        COUNTER_PULSE_COOLDOWN = BUILDER.comment(
            "反制脉冲冷却时间（tick）",
            "默认60tick（3秒）"
        ).defineInRange("cooldown", 60, 20, 600);

        COUNTER_PULSE_BASE_EXPLOSION_RADIUS = BUILDER.comment(
            "反制脉冲基础爆炸半径（格）",
            "默认1.3格"
        ).defineInRange("baseExplosionRadius", 1.3, 0.5, 10.0);

        COUNTER_PULSE_BASE_EXPLOSION_DAMAGE = BUILDER.comment(
            "反制脉冲基础爆炸伤害",
            "默认1.0"
        ).defineInRange("baseExplosionDamage", 1.0, 0.1, 100.0);

        COUNTER_PULSE_CHARGE_INTERVAL = BUILDER.comment(
            "反制脉冲充能间隔（tick）",
            "每多少tick充能1层",
            "默认3tick"
        ).defineInRange("chargeInterval", 3, 1, 100);

        COUNTER_PULSE_MAX_CHARGE_LEVEL = BUILDER.comment(
            "反制脉冲最大充能层数",
            "默认1000层"
        ).defineInRange("maxChargeLevel", 1000, 10, 10000);

        BUILDER.pop();

        // ===== 30. 重塑 =====
        BUILDER.comment("重塑系统配置").push("reshaping");

        

        RESHAPING_HEAL_RATE = BUILDER.comment(
            "重塑防御无人机生命恢复速率",
            "每秒恢复最大生命值的百分比",
            "默认0.02（2%/秒）"
        ).defineInRange("healRate", 0.02, 0.0, 1.0);

        RESHAPING_BASE_DAMAGE_REDUCTION = BUILDER.comment(
            "重塑装甲碎片基础伤害减免（%）",
            "玩家吸收装甲碎片后获得的基础伤害减免百分比",
            "实际减免 = 基础减免 × (1 + 伤害加成/100)",
            "默认15.0%"
        ).defineInRange("baseDamageReduction", 15.0, 0.0, 100.0);

        RESHAPING_DAMAGE_REDUCTION_DURATION = BUILDER.comment(
            "重塑伤害减免持续时间（tick）",
            "玩家吸收装甲碎片后伤害减免的持续时间",
            "默认100tick（5秒）"
        ).defineInRange("damageReductionDuration", 100, 20, 6000);

        BUILDER.pop();

        // ===== 31. 充能护盾 =====
        BUILDER.comment("充能护盾系统配置").push("charged_shield");

        

        CHARGED_SHIELD_CHARGE_RATIO = BUILDER.comment(
            "充能值转化为护盾加成的比率",
            "动态属性值 = 累计充能值 * 此比率",
            "默认0.1（即10%）",
            "范围：0.01 ~ 1.0"
        ).defineInRange("chargeRatio", 0.1, 0.01, 1.0);

        CHARGED_SHIELD_MAX_BONUS = BUILDER.comment(
            "动态属性值上限",
            "动态属性值不超过此值（独立乘区值，0.8即80%加成）",
            "默认0.8",
            "范围：0.1 ~ 5.0"
        ).defineInRange("maxBonus", 0.8, 0.1, 5.0);

        CHARGED_SHIELD_DECAY_RATE = BUILDER.comment(
            "充能护盾消退速率（每tick消退的独立乘区值）",
            "停止充能后，动态属性值按此速率线性消退",
            "默认0.05（即每tick消退5%，约16tick完全消退）",
            "范围：0.005 ~ 0.5"
        ).defineInRange("decayRate", 0.08, 0.005, 0.5);

        CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "充能护盾模块附加的移动速度独立乘区惩罚",
            "充能期间若拥有充能护盾模块，额外施加的减速比例",
            "默认-0.15（即-15%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.15, -0.99, 0.0);

        BUILDER.pop();

        // ===== 35. 幽灵机身 =====
        BUILDER.comment("幽灵机身配置").push("ghost_fuselage");

        

        GHOST_FUSELAGE_STEALTH_SPEED_BONUS_PER_LEVEL = BUILDER.comment(
            "每级光点等级增加的隐身速度百分比",
            "默认0.005（即0.5%/级）",
            "范围：0.0 ~ 1.0"
        ).defineInRange("stealthSpeedBonusPerLevel", 0.005, 0.0, 1.0);

        GHOST_FUSELAGE_MAX_BONUS_PER_LEVEL = BUILDER.comment(
            "每级光点等级增加的最大伤害加成百分比",
            "默认0.005（即0.5%/级），最大加成=基础最大伤害加成×(1+level×此值）",
            "范围：0.0 ~ 1.0"
        ).defineInRange("maxBonusPerLevel", 0.005, 0.0, 1.0);

        GHOST_FUSELAGE_BASE_MAX_DAMAGE_BONUS = BUILDER.comment(
            "基础最大伤害加成（完全隐身时的独立乘区伤害加成）",
            "默认3.0（即+300%）",
            "范围：0.0 ~ 100.0"
        ).defineInRange("baseMaxDamageBonus", 3.0, 0.0, 100.0);

        GHOST_FUSELAGE_MOVE_SPEED_THRESHOLD = BUILDER.comment(
            "移动速度阈值（blocks/tick），超过此值开始扣除隐身进度",
            "默认0.25（正常行走以下不扣除）",
            "范围：0.0 ~ 10.0"
        ).defineInRange("moveSpeedThreshold", 0.25, 0.0, 10.0);

        GHOST_FUSELAGE_MOVE_SPEED_REDUCTION = BUILDER.comment(
            "移动速度超过阈值时，每0.1 blocks/tick扣除的隐身进度",
            "默认0.2（正常行走0.1×0.2=0.02，与基础增长持平）",
            "范围：0.0 ~ 1.0"
        ).defineInRange("moveSpeedReduction", 0.2, 0.0, 1.0);

        GHOST_FUSELAGE_DECAY_RATE = BUILDER.comment(
            "破隐后隐身进度每tick消退的当前值比例",
            "默认0.3（即每刻消退当前值的30%）",
            "范围：0.01 ~ 0.99"
        ).defineInRange("decayRate", 0.3, 0.01, 0.99);

        GHOST_FUSELAGE_MIN_DECAY = BUILDER.comment(
            "破隐后隐身进度每tick的最低消退量",
            "默认0.02（即每刻至少消退2%进度）",
            "范围：0.0 ~ 0.1"
        ).defineInRange("minDecay", 0.02, 0.0, 0.1);

        GHOST_FUSELAGE_FULL_STEALTH_TICKS = BUILDER.comment(
            "达到完全隐身（100%进度）所需的tick数",
            "默认40（即2秒）",
            "范围：1 ~ 600（0.05秒 ~ 30秒）"
        ).defineInRange("fullStealthTicks", 40, 1, 600);

        BUILDER.pop();

        // ===== 36. 积怨 =====
        BUILDER.comment("积怨系统配置").push("grudge");

        

        GRUDGE_CONVERSION_RATIO = BUILDER.comment(
            "积怨转化比率",
            "每点损失的生命或护盾值转化为多少充能速率",
            "默认0.05（即1:0.05）",
            "范围：0.001 ~ 1.0"
        ).defineInRange("conversionRatio", 0.2, 0.001, 1.0);

        GRUDGE_FADE_BASE = BUILDER.comment(
            "积怨消退基础速率",
            "消退时每tick固定减少的充能速率",
            "默认1.0",
            "范围：0.0 ~ 10.0"
        ).defineInRange("fadeBase", 0.00001, 0.0, 10.0);

        GRUDGE_FADE_PERCENT = BUILDER.comment(
            "积怨消退百分比",
            "消退时每tick额外减少当前值的百分比",
            "消退速度 = fadeBase + 当前值 * fadePercent",
            "默认0.08（即8%）",
            "范围：0.0 ~ 1.0"
        ).defineInRange("fadePercent", 0.02, 0.0, 1.0);

        GRUDGE_MOVEMENT_SPEED_PENALTY = BUILDER.comment(
            "积怨模块附加的移动速度独立乘区惩罚",
            "充能期间若拥有积怨模块，额外施加的减速比例",
            "默认-0.15（即-15%，独立乘区）",
            "范围：-0.99 ~ 0.0"
        ).defineInRange("movementSpeedPenalty", -0.15, -0.99, 0.0);

        BUILDER.pop();

        // ===== 32. 升级系统 =====
        BUILDER.comment("升级系统配置").push("upgrade_system");

        UPGRADE_SYSTEM_ENABLED = BUILDER.comment("是否启用升级系统").define("enabled", true);

        BUILDER.pop();

        // ===== 33. 快速装备 =====
        BUILDER.comment("快速装备配置").push("quick_equip");

        QUICK_EQUIP_UPGRADE_POINTS_COST = BUILDER.comment(
            "快速装备每次消耗的升级点数量",
            "每次触发快速装备时消耗的升级点数量",
            "默认1"
        ).defineInRange("upgradePointsCost", 1, 0, 100);

        BUILDER.pop();

        // ===== 34. 其他通用设置 =====
        BUILDER.comment("困难模式配置").push("hardcore_mode");

        HARDCORE_MODE_ENABLED = BUILDER.comment(
            "困难模式",
            "启用后，当玩家死亡时，将移除玩家光点核心内所有物品",
            "默认不启用"
        ).define("hardcoreMode", false);

        BUILDER.pop();

        BUILDER.comment("护盾格挡配置").push("shield_block");

        SHIELD_BLOCK_INVULNERABLE_TICKS = BUILDER.comment(
            "护盾格挡时施加的无敌状态持续时间（刻）",
            "当护盾完全吸收伤害时，被攻击者获得短暂无敌帧",
            "默认5刻（0.25秒）"
        ).defineInRange("blockInvulnerableTicks", 5, 0, 100);

        BUILDER.pop();

        // ===== 护盾音效 =====
        BUILDER.comment("护盾音效配置").push("shield_sound");

        SHIELD_HIT_SOUND = BUILDER.comment(
            "护盾受击音效",
            "可选值：",
            "  shield_hit - 自定义护盾受击音效（assets/gytrinket/sounds/shield_hit.ogg）",
            "  vanilla_hurt - 原版玩家受击音效",
            "  none - 不播放音效",
            "默认：shield_hit"
        ).define("shieldHitSound", "shield_hit");

        BUILDER.pop();

        BUILDER.comment("点燃系统配置").push("ignite_system");

        IGNITE_DEFAULT_DAMAGE = BUILDER.comment("点燃默认伤害").defineInRange("igniteDefaultDamage", 1.2, 0.0, 100.0);
        IGNITE_DEFAULT_DURATION = BUILDER.comment("点燃默认持续时间（秒）").defineInRange("igniteDefaultDuration", 3, 1, 600);

        BUILDER.pop();

        BUILDER.comment("自然恢复系统配置").push("natural_recovery");

        NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED = BUILDER.comment(
            "是否启用玩家基础生命自然恢复",
            "启用：无论恢复效率属性值多少，始终按 naturalRecoveryPlayerHealth 的值恢复",
            "不启用：仅当恢复效率属性 > 1 时才恢复，恢复量为 naturalRecoveryPlayerHealth 的值；否则不恢复"
        ).define("playerHealthEnabled", true);
        NATURAL_RECOVERY_PLAYER_HEALTH = BUILDER.comment(
            "玩家基础生命恢复速度（%/秒）",
            "恢复频率：每4刻执行一次（每秒5次），每次实际恢复量 = 恢复基数 ×（该值 ÷ 5）",
            "恢复基数采用有限资源制：原版最大生命值（高于20时限为20）叠加本模组生命修改属性，其他模组生命修饰符不计入",
            "实际恢复量还会乘恢复效率属性与攻击冷却惩罚系数",
            "默认 0.02 = 每秒恢复恢复基数的 2%（每次 0.4%）",
            "范围：0.0 ~ 10.0"
        ).defineInRange("naturalRecoveryPlayerHealth", 0.02, 0.0, 10.0);
        NATURAL_RECOVERY_SHIELD = BUILDER.comment(
            "护盾基础恢复速度（%/秒，0为禁用）",
            "恢复频率：每4刻执行一次（每秒5次），每次实际恢复量 = 最大护盾值 ×（该值 ÷ 5）（有限资源制）",
            "实际恢复量还会乘恢复效率属性与攻击冷却惩罚系数；装备再生护盾模块时与 naturalRecoveryShieldRecoveryPerTick 叠加",
            "默认 0.0 = 每秒恢复最大护盾的 0%",
            "范围：0.0 ~ 10.0"
        ).defineInRange("naturalRecoveryShield", 0.0, 0.0, 10.0);
        NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY = BUILDER.comment(
            "攻击冷却期间恢复惩罚系数（0-1，越低恢复越少）",
            "玩家处于攻击冷却（攻击强度刻度 > 0.5）时，生命与护盾的恢复量乘以此系数",
            "默认 0.8 = 恢复量降低 20%"
        ).defineInRange("naturalRecoveryAttackCooldownPenalty", 0.8, 0.0, 1.0);

        BUILDER.pop();

        // ===== 敌对目标系统 =====
        BUILDER.comment("敌对目标系统配置").push("hostile_target");

        HOSTILE_TARGET_MARK_DURATION = BUILDER.comment(
            "玩家攻击标记持续时间（tick）",
            "被玩家攻击过的实体会被标记为威胁，持续此时间后标记失效",
            "20 tick = 1秒，默认100 tick = 5秒"
        ).defineInRange("markDuration", 100, 1, 10000);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static final Map<String, Boolean> SHIELD_TYPE_COMPATIBILITY = new HashMap<>();
    private static final Map<Item, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final Set<Item> BODY_ITEM_SET = new HashSet<>();
    private static final Set<Item> DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ASSAULT_DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> DEFENSE_DRONE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_INTERCEPTOR_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_EVOLUTION_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_NANO_REGEN_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> WINGMAN_SHOCKWAVE_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> SWARM_MODULE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADAPTIVE_ARMOR_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET = new HashSet<>();
    private static final Set<Item> BARRIER_ITEM_SET = new HashSet<>();
    private static final Set<Item> EXPLOSIVE_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> REFLECT_DAMAGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ELECTRIC_DISCHARGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> SHIELD_TRANSFER_ITEM_SET = new HashSet<>();
    private static final Set<Item> ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET = new HashSet<>();
    private static final Set<Item> SHIELD_NATURAL_RECOVERY_ITEM_SET = new HashSet<>();
    private static final Set<Item> BINARY_PROTOCOL_ITEM_SET = new HashSet<>();
    private static final Set<Item> WEAPONIZED_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> CONVERSION_ITEM_SET = new HashSet<>();
    private static final Set<String> DANGEROUS_ENTITY_SET = new HashSet<>();
    private static final Set<Item> NEAR_DEATH_PROTECTION_ITEM_SET = new HashSet<>();
    private static final Set<Item> NEAR_DEATH_EXPLOSION_ITEM_SET = new HashSet<>();
    private static final Set<Item> SELF_DESTRUCT_ITEM_SET = new HashSet<>();
    private static final Set<Item> FURNACE_CORE_ITEM_SET = new HashSet<>();
    private static final Set<Item> TASKMASTER_ITEM_SET = new HashSet<>();
    private static final Set<Item> COMMANDER_ITEM_SET = new HashSet<>();
    private static final Set<Item> ARC_BARRIER_ITEM_SET = new HashSet<>();
    private static final Set<Item> RESHAPING_ITEM_SET = new HashSet<>();
    private static final Set<Item> COUNTER_PULSE_ITEM_SET = new HashSet<>();
    private static final Set<Item> ASSAULT_ITEM_SET = new HashSet<>();
    private static final Set<Item> CHARGED_ATTACK_ITEM_SET = new HashSet<>();
    /** 充能物品白名单缓存（长按右键充能）：物品 -> 攻击速度修正值 */
    private static final Map<Item, Double> ITEM_USE_CHARGE_WHITELIST = new HashMap<>();
    /** 弹射物黑名单缓存：不参与充能攻击增幅与点射复制的实体类型 */
    private static final Set<EntityType<?>> PROJECTILE_BLACKLIST_CACHE = new HashSet<>();
    private static final Set<Item> JOURNEY_MODULE_ITEM_SET = new HashSet<>();
    /** 声明为特殊机制的物品集合（special_mechanics 文件夹声明并集），用于快速装备等统一判定 */
    private static final Set<Item> SPECIAL_MECHANIC_ITEM_SET = new HashSet<>();
    private static final Set<Item> CHARGED_SHIELD_ITEM_SET = new HashSet<>();
    private static final Set<Item> GHOST_FUSELAGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> GRUDGE_ITEM_SET = new HashSet<>();
    private static final Set<Item> PRECISION_CONSTRUCT_ITEM_SET = new HashSet<>();
    private static final Set<Item> ADVANCED_ENGINEERING_ITEM_SET = new HashSet<>();
    private static final Set<Item> PURSUIT_ARRAY_ITEM_SET = new HashSet<>();
    private static final Set<Item> FORMATION_ARRAY_ITEM_SET = new HashSet<>();
    private static final Set<Item> GUARD_ARRAY_ITEM_SET = new HashSet<>();

    public static List<String> getItemShieldTypes(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            return Collections.emptyList();
        }
        return ITEM_SHIELD_TYPES.getOrDefault(item, Collections.emptyList());
    }

    /**
     * 解析禁用类别为实际物品 id 集合
     * 当前支持的类别：
     *   shields -- 注册了护盾类型的物品（基础护盾及带"+"强化护盾）
     */
    public static Set<String> resolveDisableCategory(String category) {
        Set<String> result = new HashSet<>();
        if ("shields".equals(category)) {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
                if (rl != null && rl.getNamespace().equals(com.gytrinket.gytrinket.gytrinket.MODID)
                        && !getItemShieldTypes(rl).isEmpty()) {
                    result.add(rl.toString());
                }
            }
        }
        return result;
    }

    /**
     * 解析依赖类别引用（"category:xxx"）为实际物品 id 集合
     * 当前支持的类别：
     *   construct_final -- 构造体类所有模块树的终阶模块（由 module_trees 数据定义）
     */
    public static Set<String> resolveDependencyCategory(String category) {
        if ("construct_final".equals(category)) {
            return com.gytrinket.gytrinket.core.defs.DefsManager.getCategoryFinalModules("construct");
        }
        return Collections.emptySet();
    }

    public static boolean isShieldTypeCompatible(String typeName) {
        return SHIELD_TYPE_COMPATIBILITY.getOrDefault(typeName, true);
    }

    public static boolean isBodyItem(Item item) {
        return BODY_ITEM_SET.contains(item);
    }

    public static void saveItemAttributesConfig() {
        java.util.Set<String> registeredItems = AttributeManager.getAllRegisteredItemAttributes();
        java.util.List<String> configList = new java.util.ArrayList<>();
        for (String itemId : registeredItems) {
            ItemAttributeConfig config = AttributeManager.getItemAttributes(itemId);
            if (config == null || config.getAttributes().isEmpty()) continue;
            StringBuilder sb = new StringBuilder(itemId);
            for (var entry : config.getAttributes().entrySet()) {
                sb.append("|").append(entry.getKey()).append("=").append(entry.getValue());
            }
            configList.add(sb.toString());
        }
        ITEM_ATTRIBUTES_CONFIG.set(configList);
        SPEC.save();
        gytrinket.LOGGER.info("物品属性配置已保存，共 {} 个物品", configList.size());
    }

    public static void loadItemAttributes() {
        List<? extends String> itemAttrsList = ITEM_ATTRIBUTES_CONFIG.get();
        for (String itemConfig : itemAttrsList) {
            if (!itemConfig.trim().isEmpty()) {
                String[] itemParts = itemConfig.trim().split("\\|");
                if (itemParts.length >= 2) {
                    String itemId = itemParts[0].trim();
                    // 移除命令方块的属性注册（历史测试项）：跳过加载并清理内存残留，防止旧配置文件回写
                    if ("minecraft:command_block".equals(itemId)) {
                        AttributeManager.removeItemAttributes(itemId);
                        continue;
                    }
                    Map<String, Double> attrs = new HashMap<>();
                    for (int i = 1; i < itemParts.length; i++) {
                        String[] attrParts = itemParts[i].trim().split("=");
                        if (attrParts.length == 2) {
                            String attrName = attrParts[0].trim();
                            try {
                                double value = Double.parseDouble(attrParts[1].trim());
                                attrs.put(attrName, value);
                            } catch (NumberFormatException e) {
                                gytrinket.LOGGER.warn("无效的属性值：{} for {}", attrParts[1], itemId);
                            }
                        }
                    }
                    if (!attrs.isEmpty()) {
                        AttributeManager.registerItemAttributes(itemId, attrs);
                        gytrinket.LOGGER.info("注册物品属性: {} -> {}", itemId, attrs);
                    }
                }
            }
        }
    }

    public static void resetItemAttributesConfig() {
        ITEM_ATTRIBUTES_CONFIG.set(ITEM_ATTRIBUTES_CONFIG.getDefault());
        SPEC.save();
        loadItemAttributes();
        gytrinket.LOGGER.info("物品属性配置已重置为默认值");
    }

    /**
     * 解析充能物品白名单配置（长按右键充能）
     * 格式：物品注册名=攻击速度修正值（有效攻速 = 4.0 + 修正值）
     */
    public static void loadItemUseChargeWhitelist() {
        ITEM_USE_CHARGE_WHITELIST.clear();
        for (String entry : CHARGED_ATTACK_ITEM_USE_WHITELIST.get()) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=");
            if (parts.length != 2) {
                gytrinket.LOGGER.warn("无效的充能物品白名单条目（格式应为 物品注册名=攻击速度修正值）：{}", trimmed);
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(parts[0].trim());
            if (itemId == null) {
                gytrinket.LOGGER.warn("无效的物品注册名：{}", parts[0]);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR) {
                gytrinket.LOGGER.warn("充能物品白名单中的物品未注册：{}", parts[0]);
                continue;
            }
            try {
                ITEM_USE_CHARGE_WHITELIST.put(item, Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException e) {
                gytrinket.LOGGER.warn("无效的攻击速度修正值：{} for {}", parts[1], parts[0]);
            }
        }
        gytrinket.LOGGER.info("充能物品白名单加载完成，共 {} 个物品", ITEM_USE_CHARGE_WHITELIST.size());
    }

    /**
     * 解析弹射物黑名单配置
     * 名单中的弹射物不参与本模组的弹射物系统：不会被充能攻击增幅（ProjectileDamageHandler），
     * 也不会被点射复制（ProjectileBurstManager）
     */
    public static void loadProjectileBlacklist() {
        PROJECTILE_BLACKLIST_CACHE.clear();
        for (String entry : PROJECTILE_BLACKLIST.get()) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ResourceLocation entityTypeId = ResourceLocation.tryParse(trimmed);
            if (entityTypeId == null) {
                gytrinket.LOGGER.warn("无效的实体类型注册名：{}", trimmed);
                continue;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
            if (entityType == null) {
                gytrinket.LOGGER.warn("弹射物黑名单中的实体类型未注册：{}", trimmed);
                continue;
            }
            PROJECTILE_BLACKLIST_CACHE.add(entityType);
        }
        gytrinket.LOGGER.info("弹射物黑名单加载完成，共 {} 个实体类型", PROJECTILE_BLACKLIST_CACHE.size());
    }

    /**
     * 弹射物是否在黑名单中（不参与充能攻击增幅与点射复制）
     */
    public static boolean isProjectileBlacklisted(Entity entity) {
        return PROJECTILE_BLACKLIST_CACHE.contains(entity.getType());
    }

    /**
     * 物品是否为武器类（剑/三叉戟）或工具类武器（镐/斧/铲/锄）
     * 这些物品自带攻击速度修正，长按右键充能不受白名单限制
     */
    public static boolean isWeaponLikeItem(Item item) {
        return item instanceof SwordItem || item instanceof TridentItem || item instanceof DiggerItem;
    }

    /**
     * 获取长按右键充能时的攻击速度修正值
     * 以原版加法攻击速度修饰符形式临时施加在玩家 ATTACK_SPEED 属性上，
     * 与其他攻击速度修饰符正常叠加，由原版属性系统计算最终攻速
     * 白名单查值，未注册返回默认修正值
     */
    public static double getItemUseChargeSpeedModifier(Item item) {
        Double modifier = ITEM_USE_CHARGE_WHITELIST.get(item);
        return modifier != null ? modifier : CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER.get();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!event.getConfig().getSpec().equals(SPEC)) {
            return;
        }
        // 每次加载配置时检查配置文件完整性：缺失项补充默认值（保留用户已有值）
        checkAndCompleteConfig(event.getConfig());
        if (initialized) {
            gytrinket.LOGGER.info("配置已初始化，跳过重复加载");
            return;
        }
        initialized = true;

        loadItemAttributes();
        loadItemUseChargeWhitelist();
        loadProjectileBlacklist();

        gytrinket.LOGGER.info("属性系统配置加载完成");
    }

    /**
     * 检查配置文件完整性：若文件缺失 spec 中的配置项，调用 SPEC.save() 将内存中的完整配置
     * （缺失项取默认值，已存在项保留用户值）写回原文件。
     * 解决 NeoForge 检测到配置不统一时生成新配置文件但不生效的问题，
     * 确保旧配置随版本更新自动补齐新增配置项（不删除文件中的多余项）。
     */
    private static void checkAndCompleteConfig(ModConfig config) {
        try {
            if (config == null) {
                return;
            }
            Path configPath = config.getFullPath();
            if (!Files.exists(configPath)) {
                return;
            }
            if (hasMissingConfigKeys(configPath)) {
                SPEC.save();
                gytrinket.LOGGER.info("配置文件存在缺失项，已补充默认值并保存：{}", configPath.getFileName());
            }
        } catch (Exception e) {
            gytrinket.LOGGER.warn("配置完整性检查失败：{}", e.getMessage());
        }
    }

    /**
     * 按分节解析 TOML 配置文件文本，检查 spec 中是否存在文件里缺失的配置项。
     */
    private static boolean hasMissingConfigKeys(Path configPath) throws IOException {
        Map<String, java.util.Set<String>> fileSections = new HashMap<>();
        String currentSection = "";
        for (String line : Files.readAllLines(configPath)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) {
                continue;
            }
            if (t.startsWith("[")) {
                int end = t.indexOf(']');
                if (end > 1) {
                    currentSection = t.substring(1, end).trim();
                }
                continue;
            }
            int eq = t.indexOf('=');
            if (eq > 0) {
                String key = t.substring(0, eq).trim();
                fileSections.computeIfAbsent(currentSection, k -> new java.util.HashSet<>()).add(key);
            }
        }

        final boolean[] missing = {false};
        forEachConfigValue(SPEC.getValues().valueMap().values(), configValue -> {
            List<String> path = configValue.getPath();
            if (path.isEmpty()) {
                return;
            }
            String section = path.size() > 1 ? path.get(0) : "";
            String key = path.get(path.size() - 1);
            java.util.Set<String> keys = fileSections.get(section);
            if (keys == null || !keys.contains(key)) {
                missing[0] = true;
            }
        });
        return missing[0];
    }

    /**
     * 递归遍历 spec 中的全部配置值（处理嵌套的分节结构）
     */
    private static void forEachConfigValue(Iterable<Object> values, Consumer<ModConfigSpec.ConfigValue<?>> consumer) {
        for (Object value : values) {
            if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                consumer.accept(configValue);
            } else if (value instanceof com.electronwill.nightconfig.core.Config innerConfig) {
                forEachConfigValue(innerConfig.valueMap().values(), consumer);
            }
        }
    }

    /**
     * 应用 datapack 定义类数据（由 DefsManager 在数据包重载后调用）
     * 填充物品集合、护盾类型、属性定义、禁用/依赖、升级路径等，并触发依赖子系统重载
     */
    public static void applyDefs() {
        // 属性定义
        for (DefsManager.AttributeEntry entry : DefsManager.getAttributeDefs()) {
            try {
                AttributeType type = AttributeType.valueOf(entry.combine());
                String group = entry.group().isEmpty() ? null : entry.group();
                AttributeManager.registerAttribute(entry.name(), type, group);
            } catch (IllegalArgumentException e) {
                gytrinket.LOGGER.warn("无效的属性组合方式：{}，属性：{}", entry.combine(), entry.name());
            }
        }

        // 护盾类型兼容性
        SHIELD_TYPE_COMPATIBILITY.clear();
        SHIELD_TYPE_COMPATIBILITY.putAll(DefsManager.getShieldTypes());

        // 物品->护盾类型
        ITEM_SHIELD_TYPES.clear();
        DefsManager.getItemShieldTypes().forEach((itemId, types) -> {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null && item != Items.AIR) {
                ITEM_SHIELD_TYPES.put(item, types);
                gytrinket.LOGGER.info("注册物品护盾类型: {} -> {}", itemId, types);
            }
        });

        // 物品集合
        loadItemSetFromDefs(BODY_ITEM_SET, "body_items", "机身物品");
        loadItemSetFromDefs(ADAPTIVE_ARMOR_ITEM_SET, "adaptive_armor_items", "适应性装甲启用");
        loadItemSetFromDefs(ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET, "adaptive_armor_shield_effect_items", "适应性装甲护盾效果");
        loadItemSetFromDefs(ELECTRIC_DISCHARGE_ITEM_SET, "electric_discharge_items", "闪电释放模块");
        loadItemSetFromDefs(ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET, "attack_cooldown_efficiency_items", "攻击冷却效率");
        loadItemSetFromDefs(BARRIER_ITEM_SET, "barrier_items", "屏障处理器启用");
        loadItemSetFromDefs(SHIELD_NATURAL_RECOVERY_ITEM_SET, "shield_natural_recovery_items", "护盾自然恢复");
        loadItemSetFromDefs(REFLECT_DAMAGE_ITEM_SET, "reflect_damage_items", "反射护盾伤害处理器启用");
        loadItemSetFromDefs(EXPLOSIVE_SHIELD_ITEM_SET, "explosive_shield_items", "易爆护盾效果启用");
        loadItemSetFromDefs(SHIELD_TRANSFER_ITEM_SET, "shield_transfer_items", "护盾移植模块");
        loadItemSetFromDefs(BINARY_PROTOCOL_ITEM_SET, "binary_protocol_items", "二元协议");
        loadItemSetFromDefs(WEAPONIZED_SHIELD_ITEM_SET, "weaponized_shield_items", "武器化护盾");
        loadItemSetFromDefs(NEAR_DEATH_PROTECTION_ITEM_SET, "near_death_protection_items", "濒死保护前置");
        loadItemSetFromDefs(NEAR_DEATH_EXPLOSION_ITEM_SET, "near_death_explosion_items", "濒死自爆前置");
        loadItemSetFromDefs(SELF_DESTRUCT_ITEM_SET, "self_destruct_items", "自毁装置前置");
        loadItemSetFromDefs(FURNACE_CORE_ITEM_SET, "furnace_core_items", "炉心融解模块");
        loadItemSetFromDefs(TASKMASTER_ITEM_SET, "taskmaster_items", "督战者前置");
        loadItemSetFromDefs(DRONE_MODULE_ITEM_SET, "drone_module_items", "基础无人机构建");
        loadItemSetFromDefs(ASSAULT_DRONE_MODULE_ITEM_SET, "assault_drone_module_items", "突击无人机构建");
        loadItemSetFromDefs(COMMANDER_ITEM_SET, "commander_required_items", "指挥官前置");
        loadItemSetFromDefs(DEFENSE_DRONE_MODULE_ITEM_SET, "defense_drone_module_items", "防御无人机构建");
        loadItemSetFromDefs(WINGMAN_MODULE_ITEM_SET, "wingman_module_items", "僚机构建");
        loadItemSetFromDefs(WINGMAN_INTERCEPTOR_MODULE_ITEM_SET, "wingman_interceptor_module_items", "拦截机模块");
        loadItemSetFromDefs(WINGMAN_EVOLUTION_MODULE_ITEM_SET, "wingman_evolution_module_items", "进化模块");
        loadItemSetFromDefs(WINGMAN_NANO_REGEN_MODULE_ITEM_SET, "wingman_nano_regen_module_items", "纳米再生模块");
        loadItemSetFromDefs(WINGMAN_SHOCKWAVE_MODULE_ITEM_SET, "wingman_shockwave_module_items", "震撼弹模块");
        loadItemSetFromDefs(SWARM_MODULE_ITEM_SET, "swarm_module_items", "蜂群构建");
        loadItemSetFromDefs(ARC_BARRIER_ITEM_SET, "arc_barrier_items", "弧形屏障启用");
        loadItemSetFromDefs(RESHAPING_ITEM_SET, "reshaping_items", "重塑启用");
        loadItemSetFromDefs(COUNTER_PULSE_ITEM_SET, "counter_pulse_items", "反制脉冲启用");
        loadItemSetFromDefs(ASSAULT_ITEM_SET, "assault_items", "强袭模块");
        loadItemSetFromDefs(CHARGED_ATTACK_ITEM_SET, "charged_attack_items", "充能攻击模块");
        loadItemSetFromDefs(JOURNEY_MODULE_ITEM_SET, "journey_module_items", "征途模块");
        loadItemSetFromDefs(CHARGED_SHIELD_ITEM_SET, "charged_shield_items", "充能护盾模块");
        loadItemSetFromDefs(GHOST_FUSELAGE_ITEM_SET, "ghost_fuselage_items", "幽灵机身模块");
        loadItemSetFromDefs(GRUDGE_ITEM_SET, "grudge_items", "积怨模块");
        loadItemSetFromDefs(PRECISION_CONSTRUCT_ITEM_SET, "precision_construct_items", "精妙构造前置");
        loadItemSetFromDefs(ADVANCED_ENGINEERING_ITEM_SET, "advanced_engineering_items", "高等工程前置");
        loadItemSetFromDefs(PURSUIT_ARRAY_ITEM_SET, "pursuit_array_required_items", "追击阵列前置");
        loadItemSetFromDefs(FORMATION_ARRAY_ITEM_SET, "formation_array_required_items", "编队阵列前置");
        loadItemSetFromDefs(GUARD_ARRAY_ITEM_SET, "guard_array_required_items", "守卫阵列前置");
        loadItemSetFromDefs(CONVERSION_ITEM_SET, "conversion_items", "转化效果启用");

        // 特殊机制物品集合（special_mechanics 文件夹声明并集，供快速装备等统一判定）
        SPECIAL_MECHANIC_ITEM_SET.clear();
        for (String itemId : DefsManager.getSpecialMechanicItems()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null && item != Items.AIR) {
                SPECIAL_MECHANIC_ITEM_SET.add(item);
            }
        }
        gytrinket.LOGGER.info("注册特殊机制物品集合: {} 项", SPECIAL_MECHANIC_ITEM_SET.size());

        // 危险实体
        DANGEROUS_ENTITY_SET.clear();
        DANGEROUS_ENTITY_SET.addAll(DefsManager.getEntitySet("dangerous_entities"));

        // 依赖定义数据的子系统重载
        DisableSystem.loadConfig();
        UpgradeManager.loadConfig();
        ShieldTypeManager.init();

        gytrinket.LOGGER.info("定义类数据已应用：物品集合 {} 项，护盾类型 {} 项，属性定义 {} 项",
                BODY_ITEM_SET.size(), SHIELD_TYPE_COMPATIBILITY.size(), DefsManager.getAttributeDefs().size());
    }

    private static void loadItemSetFromDefs(Set<Item> targetSet, String setName, String logLabel) {
        targetSet.clear();
        for (String itemId : DefsManager.getItemSet(setName)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null && item != Items.AIR) {
                targetSet.add(item);
                gytrinket.LOGGER.info("注册{}物品: {}", logLabel, itemId);
            }
        }
    }

    /**
     * 按配置名统一取值（供数据驱动的 tooltip 参数引用）
     * 返回 int 或 double，保留原始类型以匹配语言文件的格式符（%d / %.1f / %s）
     */
    public static Object getValue(String name) {
        return switch (name) {
            case "shieldNaturalRecoveryPresentHealthModifier" -> getNaturalRecoveryShieldPresentHealthModifier();
            case "shieldNaturalRecoveryPresentShieldModifier" -> getNaturalRecoveryShieldPresentShieldModifier();
            case "explosiveShieldDamage" -> EXPLOSIVE_SHIELD_DAMAGE.get();
            case "shieldTransferEffectPenaltyPerEntity" -> SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get();
            case "weaponizedShieldVulnerability" -> WEAPONIZED_SHIELD_VULNERABILITY.get();
            case "nearDeathProtectionInvincibleDuration" -> NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get();
            case "nearDeathProtectionCooldown" -> NEAR_DEATH_PROTECTION_COOLDOWN.get();
            case "nearDeathExplosionInvincibleDuration" -> NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get();
            case "nearDeathExplosionCoefficient" -> NEAR_DEATH_EXPLOSION_COEFFICIENT.get();
            case "nearDeathExplosionRadius" -> NEAR_DEATH_EXPLOSION_RADIUS.get();
            case "conversionRatio" -> CONVERSION_RATIO.get();
            case "coatingReductionPerLayer" -> getCoatingReductionPerLayer();
            case "arcBarrierPositionDeviationThreshold" -> ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get();
            case "reshapingHealRate" -> RESHAPING_HEAL_RATE.get();
            case "reshapingBaseDamageReduction" -> RESHAPING_BASE_DAMAGE_REDUCTION.get();
            case "reshapingDamageReductionDuration" -> RESHAPING_DAMAGE_REDUCTION_DURATION.get();
            case "counterPulseCooldown" -> COUNTER_PULSE_COOLDOWN.get();
            case "counterPulseBaseExplosionRadius" -> COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get();
            case "counterPulseBaseExplosionDamage" -> COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get();
            case "counterPulseChargeInterval" -> COUNTER_PULSE_CHARGE_INTERVAL.get();
            case "counterPulseMaxChargeLevel" -> COUNTER_PULSE_MAX_CHARGE_LEVEL.get();
            case "precisionConstructBonusPerLevel" -> PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get();
            case "advancedEngineeringBonusPerLevel" -> ADVANCED_ENGINEERING_BONUS_PER_LEVEL.get();
            case "commanderMaxCount" -> COMMANDER_MAX_COUNT.get();
            case "commanderAppointDelay" -> COMMANDER_APPOINT_DELAY.get();
            case "chargedShieldChargeRatio" -> getChargedShieldChargeRatio();
            case "chargedShieldMaxBonus" -> getChargedShieldMaxBonus();
            case "chargedShieldMovementSpeedPenalty" -> getChargedShieldMovementSpeedPenalty();
            case "grudgeConversionRatio" -> getGrudgeConversionRatio();
            case "grudgeFadePercent" -> getGrudgeFadePercent();
            case "grudgeFadeBase" -> getGrudgeFadeBase();
            case "grudgeMovementSpeedPenalty" -> getGrudgeMovementSpeedPenalty();
            case "wingmanShockwaveDamageMultiplier" -> getWingmanShockwaveDamageMultiplier();
            case "wingmanShockwaveSplashLengthMultiplier" -> getWingmanShockwaveSplashLengthMultiplier();
            case "journeyAttackSpeedPerStack" -> getJourneyAttackSpeedPerStack();
            case "journeyMovementSpeedPerStack" -> getJourneyMovementSpeedPerStack();
            case "journeyDurationTicks" -> getJourneyDurationTicks();
            case "journeyMaxStacks" -> getJourneyMaxStacks();
            case "wingmanEvolutionBonusPerLevel" -> getWingmanEvolutionBonusPerLevel();
            case "ghostFuselageFullStealthTicks" -> getGhostFuselageFullStealthTicks();
            case "ghostFuselageBaseMaxDamageBonus" -> getGhostFuselageBaseMaxDamageBonus();
            case "ghostFuselageDecayRate" -> getGhostFuselageDecayRate();
            case "ghostFuselageMinDecay" -> getGhostFuselageMinDecay();
            case "ghostFuselageStealthSpeedBonusPerLevel" -> getGhostFuselageStealthSpeedBonusPerLevel();
            case "secondaryExplosionDamageFraction" -> SECONDARY_EXPLOSION_DAMAGE_FRACTION.get();
            case "secondaryExplosionRadiusBase" -> SECONDARY_EXPLOSION_RADIUS_BASE.get();
            case "secondaryExplosionRadiusDamageFraction" -> SECONDARY_EXPLOSION_RADIUS_DAMAGE_FRACTION.get();
            default -> 0;
        };
    }

    public static double getReflectRadius() {
        return REFLECT_RADIUS.get();
    }

    public static double getReflectSpeedBaseModifier() {
        return REFLECT_SPEED_BASE_MODIFIER.get();
    }

    public static double getReflectSpeedExtraModifier() {
        return REFLECT_SPEED_EXTRA_MODIFIER.get();
    }

    public static double getReflectDamageEffectMultiplier() {
        return REFLECT_DAMAGE_EFFECT_MULTIPLIER.get();
    }

    public static double getIgniteDefaultDamage() {
        return IGNITE_DEFAULT_DAMAGE.get();
    }

    public static int getIgniteDefaultDuration() {
        return IGNITE_DEFAULT_DURATION.get();
    }

    public static double getNaturalRecoveryPlayerHealth() {
        return NATURAL_RECOVERY_PLAYER_HEALTH.get();
    }

    public static double getNaturalRecoveryShield() {
        return NATURAL_RECOVERY_SHIELD.get();
    }

    public static double getNaturalRecoveryAttackCooldownPenalty() {
        return NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY.get();
    }

    public static int getHostileTargetMarkDuration() {
        return HOSTILE_TARGET_MARK_DURATION.get();
    }

    public static double getCoatingReductionPerLayer() {
        return COATING_REDUCTION_PER_LAYER.get();
    }

    public static boolean isDroneModuleItem(Item item) {
        return DRONE_MODULE_ITEM_SET.contains(item);
    }

    public static boolean isAssaultDroneModuleItem(Item item) {
        return ASSAULT_DRONE_MODULE_ITEM_SET.contains(item);
    }

    public static double getDroneBaseHealth() {
        return DRONE_BASE_HEALTH.get();
    }

    public static double getDroneBaseDamage() {
        return DRONE_BASE_DAMAGE.get();
    }

    public static int getDroneMaxCount() {
        return DRONE_MAX_COUNT.get();
    }

    public static double getDroneFollowRange() {
        return DRONE_FOLLOW_RANGE.get();
    }

    public static boolean isDefenseDroneModuleItem(Item item) {
        return DEFENSE_DRONE_MODULE_ITEM_SET.contains(item);
    }

    // ===== 僚机配置方法 =====

    public static boolean isWingmanModuleItem(Item item) {
        return WINGMAN_MODULE_ITEM_SET.contains(item);
    }

    public static double getWingmanBaseHealth() {
        return WINGMAN_BASE_HEALTH.get();
    }

    public static int getWingmanMaxCount() {
        return WINGMAN_MAX_COUNT.get();
    }

    public static double getWingmanAttackInterval() {
        return WINGMAN_ATTACK_INTERVAL.get();
    }

    public static Double getWingmanAttackRange() {
        return WINGMAN_ATTACK_RANGE.get();
    }

    public static int getWingmanExplosiveCount() {
        return WINGMAN_EXPLOSIVE_COUNT.get();
    }

    public static double getWingmanExplosiveDamage() {
        return WINGMAN_EXPLOSIVE_DAMAGE.get();
    }

    public static double getWingmanExplosionDamage() {
        return WINGMAN_EXPLOSION_DAMAGE.get();
    }

    public static double getWingmanExplosionRadius() {
        return WINGMAN_EXPLOSION_RADIUS.get();
    }

    public static boolean isInterceptorModuleItem(Item item) {
        return WINGMAN_INTERCEPTOR_MODULE_ITEM_SET.contains(item);
    }

    public static int getInterceptorChargeDurationTicks() {
        return WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS.get();
    }

    public static int getInterceptorMaxChargeDurationTicks() {
        return WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS.get();
    }

    public static double getInterceptorChargedSweepBaseRange() {
        return WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE.get();
    }

    public static boolean isEvolutionModuleItem(Item item) {
        return WINGMAN_EVOLUTION_MODULE_ITEM_SET.contains(item);
    }

    public static double getWingmanEvolutionBonusPerLevel() {
        return WINGMAN_EVOLUTION_BONUS_PER_LEVEL.get();
    }

    public static boolean isNanoRegenModuleItem(Item item) {
        return WINGMAN_NANO_REGEN_MODULE_ITEM_SET.contains(item);
    }

    public static double getWingmanNanoRegenPercent() {
        return WINGMAN_NANO_REGEN_PERCENT.get();
    }

    public static boolean isShockwaveModuleItem(Item item) {
        return WINGMAN_SHOCKWAVE_MODULE_ITEM_SET.contains(item);
    }

    public static double getWingmanShockwaveDamageMultiplier() {
        return WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER.get();
    }

    public static double getWingmanShockwaveSplashLengthMultiplier() {
        return WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER.get();
    }

    // ===== 蜂群配置方法 =====

    public static boolean isSwarmModuleItem(Item item) {
        return SWARM_MODULE_ITEM_SET.contains(item);
    }

    public static double getSwarmBaseHealth() {
        return SWARM_BASE_HEALTH.get();
    }

    public static double getSwarmBaseDamage() {
        return SWARM_BASE_DAMAGE.get();
    }

    public static double getSwarmAttackInterval() {
        return SWARM_ATTACK_INTERVAL.get();
    }

    public static double getSwarmAttackRange() {
        return SWARM_ATTACK_RANGE.get();
    }

    public static double getSwarmSearchRange() {
        return SWARM_SEARCH_RANGE.get();
    }

    public static int getSwarmMaxCount() {
        return SWARM_MAX_COUNT.get();
    }

    public static int getSwarmCountLimit() {
        return SWARM_COUNT_LIMIT.get();
    }

    public static double getSwarmMoveSpeed() {
        return SWARM_MOVE_SPEED.get();
    }

    public static int getSwarmBuildTime() {
        return SWARM_BUILD_TIME.get();
    }

    public static double getSwarmTierUpgradeChanceStandard() {
        return SWARM_TIER_UPGRADE_CHANCE_STANDARD.get();
    }

    public static double getSwarmTierUpgradeChanceAdvanced() {
        return SWARM_TIER_UPGRADE_CHANCE_ADVANCED.get();
    }

    public static double getSwarmVulnerabilityValue() {
        return SWARM_VULNERABILITY_VALUE.get();
    }

    public static double getSwarmShieldRepairMultiplier() {
        return SWARM_SHIELD_REPAIR_MULTIPLIER.get();
    }

    public static boolean isNearDeathProtectionItem(Item item) {
        return NEAR_DEATH_PROTECTION_ITEM_SET.contains(item);
    }

    public static boolean isNearDeathExplosionItem(Item item) {
        return NEAR_DEATH_EXPLOSION_ITEM_SET.contains(item);
    }

    public static boolean isSelfDestructItem(Item item) {
        return SELF_DESTRUCT_ITEM_SET.contains(item);
    }

    public static boolean isFurnaceCoreItem(Item item) {
        return FURNACE_CORE_ITEM_SET.contains(item);
    }


    public static boolean isTaskmasterItem(Item item) {
        return TASKMASTER_ITEM_SET.contains(item);
    }

    public static boolean isCommanderItem(Item item) {
        return COMMANDER_ITEM_SET.contains(item);
    }

    public static boolean isAdaptiveArmorItem(Item item) {
        return ADAPTIVE_ARMOR_ITEM_SET.contains(item);
    }

    public static int getAdaptiveArmorDuration() {
        return ADAPTIVE_ARMOR_DURATION.get();
    }

    public static int getAdaptiveArmorMaxLayersPerHit() {
        return ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT.get();
    }

    public static double getAdaptiveArmorLayersPerDamage() {
        return ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE.get();
    }

    public static boolean isAdaptiveArmorShieldEffectItem(Item item) {
        return ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEM_SET.contains(item);
    }

    public static boolean isShieldTransferItem(Item item) {
        return SHIELD_TRANSFER_ITEM_SET.contains(item);
    }

    public static boolean isBarrierItem(Item item) {
        return BARRIER_ITEM_SET.contains(item);
    }

    public static boolean isExplosiveShieldItem(Item item) {
        return EXPLOSIVE_SHIELD_ITEM_SET.contains(item);
    }

    public static boolean isReflectDamageItem(Item item) {
        return REFLECT_DAMAGE_ITEM_SET.contains(item);
    }

    public static boolean isElectricDischargeItem(Item item) {
        return ELECTRIC_DISCHARGE_ITEM_SET.contains(item);
    }

    public static double getElectricDischargeBurnCharge() {
        return ELECTRIC_DISCHARGE_BURN_CHARGE.get();
    }

    public static int getElectricDischargeBurnDuration() {
        return ELECTRIC_DISCHARGE_BURN_DURATION.get();
    }

    public static boolean isAttackCooldownEfficiencyItem(Item item) {
        return ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET.contains(item);
    }

    public static boolean isShieldNaturalRecoveryItem(Item item) {
        return SHIELD_NATURAL_RECOVERY_ITEM_SET.contains(item);
    }

    public static double getNaturalRecoveryShieldRecoveryPerTick() {
        return NATURAL_RECOVERY_SHIELD_RECOVERY_PER_TICK.get();
    }

    public static double getNaturalRecoveryShieldPresentHealthModifier() {
        return NATURAL_RECOVERY_SHIELD_PRESENT_HEALTH_MODIFIER.get();
    }

    public static double getNaturalRecoveryShieldPresentShieldModifier() {
        return NATURAL_RECOVERY_SHIELD_PRESENT_SHIELD_MODIFIER.get();
    }

    public static boolean isBinaryProtocolItem(Item item) {
        return BINARY_PROTOCOL_ITEM_SET.contains(item);
    }

    public static boolean isWeaponizedShieldItem(Item item) {
        return WEAPONIZED_SHIELD_ITEM_SET.contains(item);
    }

    public static boolean isConversionItem(Item item) {
        return CONVERSION_ITEM_SET.contains(item);
    }

    public static boolean isDangerousEntity(String entityId) {
        return DANGEROUS_ENTITY_SET.contains(entityId);
    }

    public static double getAmplificationBaseAmplification() {
        return AMPLIFICATION_BASE_AMPLIFICATION.get();
    }

    public static double getAmplificationThreatAmplification() {
        return AMPLIFICATION_THREAT_AMPLIFICATION.get();
    }

    public static double getAmplificationCheckRadius() {
        return AMPLIFICATION_CHECK_RADIUS.get();
    }

    public static double getAmplificationMaxAmplification() {
        return AMPLIFICATION_MAX_AMPLIFICATION.get();
    }

    public static double getAmplificationMovementSpeedBonus() {
        return AMPLIFICATION_MOVEMENT_SPEED_BONUS.get();
    }

    public static double getAmplificationHealthAmplificationPerPoint() {
        return AMPLIFICATION_HEALTH_AMPLIFICATION_PER_POINT.get();
    }

    public static double getWarpShieldExplosionDamage() {
        return WARP_SHIELD_EXPLOSION_DAMAGE.get();
    }

    public static boolean isArcBarrierItem(Item item) {
        return ARC_BARRIER_ITEM_SET.contains(item);
    }

    public static boolean isReshapingItem(Item item) {
        return RESHAPING_ITEM_SET.contains(item);
    }

    public static boolean isCounterPulseItem(Item item) {
        return COUNTER_PULSE_ITEM_SET.contains(item);
    }

    public static boolean isAssaultItem(Item item) {
        return ASSAULT_ITEM_SET.contains(item);
    }

    public static double getAssaultAttackSpeedPerStack() {
        return ASSAULT_ATTACK_SPEED_PER_STACK.get();
    }

    public static int getAssaultDurationTicks() {
        return ASSAULT_DURATION_TICKS.get();
    }

    public static double getAssaultSelfDamagePerStack() {
        return ASSAULT_SELF_DAMAGE_PER_STACK.get();
    }

    public static double getAssaultMovementSpeedPenalty() {
        return ASSAULT_MOVEMENT_SPEED_PENALTY.get();
    }

    public static double getAssaultOverflowDamageEfficiency() {
        return ASSAULT_OVERFLOW_DAMAGE_EFFICIENCY.get();
    }

    public static boolean isChargedAttackItem(Item item) {
        return CHARGED_ATTACK_ITEM_SET.contains(item);
    }

    public static double getChargedAttackBaseChargeRate() {
        return CHARGED_ATTACK_BASE_CHARGE_RATE.get();
    }

    public static double getChargedAttackSpeedScaleFactor() {
        return CHARGED_ATTACK_SPEED_SCALE_FACTOR.get();
    }

    public static double getChargedAttackDragCoefficient() {
        return CHARGED_ATTACK_DRAG_COEFFICIENT.get();
    }

    public static double getChargedAttackDragThresholdFactor() {
        return CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR.get();
    }

    public static double getChargedAttackMovementSpeedPenalty() {
        return CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY.get();
    }

    public static boolean isJourneyModuleItem(Item item) {
        return JOURNEY_MODULE_ITEM_SET.contains(item);
    }

    /** 物品是否声明为特殊机制（special_mechanics 文件夹声明） */
    public static boolean isSpecialMechanicItem(Item item) {
        return SPECIAL_MECHANIC_ITEM_SET.contains(item);
    }

    public static double getJourneyAttackSpeedPerStack() {
        return JOURNEY_ATTACK_SPEED_PER_STACK.get();
    }

    public static double getJourneyMovementSpeedPerStack() {
        return JOURNEY_MOVEMENT_SPEED_PER_STACK.get();
    }

    public static int getJourneyDurationTicks() {
        return JOURNEY_DURATION_TICKS.get();
    }

    public static int getJourneyMaxStacks() {
        return JOURNEY_MAX_STACKS.get();
    }

    public static int getJourneyDecayIntervalTicks() {
        return JOURNEY_DECAY_INTERVAL_TICKS.get();
    }

    public static int getJourneyDecayPerInterval() {
        return JOURNEY_DECAY_PER_INTERVAL.get();
    }

    public static boolean isChargedShieldItem(Item item) {
        return CHARGED_SHIELD_ITEM_SET.contains(item);
    }

    public static double getChargedShieldChargeRatio() {
        return CHARGED_SHIELD_CHARGE_RATIO.get();
    }

    public static double getChargedShieldMaxBonus() {
        return CHARGED_SHIELD_MAX_BONUS.get();
    }

    public static double getChargedShieldDecayRate() {
        return CHARGED_SHIELD_DECAY_RATE.get();
    }

    public static double getChargedShieldMovementSpeedPenalty() {
        return CHARGED_SHIELD_MOVEMENT_SPEED_PENALTY.get();
    }

    // ===== 幽灵机身辅助方法 =====

    public static boolean isGhostFuselageItem(Item item) {
        return GHOST_FUSELAGE_ITEM_SET.contains(item);
    }

    public static double getGhostFuselageStealthSpeedBonusPerLevel() {
        return GHOST_FUSELAGE_STEALTH_SPEED_BONUS_PER_LEVEL.get();
    }

    public static double getGhostFuselageMaxBonusPerLevel() {
        return GHOST_FUSELAGE_MAX_BONUS_PER_LEVEL.get();
    }

    public static double getGhostFuselageBaseMaxDamageBonus() {
        return GHOST_FUSELAGE_BASE_MAX_DAMAGE_BONUS.get();
    }

    public static double getGhostFuselageMoveSpeedThreshold() {
        return GHOST_FUSELAGE_MOVE_SPEED_THRESHOLD.get();
    }

    public static double getGhostFuselageMoveSpeedReduction() {
        return GHOST_FUSELAGE_MOVE_SPEED_REDUCTION.get();
    }

    public static double getGhostFuselageDecayRate() {
        return GHOST_FUSELAGE_DECAY_RATE.get();
    }

    public static double getGhostFuselageMinDecay() {
        return GHOST_FUSELAGE_MIN_DECAY.get();
    }

    public static int getGhostFuselageFullStealthTicks() {
        return GHOST_FUSELAGE_FULL_STEALTH_TICKS.get();
    }

    public static boolean isGrudgeItem(Item item) {
        return GRUDGE_ITEM_SET.contains(item);
    }

    public static boolean isPrecisionConstructItem(Item item) {
        return PRECISION_CONSTRUCT_ITEM_SET.contains(item);
    }

    public static boolean isAdvancedEngineeringItem(Item item) {
        return ADVANCED_ENGINEERING_ITEM_SET.contains(item);
    }

    public static boolean isPursuitArrayItem(Item item) {
        return PURSUIT_ARRAY_ITEM_SET.contains(item);
    }

    public static boolean isFormationArrayItem(Item item) {
        return FORMATION_ARRAY_ITEM_SET.contains(item);
    }

    public static boolean isGuardArrayItem(Item item) {
        return GUARD_ARRAY_ITEM_SET.contains(item);
    }

    public static double getGrudgeConversionRatio() {
        return GRUDGE_CONVERSION_RATIO.get();
    }

    public static double getGrudgeFadeBase() {
        return GRUDGE_FADE_BASE.get();
    }

    public static double getGrudgeFadePercent() {
        return GRUDGE_FADE_PERCENT.get();
    }

    public static double getGrudgeMovementSpeedPenalty() {
        return GRUDGE_MOVEMENT_SPEED_PENALTY.get();
    }

    public static int getQuickEquipUpgradePointsCost() {
        return QUICK_EQUIP_UPGRADE_POINTS_COST.get();
    }

    public static boolean isRandomBuildEnabled() {
        return RANDOM_BUILD_ENABLED.get();
    }

    /** 从随机池获取物品时的升级点消耗倍数 */
    public static int getRandomBuildUpgradePointsMultiplier() {
        return RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER.get();
    }

    public static boolean isShowUpgradeReminderHud() {
        return SHOW_UPGRADE_REMINDER_HUD.get();
    }

    /** 是否启用代币机制（随机构建消耗背包代币而非升级点） */
    public static boolean isRandomBuildTokenEnabled() {
        return RANDOM_BUILD_TOKEN_ENABLED.get();
    }

    /** 代币物品 ID */
    public static String getRandomBuildTokenItemId() {
        return RANDOM_BUILD_TOKEN_ITEM.get();
    }

    public static boolean isHardcoreModeEnabled() {
        return HARDCORE_MODE_ENABLED.get();
    }

    public static String getShieldHitSound() {
        return SHIELD_HIT_SOUND.get();
    }
}
