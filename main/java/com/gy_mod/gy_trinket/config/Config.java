package com.gy_mod.gy_trinket.config;

import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeType;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;

/**
 * 模组配置类
 * 负责管理模组的配置项和属性注册
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===== 合成禁用 (crafting_disable) =====
    /** 合成禁用模式：0=不禁用，1=禁用本模组命名空间下注册了实际效果的物品合成，2=禁用所有注册了实际效果的物品合成 */
    public static final ForgeConfigSpec.IntValue DISABLE_CRAFTING_MODE;

    // ===== 33.5 随机构建系统 (random_build) =====
    public static final ForgeConfigSpec.BooleanValue RANDOM_BUILD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHOW_UPGRADE_REMINDER_HUD;
    /** 从随机池获取物品时的升级点消耗倍数（升级点惩罚，默认 5 倍） */
    public static final ForgeConfigSpec.IntValue RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER;
    /** 代币机制：启用后随机池兑换消耗背包代币而非升级点 */
    public static final ForgeConfigSpec.BooleanValue RANDOM_BUILD_TOKEN_ENABLED;
    /** 代币物品 ID（可替换为其他模组的物品） */
    public static final ForgeConfigSpec.ConfigValue<String> RANDOM_BUILD_TOKEN_ITEM;
    /** 代币物品自定义描述（附属配置项，非空时显示在代币物品描述上） */
    public static final ForgeConfigSpec.ConfigValue<String> RANDOM_BUILD_TOKEN_DESC;

    // ===== 1. 光环护盾 (aura_shield) =====

    // ===== 2. 虹吸护盾 (siphon_shield) =====

    // ===== 3. 反射护盾类型 (reflect_shield) =====

    // ===== 4. 增幅护盾 (amplification_shield) =====

    // ===== 5. 跃传护盾 (warp_shield) =====

    // ===== 6. 屏障 (barrier) =====

    // ===== 7. 反射护盾 (reflect_damage) =====

    // ===== 8. 易爆护盾 (explosive_shield) =====

    // ===== 9. 电能释放 (electric_discharge) =====
    public static final ForgeConfigSpec.IntValue ELECTRIC_DISCHARGE_BURN_DURATION;

    // ===== 10. 武器化护盾 (weaponized_shield) =====

    // ===== 11. 镀层 (coating_system) =====
    public static final ForgeConfigSpec.DoubleValue COATING_REDUCTION_PER_LAYER;

    // ===== 12. 适应性装甲 (adaptive_armor) =====
    public static final ForgeConfigSpec.IntValue ADAPTIVE_ARMOR_DURATION;
    public static final ForgeConfigSpec.IntValue ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT;
    public static final ForgeConfigSpec.DoubleValue ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE;

    // ===== 13. 再生护盾 (shield_natural_recovery) =====
    // 护盾再生速度 / 有无护盾值时的生命与护盾再生效率已迁移到物品定义：
    // item_definitions 的 values.shield_natural_recovery_items（机制参数，支持单/叠）。
    
    // ===== 15. 转化 (conversion) =====

    // ===== 17. 强袭 (assault) =====
    public static final ForgeConfigSpec.IntValue ASSAULT_DURATION_TICKS;

    // ===== 17.5 征途 (journey) =====
    public static final ForgeConfigSpec.DoubleValue JOURNEY_ATTACK_SPEED_PER_STACK;
    public static final ForgeConfigSpec.DoubleValue JOURNEY_MOVEMENT_SPEED_PER_STACK;
    public static final ForgeConfigSpec.IntValue JOURNEY_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue JOURNEY_MAX_STACKS;
    public static final ForgeConfigSpec.IntValue JOURNEY_DECAY_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue JOURNEY_DECAY_PER_INTERVAL;

    // ===== 24.5.2 次级攻击伤害合并 (secondary_damage_merge) =====
    public static final ForgeConfigSpec.BooleanValue SECONDARY_DAMAGE_MERGE_ENABLED;
    public static final ForgeConfigSpec.IntValue SECONDARY_DAMAGE_MERGE_WINDOW_TICKS;

    /** 次级爆炸（爆炸半径模块特殊机制）：爆炸伤害占弹射物伤害的比例 */
    /** 次级爆炸（爆炸半径模块特殊机制）：爆炸半径基础值 */
    /** 次级爆炸（爆炸半径模块特殊机制）：每点爆炸伤害增加的爆炸半径 */

    // ===== 18. 充能攻击 (charged_attack) =====
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_BASE_CHARGE_RATE;
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_SPEED_SCALE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_COEFFICIENT;
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_DRAG_THRESHOLD_FACTOR;
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_MOVEMENT_SPEED_PENALTY;
    /** 充能物品白名单（长按右键充能）：物品注册名=攻击速度修正值 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHARGED_ATTACK_ITEM_USE_WHITELIST;
    /** 充能物品白名单未注册物品的默认攻击速度修正值 */
    public static final ForgeConfigSpec.DoubleValue CHARGED_ATTACK_ITEM_USE_DEFAULT_SPEED_MODIFIER;

    // ===== 18.5 弹射物黑名单 (projectile_blacklist) =====
    /** 弹射物黑名单：不参与充能攻击增幅与点射复制的实体类型注册名 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_BLACKLIST;

    /** 弹射物来源追溯：弹射物加入世界时调用栈命中这些 Java 包名前缀则视为饰品/被动生成，不触发点射 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_SOURCE_TRACE_PACKAGES;

    // ===== 19. 精密构造 (precision_construct) =====

    // ===== 20. 护盾移植 (shield_transfer) =====

    // ===== 21. 追击阵列/突击无人机 (pursuit_array) =====
    public static final ForgeConfigSpec.IntValue DRONE_MAX_COUNT;
    public static final ForgeConfigSpec.DoubleValue DRONE_FOLLOW_RANGE;
    /** 无人机正常攻击间隔（秒）：物品级 attack_interval 未定义时的全局默认 */
    /** 无人机基础攻击范围（格）：物品级 attack_range 未定义时使用 */
    /** 无人机基础索敌范围（格）：物品级 target_range 未定义时使用 */

    // ===== 21.5 无人机斩杀机制 (drone_execute) =====
    public static final ForgeConfigSpec.BooleanValue DRONE_EXECUTE_ENABLED;

    // ===== 22. 宽限协议 (near_death_protection) =====

    // ===== 23. 高等工程 (advanced_engineering) =====

    // ===== 24. 最后指令 (near_death_explosion) =====
    public static final ForgeConfigSpec.IntValue NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION;
    public static final ForgeConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_COEFFICIENT;
    public static final ForgeConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_RADIUS;
    public static final ForgeConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_INITIAL_SPEED;
    public static final ForgeConfigSpec.DoubleValue NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION;

    // ===== 25. 列队阵列 (formation_array) =====
    public static final ForgeConfigSpec.IntValue FORMATION_ATTACK_PASS_DELAY;

    // ===== 26. 指挥官 (commander) =====
    public static final ForgeConfigSpec.IntValue COMMANDER_MAX_COUNT;
    public static final ForgeConfigSpec.IntValue COMMANDER_APPOINT_DELAY;
    public static final ForgeConfigSpec.DoubleValue COMMANDER_VULNERABILITY;

    // ===== 僚机 (wingman) =====
    public static final ForgeConfigSpec.IntValue WINGMAN_MAX_COUNT;
    public static final ForgeConfigSpec.IntValue WINGMAN_EXPLOSIVE_COUNT;

    // ===== 蜂群 =====
    public static final ForgeConfigSpec.IntValue SWARM_MAX_COUNT;
    public static final ForgeConfigSpec.IntValue SWARM_COUNT_LIMIT;
    public static final ForgeConfigSpec.DoubleValue SWARM_MOVE_SPEED;
    public static final ForgeConfigSpec.DoubleValue SWARM_VULNERABILITY_VALUE;
    public static final ForgeConfigSpec.DoubleValue SWARM_SHIELD_REPAIR_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_STANDARD;
    public static final ForgeConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_ADVANCED;
    public static final ForgeConfigSpec.DoubleValue WINGMAN_EXPLOSION_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue WINGMAN_EXPLOSION_RADIUS;
    public static final ForgeConfigSpec.IntValue WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE;
    public static final ForgeConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER;

    // ===== 27. 守卫阵列/防御无人机 (guard_array) =====

    // ===== 28. 弧形屏障 (arc_barrier) =====
    public static final ForgeConfigSpec.DoubleValue ARC_BARRIER_POSITION_DEVIATION_THRESHOLD;

    // ===== 29. 反制脉冲 (counter_pulse) =====
    public static final ForgeConfigSpec.IntValue COUNTER_PULSE_CHARGE_INTERVAL;
    public static final ForgeConfigSpec.IntValue COUNTER_PULSE_MAX_CHARGE_LEVEL;

    // ===== 30. 重塑 (reshaping) =====
    public static final ForgeConfigSpec.DoubleValue RESHAPING_HEAL_RATE;
    public static final ForgeConfigSpec.DoubleValue RESHAPING_BASE_DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.IntValue RESHAPING_DAMAGE_REDUCTION_DURATION;

    // ===== 30b. 自毁装置 (self_destruct) =====
    public static final ForgeConfigSpec.DoubleValue SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH;

    // ===== 31. 充能护盾 (charged_shield) =====

    // ===== 35. 积怨 (grudge) =====

    // ===== 幽灵机身 (ghost_fuselage) =====
    public static final ForgeConfigSpec.IntValue GHOST_FUSELAGE_FULL_STEALTH_TICKS;

    // ===== 32. 升级系统 (upgrade_system) =====
    public static final ForgeConfigSpec.ConfigValue<Boolean> UPGRADE_SYSTEM_ENABLED;

    // ===== 33. 快速装备 (quick_equip) =====
    public static final ForgeConfigSpec.IntValue QUICK_EQUIP_UPGRADE_POINTS_COST;

    // ===== 33b. 敌对目标 (hostile_target) =====
    public static final ForgeConfigSpec.IntValue HOSTILE_TARGET_MARK_DURATION;

    // ===== 护盾音效 (shield_sound) =====
    public static final ForgeConfigSpec.ConfigValue<String> SHIELD_HIT_SOUND;

    // ===== 34. 其他通用设置 =====
    public static final ForgeConfigSpec.ConfigValue<Boolean> HARDCORE_MODE_ENABLED;
    public static final ForgeConfigSpec.IntValue SHIELD_BLOCK_INVULNERABLE_TICKS;
    public static final ForgeConfigSpec.DoubleValue IGNITE_DEFAULT_DAMAGE;
    public static final ForgeConfigSpec.IntValue IGNITE_DEFAULT_DURATION;
    public static final ForgeConfigSpec.DoubleValue NATURAL_RECOVERY_PLAYER_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<Boolean> NATURAL_RECOVERY_PLAYER_HEALTH_ENABLED;
    public static final ForgeConfigSpec.DoubleValue NATURAL_RECOVERY_SHIELD;
    public static final ForgeConfigSpec.DoubleValue NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY;

    public static final ForgeConfigSpec SPEC;

    private static boolean initialized = false;

    static {
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
            "启用后：玩家面板经验条上方出现7个随机池，",
            "可用升级点（× 配置的消耗倍数）兑换随机物品装备到光点核心。"
        ).define("enabled", true);

        SHOW_UPGRADE_REMINDER_HUD = BUILDER.comment(
            "是否显示升级提醒 HUD",
            "当玩家有未使用的升级点时，在物品栏上方显示按下G键的提示",
            "光点核心已满时不显示，默认 true"
        ).define("showUpgradeReminderHud", true);

        RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER = BUILDER.comment(
            "从随机池获取物品时的升级点消耗倍数（升级点惩罚）",
            "从随机池获取 1 件物品消耗的升级点 = 基础 1 点 × 该倍数",
            "默认 8（即每次获取消耗 8 点升级点）；范围 1~100",
            "代币机制启用时代币消耗不受该倍数影响（每次仍消耗 1 个代币）"
        ).defineInRange("upgradePointsMultiplier", 8, 1, 100);

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

        RANDOM_BUILD_TOKEN_DESC = BUILDER.comment(
            "代币物品自定义描述（附属配置项，可随意填写）",
            "非空时以特殊机制描述的样式显示在代币物品的悬浮描述上",
            "支持 \\n 换行；留空则不显示"
        ).define("tokenItemDescription", "");

        BUILDER.pop();

        // ===== 1. 光环护盾 =====

        // ===== 2. 虹吸护盾 =====

        // ===== 3. 反射护盾类型 =====

        // ===== 4. 增幅护盾 =====

        // ===== 5. 跃传护盾 =====

        // ===== 6. 屏障 =====

        // ===== 7. 反射护盾 =====

        // ===== 8. 易爆护盾 =====

        // ===== 9. 电能释放 =====
        BUILDER.comment("电能释放系统配置").push("electric_discharge");


        ELECTRIC_DISCHARGE_BURN_DURATION = BUILDER.comment(
            "电能释放灼烧持续时间（刻）",
            "每次闪电命中后灼烧效果的持续时间",
            "范围：1 ~ 200"
        ).defineInRange("electricDischargeBurnDuration", 10, 1, 200);

        BUILDER.pop();

        // ===== 10. 武器化护盾 =====

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
        // 护盾再生速度、有无护盾值时的玩家生命/护盾再生效率已迁移到物品定义
        // （item_definitions 的 values.shield_natural_recovery_items，走默认单/叠机制）。

        // ===== 15. 转化 =====

        // ===== 17. 强袭 =====
        BUILDER.comment("强袭系统配置").push("assault");


        ASSAULT_DURATION_TICKS = BUILDER.comment(
            "强袭层数持续时间（刻）",
            "默认40tick（2秒），重复叠加刷新时间",
            "范围：10 ~ 200"
        ).defineInRange("durationTicks", 40, 10, 200);




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
            "默认100tick（5秒）"
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
            "实际充能速率 = 基础速率 * (攻击速度加成)",
            "默认0.5",
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
            "默认末影珍珠（点射复制会导致多次瞬移，语义混乱且不可控）与泰拉饰品星星斗篷落星（饰品受击被动生成，非玩家主动射击）",
            "示例：minecraft:ender_pearl"
        ).defineListAllowEmpty("projectileBlacklist",
            List.of("minecraft:ender_pearl", "terra_curio:star_cloak"),
            s -> true
        );

        PROJECTILE_SOURCE_TRACE_PACKAGES = BUILDER.comment(
            "弹射物来源追溯（Java 包名前缀）",
            "弹射物加入世界时，若调用栈来自这些包（饰品/被动技能生成弹射物，如泰拉饰品星星斗篷受击落星、蜜蜂斗篷放蜂），",
            "则不触发弹射物点射：不复制弹射物、不挂攻击冷却禁用物品",
            "该机制按生成来源整体拦截，自动覆盖目标模组新增的饰品弹射物，无需逐个加入黑名单",
            "注意：此处填写目标模组的 Java 包名前缀（非模组 id），主动技能弹射物同样会被拦截，",
            "如需保留主动技能连击请清空对应条目并改用黑名单逐个排除",
            "默认追溯泰拉饰品（org.confluence.terra_curio）与 lensouls（com.plumejade.lensouls），留空列表则禁用追溯"
        ).defineListAllowEmpty("projectileSourceTracePackages",
            List.of("org.confluence.terra_curio", "com.plumejade.lensouls"),
            s -> true
        );

        BUILDER.pop();

        // ===== 19. 精密构造 =====

        // ===== 20. 护盾移植 =====

        // ===== 21. 追击阵列/突击无人机 =====
        BUILDER.comment("追击阵列/突击无人机配置").push("pursuit_array");



        DRONE_MAX_COUNT = BUILDER.comment(
            "无人机最大数量"
        ).defineInRange("droneMaxCount", 3, 1, 20);

        DRONE_FOLLOW_RANGE = BUILDER.comment(
            "无人机跟随范围（格）"
        ).defineInRange("droneFollowRange", 16.0, 4.0, 64.0);




        BUILDER.pop();

        // ===== 21.5 斩杀机制 =====
        BUILDER.comment("斩杀机制配置").push("drone_execute");

        DRONE_EXECUTE_ENABLED = BUILDER.comment(
            "是否启用斩杀机制",
            "启用时：当目标生命值低于斩杀伤害，伤害归属玩家（爆炸伤害源）",
            "禁用时：伤害量不变，但伤害源不归属玩家（不会触发玩家的击杀效果）"
        ).define("droneExecuteEnabled", true);

        BUILDER.pop();

        // ===== 22. 宽限协议 =====

        // ===== 23. 高等工程 =====

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

        BUILDER.pop();

        // ===== 25. 列队阵列 =====
        BUILDER.comment("列队阵列配置").push("formation_array");

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

        // ===== 僚机 =====
        BUILDER.comment("僚机构造体配置").push("wingman");


        WINGMAN_MAX_COUNT = BUILDER.comment(
            "僚机最大数量"
        ).defineInRange("wingmanMaxCount", 1, 1, 20);



        WINGMAN_EXPLOSIVE_COUNT = BUILDER.comment(
            "每次攻击发射的爆破弹数量"
        ).defineInRange("wingmanExplosiveCount", 3, 1, 20);


        WINGMAN_EXPLOSION_DAMAGE = BUILDER.comment(
            "爆破弹爆炸伤害"
        ).defineInRange("wingmanExplosionDamage", 0.5, 0.01, 100.0);

        WINGMAN_EXPLOSION_RADIUS = BUILDER.comment(
            "爆破弹爆炸半径（格）"
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



        WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER = BUILDER.comment(
            "震撼弹模块：爆破弹爆炸伤害倍率",
            "默认：2.0（+100%），范围：1.0 ~ 10.0"
        ).defineInRange("wingmanShockwaveDamageMultiplier", 2.0, 1.0, 10.0);

        WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER = BUILDER.comment(
            "震撼弹模块：爆破弹溅射长度倍率",
            "默认：1.5（+50%），范围：1.0 ~ 10.0"
        ).defineInRange("wingmanShockwaveSplashLengthMultiplier", 1.5, 1.0, 10.0);

        BUILDER.pop();

        // ===== 蜂群 =====
        BUILDER.comment("蜂群构造体配置").push("swarm");



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
            "蜂群移动速度"
        ).defineInRange("swarmMoveSpeed", 0.25, 0.01, 10.0);




        SWARM_VULNERABILITY_VALUE = BUILDER.comment(
            "蜂群电弧施加的易伤值"
        ).defineInRange("swarmVulnerabilityValue", 0.0025, 0.0, 10.0);

        SWARM_SHIELD_REPAIR_MULTIPLIER = BUILDER.comment(
            "蜂群护盾修复转化倍率"
        ).defineInRange("swarmShieldRepairMultiplier", 3.0, 0.0, 100.0);

        SWARM_TIER_UPGRADE_CHANCE_STANDARD = BUILDER.comment(
            "蜂群升阶为标准的概率"
        ).defineInRange("swarmTierUpgradeChanceStandard", 0.1, 0.0, 1.0);

        SWARM_TIER_UPGRADE_CHANCE_ADVANCED = BUILDER.comment(
            "蜂群升阶为高阶的概率"
        ).defineInRange("swarmTierUpgradeChanceAdvanced", 0.03, 0.0, 1.0);

        BUILDER.pop();

        // ===== 27. 守卫阵列/防御无人机 =====

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

        // ===== 自毁装置 =====
        BUILDER.comment("自毁装置系统配置").push("self_destruct");



        SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH = BUILDER.comment(
            "每点最大生命值增加的自毁爆炸伤害"
        ).defineInRange("selfDestructDamagePerMaxHealth", 1.0, 0.0, 100.0);

        SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH = BUILDER.comment(
            "每点最大生命值增加的自毁爆炸半径（格）"
        ).defineInRange("selfDestructRadiusPerMaxHealth", 0.3, 0.0, 10.0);

        BUILDER.pop();

        // ===== 35. 积怨 =====

        // ===== 幽灵机身 =====
        BUILDER.comment("幽灵机身配置").push("ghost_fuselage");








        GHOST_FUSELAGE_FULL_STEALTH_TICKS = BUILDER.comment(
            "达到完全隐身（80%进度）所需的tick数",
            "默认40（即2秒）",
            "范围：1 ~ 600（0.05秒 ~ 30秒）"
        ).defineInRange("fullStealthTicks", 40, 1, 600);

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

        // ===== 敌对目标系统 =====
        BUILDER.comment("敌对目标系统配置").push("hostile_target");

        HOSTILE_TARGET_MARK_DURATION = BUILDER.comment(
            "玩家攻击标记持续时间（tick）",
            "被玩家攻击过的实体会被标记为威胁，持续此时间后标记失效",
            "20 tick = 1秒，默认100 tick = 5秒"
        ).defineInRange("markDuration", 100, 1, 10000);

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

        // ===== 34. 其他通用设置 =====
        BUILDER.comment("困难模式配置").push("hardcore_mode");

        HARDCORE_MODE_ENABLED = BUILDER.comment(
            "困难模式",
            "启用后，当玩家死亡时，将移除玩家光点核心内所有物品",
            "默认不启用"
        ).define("hardcoreMode", false);

        BUILDER.pop();

        SHIELD_BLOCK_INVULNERABLE_TICKS = BUILDER.comment(
            "护盾格挡时施加的无敌状态持续时间（刻）",
            "当护盾完全吸收伤害时，被攻击者获得短暂无敌帧",
            "默认5刻（0.25秒）"
        ).defineInRange("blockInvulnerableTicks", 5, 0, 100);

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
            "护盾基础恢复速度（%/秒，0为禁用；默认 0 = 初始无再生能力）",
            "每个实例按自身最大护盾值 ×（该值 ÷ 5）恢复；池量为 0 的实例不恢复",
            "实际恢复量还会乘恢复效率属性、攻击冷却惩罚与护盾再生效率；装备再生护盾模块时与其物品级 regen_per_second 叠加",
            "默认 0.0 = 每秒恢复最大护盾的 0%",
            "范围：0.0 ~ 10.0"
        ).defineInRange("naturalRecoveryShield", 0.0, 0.0, 10.0);
        NATURAL_RECOVERY_ATTACK_COOLDOWN_PENALTY = BUILDER.comment("攻击冷却期间恢复惩罚系数（0-1，越低恢复越少）").defineInRange("naturalRecoveryAttackCooldownPenalty", 0.8, 0.0, 1.0);

        BUILDER.pop();

        // ===== 24.5.2 次级攻击伤害合并 =====
        BUILDER.comment("次级攻击伤害合并系统配置").push("secondary_damage_merge");

        SECONDARY_DAMAGE_MERGE_ENABLED = BUILDER.comment(
            "是否启用次级攻击伤害合并",
            "启用后，无人机子弹/僚机爆破弹/能量波爆炸/模拟爆炸的次级攻击伤害，",
            "对同一目标同类型在配置的时间窗口内累积合并，时间结束后一次性施加，降低受击频率",
            "默认true"
        ).define("secondaryDamageMergeEnabled", true);

        SECONDARY_DAMAGE_MERGE_WINDOW_TICKS = BUILDER.comment(
            "次级攻击伤害合并窗口（刻）",
            "同类型伤害在窗口内累积，窗口结束（到期）后一次性施加",
            "默认10tick（0.5秒）",
            "范围：1 ~ 100"
        ).defineInRange("secondaryDamageMergeWindowTicks", 10, 1, 100);

        BUILDER.pop();

        // ===== 24.5.3 弹射物次级爆炸（爆炸半径模块特殊机制） =====

        SPEC = BUILDER.build();
    }

    private static final Map<String, Boolean> SHIELD_TYPE_COMPATIBILITY = new HashMap<>();
    private static final Map<Item, List<String>> ITEM_SHIELD_TYPES = new HashMap<>();
    private static final Map<String, Set<Item>> MODULE_ITEM_SETS = new HashMap<>();
    private static final Set<String> DANGEROUS_ENTITY_SET = new HashSet<>();
    /** 充能物品白名单缓存（长按右键充能）：物品 -> 攻击速度修正值 */
    private static final Map<Item, Double> ITEM_USE_CHARGE_WHITELIST = new HashMap<>();
    /** 弹射物黑名单缓存：不参与充能攻击增幅与点射复制的实体类型 */
    private static final Set<EntityType<?>> PROJECTILE_BLACKLIST_CACHE = new HashSet<>();
    /** 弹射物来源追溯包名前缀缓存 */
    private static final Set<String> PROJECTILE_SOURCE_TRACE_CACHE = new HashSet<>();

    public static List<String> getItemShieldTypes(ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            return Collections.emptyList();
        }
        return ITEM_SHIELD_TYPES.getOrDefault(item, Collections.emptyList());
    }

    public static boolean isShieldTypeCompatible(String typeName) {
        return SHIELD_TYPE_COMPATIBILITY.getOrDefault(typeName, true);
    }

    /**
     * 解析禁用类别为实际物品 id 集合
     * 当前支持的类别：
     *   shields -- 注册了护盾类型的物品（基础护盾及带"+"强化护盾）
     */
    public static Set<String> resolveDisableCategory(String category) {
        Set<String> result = new HashSet<>();
        if ("shields".equals(category)) {
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
                if (rl != null && rl.getNamespace().equals(gytrinket.MODID)
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
            return DefsManager.getCategoryFinalModules("construct");
        }
        return Collections.emptySet();
    }

    public static boolean isBodyItem(Item item) {
        return isModuleItem("body", item);
    }

    public static boolean isTaskmasterItem(Item item) {
        return isModuleItem("taskmaster", item);
    }

    public static boolean isFurnaceCoreItem(Item item) {
        return isModuleItem("furnace_core", item);
    }

    /**
     * 应用 datapack 定义类数据（由 DefsManager 在数据包重载后调用）
     * 填充物品集合、护盾类型、属性定义、危险实体等，并触发依赖子系统重载
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
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null && item != Items.AIR) {
                ITEM_SHIELD_TYPES.put(item, types);
            }
        });

        // 物品集合（模块名与 loadItemSetFromDefs 使用的 key 一致）
        loadItemSetFromDefs("body", "body_items");
        loadItemSetFromDefs("adaptive_armor", "adaptive_armor_items");
        loadItemSetFromDefs("adaptive_armor_shield_effect", "adaptive_armor_shield_effect_items");
        loadItemSetFromDefs("electric_discharge", "electric_discharge_items");
        loadItemSetFromDefs("attack_cooldown_efficiency", "attack_cooldown_efficiency_items");
        loadItemSetFromDefs("barrier", "barrier_items");
        loadItemSetFromDefs("shield_natural_recovery", "shield_natural_recovery_items");
        loadItemSetFromDefs("reflect_damage", "reflect_damage_items");
        loadItemSetFromDefs("explosive_shield", "explosive_shield_items");
        loadItemSetFromDefs("shield_transfer", "shield_transfer_items");
        loadItemSetFromDefs("binary_protocol", "binary_protocol_items");
        loadItemSetFromDefs("weaponized_shield", "weaponized_shield_items");
        loadItemSetFromDefs("near_death_protection", "near_death_protection_items");
        loadItemSetFromDefs("near_death_explosion", "near_death_explosion_items");
        loadItemSetFromDefs("self_destruct", "self_destruct_items");
        loadItemSetFromDefs("taskmaster", "taskmaster_items");
        loadItemSetFromDefs("furnace_core", "furnace_core_items");
        loadItemSetFromDefs("drone_module", "drone_module_items");
        loadItemSetFromDefs("assault_drone_module", "assault_drone_module_items");
        loadItemSetFromDefs("commander", "commander_required_items");
        loadItemSetFromDefs("defense_drone_module", "defense_drone_module_items");
        loadItemSetFromDefs("wingman_module", "wingman_module_items");
        loadItemSetFromDefs("interceptor_module", "wingman_interceptor_module_items");
        loadItemSetFromDefs("evolution_module", "wingman_evolution_module_items");
        loadItemSetFromDefs("nano_regen_module", "wingman_nano_regen_module_items");
        loadItemSetFromDefs("shockwave_module", "wingman_shockwave_module_items");
        loadItemSetFromDefs("swarm_module", "swarm_module_items");
        loadItemSetFromDefs("arc_barrier", "arc_barrier_items");
        loadItemSetFromDefs("reshaping", "reshaping_items");
        loadItemSetFromDefs("counter_pulse", "counter_pulse_items");
        loadItemSetFromDefs("assault", "assault_items");
        loadItemSetFromDefs("charged_attack", "charged_attack_items");
        loadItemSetFromDefs("charged_shield", "charged_shield_items");
        loadItemSetFromDefs("grudge", "grudge_items");
        loadItemSetFromDefs("precision_construct", "precision_construct_items");
        loadItemSetFromDefs("advanced_engineering", "advanced_engineering_items");
        loadItemSetFromDefs("pursuit_array", "pursuit_array_required_items");
        loadItemSetFromDefs("formation_array", "formation_array_required_items");
        loadItemSetFromDefs("guard_array", "guard_array_required_items");
        loadItemSetFromDefs("ghost_fuselage", "ghost_fuselage_items");
        loadItemSetFromDefs("conversion", "conversion_items");
        loadItemSetFromDefs("journey_module", "journey_module_items");

        // 特殊机制物品集合（数据驱动，配置系统对 special_mechanics 的增删会实时反映到快速装备/阵列判定）
        SPECIAL_MECHANIC_ITEM_SET.clear();
        for (String itemId : DefsManager.getSpecialMechanicItems()) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null && item != Items.AIR) {
                SPECIAL_MECHANIC_ITEM_SET.add(item);
            }
        }

        // 危险实体
        DANGEROUS_ENTITY_SET.clear();
        DANGEROUS_ENTITY_SET.addAll(DefsManager.getEntitySet("dangerous_entities"));

        // 依赖定义数据的子系统重载
        DisableSystem.loadConfig();
        UpgradeManager.loadConfig();
        ShieldTypeManager.init();

    }

    /** 从 datapack 物品集合填充指定模块的物品集合（与 loadItemSetFromDefs 的模块名一致） */
    private static void loadItemSetFromDefs(String moduleName, String setName) {
        Set<Item> itemSet = MODULE_ITEM_SETS.computeIfAbsent(moduleName, k -> new HashSet<>());
        itemSet.clear();
        for (String itemId : DefsManager.getItemSet(setName)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null && item != Items.AIR) {
                itemSet.add(item);
            }
        }
    }

    /**
     * 按配置名统一取值（供数据驱动的 tooltip 参数引用）
     * 返回 int 或 double，保留原始类型以匹配语言文件的格式符（%d / %.1f / %s）
     */
    public static Object getValue(String name) {
        return switch (name) {
            case "nearDeathExplosionInvincibleDuration" -> NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get();
            case "nearDeathExplosionCoefficient" -> NEAR_DEATH_EXPLOSION_COEFFICIENT.get();
            case "nearDeathExplosionRadius" -> NEAR_DEATH_EXPLOSION_RADIUS.get();
            case "coatingReductionPerLayer" -> getCoatingReductionPerLayer();
            case "arcBarrierPositionDeviationThreshold" -> ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get();
            case "reshapingHealRate" -> RESHAPING_HEAL_RATE.get();
            case "reshapingBaseDamageReduction" -> RESHAPING_BASE_DAMAGE_REDUCTION.get();
            case "reshapingDamageReductionDuration" -> RESHAPING_DAMAGE_REDUCTION_DURATION.get();
            case "counterPulseChargeInterval" -> COUNTER_PULSE_CHARGE_INTERVAL.get();
            case "counterPulseMaxChargeLevel" -> COUNTER_PULSE_MAX_CHARGE_LEVEL.get();
            case "commanderMaxCount" -> COMMANDER_MAX_COUNT.get();
            case "commanderAppointDelay" -> COMMANDER_APPOINT_DELAY.get();
            case "wingmanShockwaveDamageMultiplier" -> getWingmanShockwaveDamageMultiplier();
            case "wingmanShockwaveSplashLengthMultiplier" -> getWingmanShockwaveSplashLengthMultiplier();
            case "journeyAttackSpeedPerStack" -> getJourneyAttackSpeedPerStack();
            case "journeyMovementSpeedPerStack" -> getJourneyMovementSpeedPerStack();
            case "journeyDurationTicks" -> getJourneyDurationTicks();
            case "journeyMaxStacks" -> getJourneyMaxStacks();
            case "ghostFuselageFullStealthTicks" -> getGhostFuselageFullStealthTicks();
            case "electricDischargeBurnDuration" -> getElectricDischargeBurnDuration();
            default -> 0;
        };
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
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) {
                gytrinket.LOGGER.warn("充能物品白名单中的物品未注册：{}", parts[0]);
                continue;
            }
            try {
                ITEM_USE_CHARGE_WHITELIST.put(item, Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException e) {
                gytrinket.LOGGER.warn("无效的攻击速度修正值：{} for {}", parts[1], parts[0]);
            }
        }
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
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
            if (entityType == null) {
                gytrinket.LOGGER.warn("弹射物黑名单中的实体类型未注册：{}", trimmed);
                continue;
            }
            PROJECTILE_BLACKLIST_CACHE.add(entityType);
        }
    }

    /**
     * 弹射物是否在黑名单中（不参与充能攻击增幅与点射复制）
     */
    public static boolean isProjectileBlacklisted(Entity entity) {
        return PROJECTILE_BLACKLIST_CACHE.contains(entity.getType());
    }

    /**
     * 解析弹射物来源追溯包名前缀配置
     * 弹射物加入世界时调用栈命中这些前缀则视为饰品/被动生成，不触发点射
     */
    public static void loadProjectileSourceTrace() {
        PROJECTILE_SOURCE_TRACE_CACHE.clear();
        for (String entry : PROJECTILE_SOURCE_TRACE_PACKAGES.get()) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            PROJECTILE_SOURCE_TRACE_CACHE.add(trimmed);
        }
    }

    /**
     * 弹射物来源追溯：弹射物加入世界时，调用栈是否命中被追溯的包名前缀
     * <p>
     * 用于识别饰品/被动技能生成的弹射物（如泰拉饰品星星斗篷受击落星）：
     * 此类弹射物 owner 为玩家但并非玩家主动射击，第三方模组也不会打来源标记，
     * 只能以调用栈追溯生成来源。命中即不触发弹射物点射（不复制、不挂冷却禁用物品）。
     * <p>
     * 性能：仅在弹射物点射链路内调用（弹射物归属玩家且连击段数 > 0），栈遍历开销可忽略。
     */
    public static boolean isSpawnedByTracedPackage() {
        if (PROJECTILE_SOURCE_TRACE_CACHE.isEmpty()) {
            return false;
        }
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            for (String prefix : PROJECTILE_SOURCE_TRACE_CACHE) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 物品是否为武器类（剑/三叉戟）或工具类武器（镐/斧/铲/锄）
     * 这些物品自带攻击速度修正，长按右键充能不受白名单限制
     */
    public static boolean isWeaponLikeItem(Item item) {
        return item instanceof SwordItem || item instanceof TridentItem || item instanceof DiggerItem;
    }

    /**
     * 武器形态判定（ItemStack 版）：原版三大类武器直接命中；
     * 其余物品检查主手属性表——自带攻速修正即视为武器。
     * <p>
     * 兜底场景：部分模组武器以「普通 Item + 自定义属性表」形态实现
     * （override getDefaultAttributeModifiers 提供攻速修正），instanceof 三大类判不中。
     * 若误判为非武器，攻速口径测量/充能减益会在武器修正之上再叠加默认修正值，
     * 攻速被双重削减为负值（连击冷却被钳制到连击段数×10秒）
     * <p>
     * 局限说明：1.20.1 无 ATTRIBUTE_MODIFIERS 组件，此处读取物品的静态主手属性表，
     * 经 Forge ItemAttributeModifierEvent 动态注入攻速的物品识别不到（维持原判定）
     */
    public static boolean isWeaponLikeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isWeaponLikeItem(stack.getItem())) {
            return true;
        }
        return stack.getItem().getDefaultAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(Attributes.ATTACK_SPEED);
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

    public static boolean isModuleItem(String moduleName, Item item) {
        Set<Item> itemSet = MODULE_ITEM_SETS.get(moduleName);
        return itemSet != null && itemSet.contains(item);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        if (initialized) {
            return;
        }
        initialized = true;
        loadItemUseChargeWhitelist();
        loadProjectileBlacklist();
        loadProjectileSourceTrace();
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

    public static double getCoatingReductionPerLayer() {
        return COATING_REDUCTION_PER_LAYER.get();
    }

    private static final Set<Item> SPECIAL_MECHANIC_ITEM_SET = new HashSet<>();

    /** 是否声明了特殊机制（special_mechanics 数据驱动，配置系统增删会实时反映）。 */
    public static boolean isSpecialMechanicItem(Item item) {
        return SPECIAL_MECHANIC_ITEM_SET.contains(item);
    }

    public static boolean isDroneModuleItem(Item item) {
        return isModuleItem("drone_module", item);
    }

    public static boolean isAssaultDroneModuleItem(Item item) {
        return isModuleItem("assault_drone_module", item);
    }



    public static int getDroneMaxCount() {
        return DRONE_MAX_COUNT.get();
    }

    public static double getDroneFollowRange() {
        return DRONE_FOLLOW_RANGE.get();
    }

    public static boolean isDefenseDroneModuleItem(Item item) {
        return isModuleItem("defense_drone_module", item);
    }

    public static boolean isWingmanModuleItem(Item item) {
        return isModuleItem("wingman_module", item);
    }

    public static boolean isSwarmModuleItem(Item item) {
        return isModuleItem("swarm_module", item);
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




    public static double getSwarmVulnerabilityValue() {
        return SWARM_VULNERABILITY_VALUE.get();
    }

    public static double getSwarmShieldRepairMultiplier() {
        return SWARM_SHIELD_REPAIR_MULTIPLIER.get();
    }

    public static double getSwarmTierUpgradeChanceStandard() {
        return SWARM_TIER_UPGRADE_CHANCE_STANDARD.get();
    }

    public static double getSwarmTierUpgradeChanceAdvanced() {
        return SWARM_TIER_UPGRADE_CHANCE_ADVANCED.get();
    }


    public static int getWingmanMaxCount() {
        return WINGMAN_MAX_COUNT.get();
    }



    public static int getWingmanExplosiveCount() {
        return WINGMAN_EXPLOSIVE_COUNT.get();
    }


    public static double getWingmanExplosionDamage() {
        return WINGMAN_EXPLOSION_DAMAGE.get();
    }

    public static boolean isJourneyModuleItem(Item item) {
        return isModuleItem("journey_module", item);
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

    public static double getWingmanExplosionRadius() {
        return WINGMAN_EXPLOSION_RADIUS.get();
    }

    public static boolean isInterceptorModuleItem(Item item) {
        return isModuleItem("interceptor_module", item);
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
        return isModuleItem("evolution_module", item);
    }

    public static boolean isNanoRegenModuleItem(Item item) {
        return isModuleItem("nano_regen_module", item);
    }


    public static boolean isShockwaveModuleItem(Item item) {
        return isModuleItem("shockwave_module", item);
    }

    public static double getWingmanShockwaveDamageMultiplier() {
        return WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER.get();
    }

    public static double getWingmanShockwaveSplashLengthMultiplier() {
        return WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER.get();
    }


    public static boolean isNearDeathProtectionItem(Item item) {
        return isModuleItem("near_death_protection", item);
    }

    public static boolean isNearDeathExplosionItem(Item item) {
        return isModuleItem("near_death_explosion", item);
    }

    public static boolean isCommanderItem(Item item) {
        return isModuleItem("commander", item);
    }

    public static boolean isSelfDestructItem(Item item) {
        return isModuleItem("self_destruct", item);
    }

    public static boolean isAdaptiveArmorItem(Item item) {
        return isModuleItem("adaptive_armor", item);
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
        return isModuleItem("adaptive_armor_shield_effect", item);
    }

    public static boolean isShieldTransferItem(Item item) {
        return isModuleItem("shield_transfer", item);
    }

    public static boolean isBarrierItem(Item item) {
        return isModuleItem("barrier", item);
    }

    public static boolean isExplosiveShieldItem(Item item) {
        return isModuleItem("explosive_shield", item);
    }

    public static boolean isReflectDamageItem(Item item) {
        return isModuleItem("reflect_damage", item);
    }

    public static boolean isElectricDischargeItem(Item item) {
        return isModuleItem("electric_discharge", item);
    }


    public static int getElectricDischargeBurnDuration() {
        return ELECTRIC_DISCHARGE_BURN_DURATION.get();
    }

    public static boolean isAttackCooldownEfficiencyItem(Item item) {
        return isModuleItem("attack_cooldown_efficiency", item);
    }

    public static boolean isShieldNaturalRecoveryItem(Item item) {
        return isModuleItem("shield_natural_recovery", item);
    }

    public static boolean isBinaryProtocolItem(Item item) {
        return isModuleItem("binary_protocol", item);
    }

    public static boolean isWeaponizedShieldItem(Item item) {
        return isModuleItem("weaponized_shield", item);
    }

    public static boolean isConversionItem(Item item) {
        return isModuleItem("conversion", item);
    }

    public static boolean isDangerousEntity(String entityId) {
        return DANGEROUS_ENTITY_SET.contains(entityId);
    }






    

    public static boolean isArcBarrierItem(Item item) {
        return isModuleItem("arc_barrier", item);
    }

    public static boolean isReshapingItem(Item item) {
        return isModuleItem("reshaping", item);
    }

    public static boolean isCounterPulseItem(Item item) {
        return isModuleItem("counter_pulse", item);
    }

    public static boolean isAssaultItem(Item item) {
        return isModuleItem("assault", item);
    }


    public static int getAssaultDurationTicks() {
        return ASSAULT_DURATION_TICKS.get();
    }




    public static boolean isChargedAttackItem(Item item) {
        return isModuleItem("charged_attack", item);
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

    public static boolean isChargedShieldItem(Item item) {
        return isModuleItem("charged_shield", item);
    }





    public static boolean isGrudgeItem(Item item) {
        return isModuleItem("grudge", item);
    }





    public static int getQuickEquipUpgradePointsCost() {
        return QUICK_EQUIP_UPGRADE_POINTS_COST.get();
    }

    public static boolean isRandomBuildEnabled() {
        return RANDOM_BUILD_ENABLED.get();
    }

    public static boolean isShowUpgradeReminderHud() {
        return SHOW_UPGRADE_REMINDER_HUD.get();
    }

    /** 从随机池获取物品时的升级点消耗倍数（升级点惩罚） */
    public static int getRandomBuildUpgradePointsMultiplier() {
        return RANDOM_BUILD_UPGRADE_POINTS_MULTIPLIER.get();
    }

    /** 是否启用代币机制（随机构建消耗背包代币而非升级点） */
    public static boolean isRandomBuildTokenEnabled() {
        return RANDOM_BUILD_TOKEN_ENABLED.get();
    }

    /** 代币物品 ID */
    public static String getRandomBuildTokenItemId() {
        return RANDOM_BUILD_TOKEN_ITEM.get();
    }

    /** 代币物品自定义描述（非空时显示在代币物品描述上） */
    public static String getRandomBuildTokenItemDescription() {
        return RANDOM_BUILD_TOKEN_DESC.get();
    }

    public static int getHostileTargetMarkDuration() {
        return HOSTILE_TARGET_MARK_DURATION.get();
    }

    public static boolean isPrecisionConstructItem(Item item) {
        return isModuleItem("precision_construct", item);
    }

    public static boolean isAdvancedEngineeringItem(Item item) {
        return isModuleItem("advanced_engineering", item);
    }

    public static boolean isPursuitArrayItem(Item item) {
        return isModuleItem("pursuit_array", item);
    }

    public static boolean isFormationArrayItem(Item item) {
        return isModuleItem("formation_array", item);
    }

    public static boolean isGuardArrayItem(Item item) {
        return isModuleItem("guard_array", item);
    }

    public static boolean isHardcoreModeEnabled() {
        return HARDCORE_MODE_ENABLED.get();
    }

    public static String getShieldHitSound() {
        return SHIELD_HIT_SOUND.get();
    }

    // ===== 幽灵机身辅助方法 =====

    public static boolean isGhostFuselageItem(Item item) {
        return isModuleItem("ghost_fuselage", item);
    }








    public static int getGhostFuselageFullStealthTicks() {
        return GHOST_FUSELAGE_FULL_STEALTH_TICKS.get();
    }
}

