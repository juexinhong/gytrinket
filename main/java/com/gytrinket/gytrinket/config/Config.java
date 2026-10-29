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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    /** 代币物品自定义描述（附属配置项，非空时显示在代币物品描述上） */
    public static final ModConfigSpec.ConfigValue<String> RANDOM_BUILD_TOKEN_DESC;

    // ===== 1. 光环护盾 (aura_shield) =====
    // 光环护盾的数值参数已迁移到数据驱动（item_definitions 的物品级 shieldValues 段），
    // 不再提供全局配置项；未声明时的最终回退见 ShieldValueDefs。

    // ===== 2-5. 虹吸 / 反射 / 增幅 / 跃传护盾 =====
    // 行为数值已迁移到数据驱动（item_definitions 的物品级 shieldValues 段），
    // 不再提供全局配置项；未声明时的最终回退见 ShieldValueDefs。

    // ===== 6/8/9/10. 屏障 / 易爆护盾 / 电能释放 / 武器化护盾 =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 7. 反射护盾 (reflect_damage) =====

    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_RAY_LENGTH;

    // ===== 11. 镀层 (coating_system) =====

    public static final ModConfigSpec.DoubleValue COATING_REDUCTION_PER_LAYER;

    // ===== 12. 适应性装甲 (adaptive_armor) =====

    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_DURATION;
    public static final ModConfigSpec.IntValue ADAPTIVE_ARMOR_MAX_LAYERS_PER_HIT;
    public static final ModConfigSpec.DoubleValue ADAPTIVE_ARMOR_LAYERS_PER_DAMAGE;

    // ===== 13. 再生护盾 (shield_natural_recovery) =====
    // 护盾再生速度 / 有无护盾值时的生命与护盾再生效率已迁移到物品定义：
    // item_definitions 的 values.shield_natural_recovery_items（机制参数，支持单/叠）。

    // ===== 14. 效率 (attack_cooldown_efficiency) =====

    // ===== 15. 转化 (conversion) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 16. 二原协议 (binary_protocol) =====

    // ===== 17. 强袭 (assault) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

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

    /** 弹射物来源追溯：弹射物加入世界时调用栈命中这些 Java 包名前缀则视为饰品/被动生成，不触发点射 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_SOURCE_TRACE_PACKAGES;

    // ===== 17.5 征途 (journey) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 19. 精密构造 (precision_construct) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 20. 护盾移植 (shield_transfer) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 21. 追击阵列/突击无人机 (pursuit_array) =====
    // 无人机基础属性已迁移到 item_definitions 的 values 段；仅跟随范围仍为全局

    public static final ModConfigSpec.DoubleValue DRONE_FOLLOW_RANGE;

    // ===== 21.5 无人机斩杀机制 (drone_execute) =====
    public static final ModConfigSpec.BooleanValue DRONE_EXECUTE_ENABLED;

    // ===== 21.6 僚机 (wingman) =====
    // 基础生命/数量/攻击间隔/范围/爆破弹伤害已迁移到 item_definitions 的 values 段

    public static final ModConfigSpec.IntValue WINGMAN_EXPLOSIVE_COUNT;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_DAMAGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_EXPLOSION_RADIUS;

    // ===== 拦截机/进化/纳米再生/震撼弹模块 =====

    public static final ModConfigSpec.IntValue WINGMAN_INTERCEPTOR_CHARGE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue WINGMAN_INTERCEPTOR_MAX_CHARGE_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue WINGMAN_INTERCEPTOR_CHARGED_SWEEP_BASE_RANGE;

    // 进化/纳米再生：数值已迁移到 item_definitions 的 values 段

    public static final ModConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WINGMAN_SHOCKWAVE_SPLASH_LENGTH_MULTIPLIER;

    // ===== 21.7 蜂群 (swarm) =====
    // 基础属性/构建时间已迁移到 item_definitions 的 values 段；数量极限/移速/升阶概率等仍为全局

    public static final ModConfigSpec.IntValue SWARM_COUNT_LIMIT;
    public static final ModConfigSpec.DoubleValue SWARM_MOVE_SPEED;
    public static final ModConfigSpec.IntValue SWARM_BUILD_TIME;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_STANDARD;
    public static final ModConfigSpec.DoubleValue SWARM_TIER_UPGRADE_CHANCE_ADVANCED;
    public static final ModConfigSpec.DoubleValue SWARM_VULNERABILITY_VALUE;
    public static final ModConfigSpec.DoubleValue SWARM_SHIELD_REPAIR_MULTIPLIER;

    // ===== 22. 宽限协议 (near_death_protection) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 23. 高等工程 (advanced_engineering) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

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

    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH;

    // ===== 24.5.2 次级攻击伤害合并 (secondary_damage_merge) =====

    public static final ModConfigSpec.BooleanValue SECONDARY_DAMAGE_MERGE_ENABLED;
    public static final ModConfigSpec.IntValue SECONDARY_DAMAGE_MERGE_WINDOW_TICKS;

    // 次级爆炸（爆炸半径模块特殊机制）：数值已迁移到 item_definitions 的 values 段

    // ===== 24.5.1 炉心融解模块 (furnace_core) =====

    // ===== 24.6 督战者 (taskmaster) =====

    // ===== 25. 列队阵列 (formation_array) =====

    public static final ModConfigSpec.IntValue FORMATION_ATTACK_PASS_DELAY;

    // ===== 26. 指挥官 (commander) =====

    public static final ModConfigSpec.IntValue COMMANDER_MAX_COUNT;
    public static final ModConfigSpec.IntValue COMMANDER_APPOINT_DELAY;
    public static final ModConfigSpec.DoubleValue COMMANDER_VULNERABILITY;

    // ===== 27. 守卫阵列/防御无人机 (guard_array) =====

    // ===== 28. 弧形屏障 (arc_barrier) =====

    public static final ModConfigSpec.DoubleValue ARC_BARRIER_POSITION_DEVIATION_THRESHOLD;

    // ===== 29. 反制脉冲 (counter_pulse) =====
    // 爆炸半径/伤害/冷却已迁移到 item_definitions 的 values 段；充能间隔与最大层数仍为全局

    public static final ModConfigSpec.IntValue COUNTER_PULSE_CHARGE_INTERVAL;
    public static final ModConfigSpec.IntValue COUNTER_PULSE_MAX_CHARGE_LEVEL;

    // ===== 30. 重塑 (reshaping) =====

    public static final ModConfigSpec.DoubleValue RESHAPING_HEAL_RATE;
    public static final ModConfigSpec.DoubleValue RESHAPING_BASE_DAMAGE_REDUCTION;
    public static final ModConfigSpec.IntValue RESHAPING_DAMAGE_REDUCTION_DURATION;

    // ===== 31. 充能护盾 (charged_shield) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 35. 幽灵机身 (ghost_fuselage) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

    // ===== 36. 积怨 (grudge) =====
    // 数值已迁移到 item_definitions 的 values 段；回退见 MechanicValueDefs。

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

        RANDOM_BUILD_TOKEN_DESC = BUILDER.comment(
            "代币物品自定义描述（附属配置项，可随意填写）",
            "非空时以特殊机制描述的样式显示在代币物品的悬浮描述上",
            "支持 \\n 换行；留空则不显示"
        ).define("tokenItemDescription", "");

        BUILDER.pop();

        // ===== 1. 光环护盾 =====
        // 数值已迁移到 item_definitions 物品级定义（shieldValues），此处不再创建全局配置项。

        // ===== 2-5. 虹吸 / 反射 / 增幅 / 跃传护盾 =====
        // 行为数值已迁移到 item_definitions 物品级定义（shieldValues），此处不再创建全局配置项。

        // ===== 6/8/9/10. 屏障 / 易爆护盾 / 电能释放 / 武器化护盾 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

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


        // ===== 14. 效率 =====
        BUILDER.comment("攻击冷却效率系统配置").push("attack_cooldown_efficiency");

        BUILDER.pop();

        // ===== 15. 转化 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 17. 强袭 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 17.5 征途 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

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

        // ===== 19. 精妙构造 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 20. 护盾移植 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 21. 追击阵列/突击无人机 =====
        BUILDER.comment("追击阵列/突击无人机配置").push("pursuit_array");

        DRONE_FOLLOW_RANGE = BUILDER.comment(
            "无人机跟随范围（格）"
        ).defineInRange("droneFollowRange", 16.0, 4.0, 64.0);

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

        WINGMAN_EXPLOSIVE_COUNT = BUILDER.comment(
            "每次攻击发射的爆破弹数量"
        ).defineInRange("wingmanExplosiveCount", 3, 1, 20);

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
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 23. 高等工程 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

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
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 24.5.1 炉心融解模块（配置挂载于自毁装置段，无独立 push） =====

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

        // ===== 27. 守卫阵列/防御无人机 =====
        BUILDER.comment("守卫阵列/防御无人机配置").push("guard_array");

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
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 35. 幽灵机身 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

        // ===== 36. 积怨 =====
        // 数值已迁移到 item_definitions 的 values 段，此处不再创建全局配置项。

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
            "护盾基础恢复速度（%/秒，0为禁用；默认 0 = 初始无再生能力）",
            "每个实例按自身最大护盾值 ×（该值 ÷ 5）恢复；池量为 0 的实例不恢复",
            "实际恢复量还会乘恢复效率属性、攻击冷却惩罚与护盾再生效率；装备再生护盾模块时与其物品级 regen_per_second 叠加",
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
    /** 威胁排除名单（第一优先级）：名单内实体不构成任何威胁，护盾系统永不将其作为目标（如伪无敌 boss） */
    private static final Set<String> THREAT_EXCLUDED_ENTITY_SET = new HashSet<>();
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
    /** 弹射物来源追溯包名前缀缓存 */
    private static final Set<String> PROJECTILE_SOURCE_TRACE_CACHE = new HashSet<>();
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
     * 其余物品检查 ATTRIBUTE_MODIFIERS 组件——自带主手攻速修正即视为武器。
     * <p>
     * 兜底场景：部分模组武器以「普通 Item + ItemAttributeModifiers 组件」形态实现
     * （如灾变 Cataclysm_Weapon extends Item，攻速修正 -2.4 全靠组件挂在物品上），
     * instanceof 三大类判不中。若误判为非武器，攻速口径测量/充能减益会在武器修正
     * 之上再叠加默认修正值，攻速被双重削减为负值（连击冷却被钳制到连击段数×10秒）
     */
    public static boolean isWeaponLikeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isWeaponLikeItem(stack.getItem())) {
            return true;
        }
        return stack.getAttributeModifiers().modifiers().stream()
                .anyMatch(entry -> entry.attribute().is(Attributes.ATTACK_SPEED)
                        && entry.slot().test(EquipmentSlot.MAINHAND));
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
            return;
        }
        initialized = true;

        loadItemUseChargeWhitelist();
        loadProjectileBlacklist();
        loadProjectileSourceTrace();

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

        // 危险实体
        DANGEROUS_ENTITY_SET.clear();
        DANGEROUS_ENTITY_SET.addAll(DefsManager.getEntitySet("dangerous_entities"));

        // 威胁排除名单（第一优先级）
        THREAT_EXCLUDED_ENTITY_SET.clear();
        THREAT_EXCLUDED_ENTITY_SET.addAll(DefsManager.getEntitySet("threat_excluded_entities"));

        // 依赖定义数据的子系统重载
        DisableSystem.loadConfig();
        UpgradeManager.loadConfig();
        ShieldTypeManager.init();

    }

    private static void loadItemSetFromDefs(Set<Item> targetSet, String setName, String logLabel) {
        targetSet.clear();
        for (String itemId : DefsManager.getItemSet(setName)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null && item != Items.AIR) {
                targetSet.add(item);
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
            default -> 0;
        };
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

    public static int getWingmanExplosiveCount() {
        return WINGMAN_EXPLOSIVE_COUNT.get();
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

    public static boolean isNanoRegenModuleItem(Item item) {
        return WINGMAN_NANO_REGEN_MODULE_ITEM_SET.contains(item);
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

    public static int getSwarmCountLimit() {
        return SWARM_COUNT_LIMIT.get();
    }

    public static double getSwarmMoveSpeed() {
        return SWARM_MOVE_SPEED.get();
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

    public static boolean isAttackCooldownEfficiencyItem(Item item) {
        return ATTACK_COOLDOWN_EFFICIENCY_ITEM_SET.contains(item);
    }

    public static boolean isShieldNaturalRecoveryItem(Item item) {
        return SHIELD_NATURAL_RECOVERY_ITEM_SET.contains(item);
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

    /** 威胁排除名单（第一优先级）：名单内实体不构成任何威胁，护盾系统永不将其作为目标 */
    public static boolean isThreatExcluded(String entityId) {
        return THREAT_EXCLUDED_ENTITY_SET.contains(entityId);
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

    public static boolean isChargedShieldItem(Item item) {
        return CHARGED_SHIELD_ITEM_SET.contains(item);
    }

    // ===== 幽灵机身辅助方法 =====

    public static boolean isGhostFuselageItem(Item item) {
        return GHOST_FUSELAGE_ITEM_SET.contains(item);
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

    /** 代币物品自定义描述（非空时显示在代币物品描述上） */
    public static String getRandomBuildTokenItemDescription() {
        return RANDOM_BUILD_TOKEN_DESC.get();
    }

    public static boolean isHardcoreModeEnabled() {
        return HARDCORE_MODE_ENABLED.get();
    }

    public static String getShieldHitSound() {
        return SHIELD_HIT_SOUND.get();
    }
}
