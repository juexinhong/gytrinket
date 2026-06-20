package com.gytrinket.gytrinket.items;

import com.gytrinket.gytrinket.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(com.gytrinket.gytrinket.gytrinket.MODID);

    // 基础方块
    public static final DeferredItem<BlockItem> LIGHT_POINT_CORE = ITEMS.registerSimpleBlockItem("light_point_core", ModBlocks.LIGHT_POINT_CORE);

    // 机身物品
    public static final DeferredItem<Item> GUARDIAN = ITEMS.registerSimpleItem("guardian", new Item.Properties()); // 哨卫机身

    // 基础护盾物品
    public static final DeferredItem<Item> SHIELD_GY = ITEMS.registerSimpleItem("shield_gy", new Item.Properties()); // 基础护盾
    public static final DeferredItem<Item> SHIELD_GY1 = ITEMS.registerSimpleItem("shield_gy1", new Item.Properties()); // 基础护盾+
    public static final DeferredItem<Item> SHIELD_GY2 = ITEMS.registerSimpleItem("shield_gy2", new Item.Properties()); // 基础护盾++
    public static final DeferredItem<Item> SHIELD_GY3 = ITEMS.registerSimpleItem("shield_gy3", new Item.Properties()); // 基础护盾+++

    // 光环护盾物品
    public static final DeferredItem<Item> SHIELD_AURA_RING = ITEMS.registerSimpleItem("shield_aura_ring", new Item.Properties()); // 光环护盾
    public static final DeferredItem<Item> SHIELD_AURA_RING1 = ITEMS.registerSimpleItem("shield_aura_ring1", new Item.Properties()); // 光环护盾+
    public static final DeferredItem<Item> SHIELD_AURA_RING2 = ITEMS.registerSimpleItem("shield_aura_ring2", new Item.Properties()); // 光环护盾++
    public static final DeferredItem<Item> SHIELD_AURA_RING3 = ITEMS.registerSimpleItem("shield_aura_ring3", new Item.Properties()); // 光环护盾+++

    // 虹吸护盾物品
    public static final DeferredItem<Item> SHIELD_SIPHON = ITEMS.registerSimpleItem("shield_siphon", new Item.Properties()); // 虹吸护盾
    public static final DeferredItem<Item> SHIELD_SIPHON1 = ITEMS.registerSimpleItem("shield_siphon1", new Item.Properties()); // 虹吸护盾+
    public static final DeferredItem<Item> SHIELD_SIPHON2 = ITEMS.registerSimpleItem("shield_siphon2", new Item.Properties()); // 虹吸护盾++
    public static final DeferredItem<Item> SHIELD_SIPHON3 = ITEMS.registerSimpleItem("shield_siphon3", new Item.Properties()); // 虹吸护盾+++

    // 反射护盾物品
    public static final DeferredItem<Item> SHIELD_REFLECT = ITEMS.registerSimpleItem("shield_reflect", new Item.Properties()); // 反射护盾
    public static final DeferredItem<Item> SHIELD_REFLECT1 = ITEMS.registerSimpleItem("shield_reflect1", new Item.Properties()); // 反射护盾+
    public static final DeferredItem<Item> SHIELD_REFLECT2 = ITEMS.registerSimpleItem("shield_reflect2", new Item.Properties()); // 反射护盾++
    public static final DeferredItem<Item> SHIELD_REFLECT3 = ITEMS.registerSimpleItem("shield_reflect3", new Item.Properties()); // 反射护盾+++

    //增幅护盾物品
    public static final DeferredItem<Item> SHIELD_AMPLIFIER = ITEMS.registerSimpleItem("shield_amplifier", new Item.Properties()); // 增幅护盾
    public static final DeferredItem<Item> SHIELD_AMPLIFIER1 = ITEMS.registerSimpleItem("shield_amplifier1", new Item.Properties()); // 增幅护盾+
    public static final DeferredItem<Item> SHIELD_AMPLIFIER2 = ITEMS.registerSimpleItem("shield_amplifier2", new Item.Properties()); // 增幅护盾++
    public static final DeferredItem<Item> SHIELD_AMPLIFIER3 = ITEMS.registerSimpleItem("shield_amplifier3", new Item.Properties()); // 增幅护盾+++

    // 跃传护盾物品
    public static final DeferredItem<Item> SHIELD_WARP = ITEMS.registerSimpleItem("shield_warp", new Item.Properties()); // 跃传护盾
    public static final DeferredItem<Item> SHIELD_WARP1 = ITEMS.registerSimpleItem("shield_warp1", new Item.Properties()); // 跃传护盾+
    public static final DeferredItem<Item> SHIELD_WARP2 = ITEMS.registerSimpleItem("shield_warp2", new Item.Properties()); // 跃传护盾++
    public static final DeferredItem<Item> SHIELD_WARP3 = ITEMS.registerSimpleItem("shield_warp3", new Item.Properties()); // 跃传护盾+++

    // 框架物品
    public static final DeferredItem<Item> MODULAR_FRAMEWORK = ITEMS.registerSimpleItem("modular_framework", new Item.Properties()); // 模块化框架
    public static final DeferredItem<Item> CONSTRUCT_FRAME = ITEMS.registerSimpleItem("construct_frame", new Item.Properties()); // 构造体框架
    public static final DeferredItem<Item> MOVEMENT_FRAME = ITEMS.registerSimpleItem("movement_frame", new Item.Properties()); // 移动框架
    public static final DeferredItem<Item> LIFE_FRAME = ITEMS.registerSimpleItem("life_frame", new Item.Properties()); // 生命框架
    public static final DeferredItem<Item> ATTACK_FRAME = ITEMS.registerSimpleItem("attack_frame", new Item.Properties()); // 攻击框架
    public static final DeferredItem<Item> SHIELD_RECEIVER = ITEMS.register("shield_receiver", () -> new ShieldReceiverItem(new Item.Properties())); // 护盾接收器

    // 护盾值物品
    public static final DeferredItem<Item> SHIELD_AMPLIFIER_MODULE = ITEMS.registerSimpleItem("shield_amplifier_module", new Item.Properties()); // 护盾值提升模块
    public static final DeferredItem<Item> BARRIER_SHIELD_MODULE = ITEMS.registerSimpleItem("barrier_shield_module", new Item.Properties()); // 屏障护盾模块
    public static final DeferredItem<Item> REFLECT_SHIELD_MODULE = ITEMS.registerSimpleItem("reflect_shield_module", new Item.Properties()); // 反射护盾模块
    public static final DeferredItem<Item> ULTIMATE_SHIELD_MODULE = ITEMS.registerSimpleItem("ultimate_shield_module", new Item.Properties()); // 至高之盾模块

    // 护盾冷却物品
    public static final DeferredItem<Item> SHIELD_COOLDOWN_REDUCTION_MODULE = ITEMS.registerSimpleItem("shield_cooldown_reduction_module", new Item.Properties()); // 护盾冷却缩减模块
    public static final DeferredItem<Item> SHIELD_QUICK_CHARGE_MODULE = ITEMS.registerSimpleItem("shield_quick_charge_module", new Item.Properties()); // 闪充护盾模块
    public static final DeferredItem<Item> EXPLOSIVE_SHIELD_MODULE = ITEMS.registerSimpleItem("explosive_shield_module", new Item.Properties()); // 易爆护盾模块
    public static final DeferredItem<Item> ELECTRIC_ENERGY_RELEASE_MODULE = ITEMS.registerSimpleItem("electric_energy_release_module", new Item.Properties()); // 电能释放模块

    // 护盾效果物品
    public static final DeferredItem<Item> SHIELD_EFFECT_BOOST_MODULE = ITEMS.registerSimpleItem("shield_effect_boost_module", new Item.Properties()); // 护盾效果增强模块
    public static final DeferredItem<Item> DIVERGENT_SHIELD_MODULE = ITEMS.registerSimpleItem("divergent_shield_module", new Item.Properties()); // 发散护盾模块
    public static final DeferredItem<Item> FOCUSED_SHIELD_MODULE = ITEMS.registerSimpleItem("focused_shield_module", new Item.Properties()); // 收束护盾模块
    public static final DeferredItem<Item> WEAPONIZED_SHIELD_MODULE = ITEMS.registerSimpleItem("weaponized_shield_module", new Item.Properties()); // 武器化护盾模块

    // 生命物品
    public static final DeferredItem<Item> HEALTH_BOOST_MODULE = ITEMS.registerSimpleItem("health_boost_module", new Item.Properties()); // 生命提升模块
    public static final DeferredItem<Item> COATING_MODULE = ITEMS.registerSimpleItem("coating_module", new Item.Properties()); // 镀层模块
    public static final DeferredItem<Item> COLOSSUS_MODULE = ITEMS.registerSimpleItem("colossus_module", new Item.Properties()); // 巨像模块

    // 适应性装甲物品
    public static final DeferredItem<Item> ADAPTIVE_ARMOR_MODULE = ITEMS.registerSimpleItem("adaptive_armor_module", new Item.Properties()); // 适应性装甲模块
    public static final DeferredItem<Item> BOND_MODULE = ITEMS.registerSimpleItem("bond_module", new Item.Properties()); // 联结模块
    public static final DeferredItem<Item> CORE_ARMOR_MODULE = ITEMS.registerSimpleItem("core_armor_module", new Item.Properties()); // 核心装甲模块

    // 再生物品
    public static final DeferredItem<Item> REGEN_MODULE = ITEMS.registerSimpleItem("regen_module", new Item.Properties()); // 再生模块
    public static final DeferredItem<Item> REGEN_SHIELD_MODULE = ITEMS.registerSimpleItem("regen_shield_module", new Item.Properties()); // 护盾再生模块

    // 协同物品
    public static final DeferredItem<Item> EFFICIENCY_MODULE = ITEMS.registerSimpleItem("efficiency_module", new Item.Properties()); // 效率模块
    public static final DeferredItem<Item> TRANSFORMATION_MODULE = ITEMS.registerSimpleItem("transformation_module", new Item.Properties()); // 转化模块
    public static final DeferredItem<Item> BINARY_PROTOCOL_MODULE = ITEMS.registerSimpleItem("binary_protocol_module", new Item.Properties()); // 二原协议模块

    // 攻击速度物品
    public static final DeferredItem<Item> FAST_SHOOTING_MODULE = ITEMS.registerSimpleItem("fast_shooting_module", new Item.Properties()); // 快速射击模块
    public static final DeferredItem<Item> BURST_FIRE_MODULE = ITEMS.registerSimpleItem("burst_fire_module", new Item.Properties()); // 点射模块
    public static final DeferredItem<Item> ASSAULT_MODULE = ITEMS.registerSimpleItem("assault_module", new Item.Properties()); // 强袭模块

    // 攻击伤害物品
    public static final DeferredItem<Item> CHARGED_ATTACK_MODULE = ITEMS.registerSimpleItem("charged_attack_module", new Item.Properties()); // 充能攻击模块

    // 速度物品
    public static final DeferredItem<Item> THRUST_BOOST_MODULE = ITEMS.registerSimpleItem("thrust_boost_module", new Item.Properties()); // 推进改良模块
    public static final DeferredItem<Item> AERODYNAMIC_FRAMEWORK_MODULE = ITEMS.registerSimpleItem("aerodynamic_framework_module", new Item.Properties()); // 流线构型模块
    public static final DeferredItem<Item> FLASH_MODULE = ITEMS.registerSimpleItem("flash_module", new Item.Properties()); // 闪现模块

    // 构造体物品
    public static final DeferredItem<Item> PRECISION_CONSTRUCT_MODULE = ITEMS.registerSimpleItem("precision_construct_module", new Item.Properties()); // 精密构造模块
    public static final DeferredItem<Item> SHIELD_TRANSFER_MODULE = ITEMS.registerSimpleItem("shield_transfer_module", new Item.Properties()); // 护盾移植模块

    // 无人机构建物品
    public static final DeferredItem<Item> DRONE_MODULE = ITEMS.registerSimpleItem("drone_module", new Item.Properties()); // 无人机模块
    public static final DeferredItem<Item> WIDE_PROTOCOL_MODULE = ITEMS.registerSimpleItem("wide_protocol_module", new Item.Properties()); // 宽限协议模块
    public static final DeferredItem<Item> ADVANCED_ENGINEERING_MODULE = ITEMS.registerSimpleItem("advanced_engineering_module", new Item.Properties()); // 高等工程模块

    // 突击无人机物品
    public static final DeferredItem<Item> ASSAULT_DRONE_MODULE = ITEMS.registerSimpleItem("assault_drone_module", new Item.Properties()); // 突击无人机模块
    public static final DeferredItem<Item> LAST_ORDER_MODULE = ITEMS.registerSimpleItem("last_order_module", new Item.Properties()); // 最后指令模块
    public static final DeferredItem<Item> LINE_FORMATION_MODULE = ITEMS.registerSimpleItem("line_formation_module", new Item.Properties()); // 列队阵列模块
    public static final DeferredItem<Item> WING_COMMANDER_MODULE = ITEMS.registerSimpleItem("wing_commander_module", new Item.Properties()); // 指挥官模块

    // 防御无人机物品
    public static final DeferredItem<Item> DEFENSE_DRONE_MODULE = ITEMS.registerSimpleItem("defense_drone_module", new Item.Properties()); // 防御无人机模块
    public static final DeferredItem<Item> COUNTER_PULSE_MODULE = ITEMS.registerSimpleItem("counter_pulse_module", new Item.Properties()); // 反制脉冲模块
    public static final DeferredItem<Item> ARC_BARRIER_MODULE = ITEMS.registerSimpleItem("arc_barrier_module", new Item.Properties()); // 弧形屏障模块
    public static final DeferredItem<Item> RESHAPING_MODULE = ITEMS.registerSimpleItem("reshaping_module", new Item.Properties()); // 重塑模块

    // 超越模块
    public static final DeferredItem<Item> CHARGED_SHIELD_MODULE = ITEMS.registerSimpleItem("charged_shield_module", new Item.Properties()); // 充能护盾模块
    public static final DeferredItem<Item> GRUDGE_MODULE = ITEMS.registerSimpleItem("grudge_module", new Item.Properties()); // 积怨模块

    // 零件
    public static final DeferredItem<Item> BEAM = ITEMS.registerSimpleItem("beam", new Item.Properties()); // 光束
    public static final DeferredItem<Item> BARRIER = ITEMS.registerSimpleItem("barrier", new Item.Properties()); // 屏障
    public static final DeferredItem<Item> ULTIMATE_SHIELD = ITEMS.registerSimpleItem("ultimate_shield", new Item.Properties()); // 至高之盾
    public static final DeferredItem<Item> CLOCK = ITEMS.registerSimpleItem("clock", new Item.Properties()); // 时钟
    public static final DeferredItem<Item> GALE = ITEMS.registerSimpleItem("gale", new Item.Properties()); // 旋风
    public static final DeferredItem<Item> LIGHT_BULB = ITEMS.registerSimpleItem("light_bulb", new Item.Properties()); // 电球
    public static final DeferredItem<Item> EFFECT_CORE = ITEMS.registerSimpleItem("effect_core", new Item.Properties()); // 效果核心
    public static final DeferredItem<Item> DIVERGENT_SHIELD = ITEMS.registerSimpleItem("divergent_shield", new Item.Properties()); // 发散护盾
    public static final DeferredItem<Item> CONVERGENT_SHIELD = ITEMS.registerSimpleItem("convergent_shield", new Item.Properties()); // 收束护盾
    public static final DeferredItem<Item> WEAPONIZED_CORE = ITEMS.registerSimpleItem("weaponized_core", new Item.Properties()); // 武器化核心
    public static final DeferredItem<Item> LIFE_ESSENCE = ITEMS.registerSimpleItem("life_essence", new Item.Properties()); // 生命精华
    public static final DeferredItem<Item> COATING = ITEMS.registerSimpleItem("coating", new Item.Properties()); // 镀层模块
    public static final DeferredItem<Item> STATUE = ITEMS.registerSimpleItem("statue", new Item.Properties()); // 雕像
    public static final DeferredItem<Item> ADAPTIVE_ARMOR = ITEMS.registerSimpleItem("adaptive_armor", new Item.Properties()); // 适应性装甲
    public static final DeferredItem<Item> BOND = ITEMS.registerSimpleItem("bond", new Item.Properties()); // 联结
    public static final DeferredItem<Item> CORE_ARMOR = ITEMS.registerSimpleItem("core_armor", new Item.Properties()); // 核心装甲
    public static final DeferredItem<Item> GEAR = ITEMS.registerSimpleItem("gear", new Item.Properties()); // 齿轮
    public static final DeferredItem<Item> TRANSMUTE_RUNE = ITEMS.registerSimpleItem("transmute_rune", new Item.Properties()); // 转化符
    public static final DeferredItem<Item> PROTOCOL = ITEMS.registerSimpleItem("protocol", new Item.Properties()); // 协议
    public static final DeferredItem<Item> PARTICLE_STREAM = ITEMS.registerSimpleItem("particle_stream", new Item.Properties()); // 粒子流
    public static final DeferredItem<Item> OVERCLOCK = ITEMS.registerSimpleItem("overclock", new Item.Properties()); // 超频
    public static final DeferredItem<Item> STAR_SPEAR = ITEMS.registerSimpleItem("star_spear", new Item.Properties()); // 星宇之矛
    public static final DeferredItem<Item> FOCUS_CORE = ITEMS.registerSimpleItem("focus_core", new Item.Properties()); // 聚能核心
    public static final DeferredItem<Item> THRUSTER = ITEMS.registerSimpleItem("thruster", new Item.Properties()); // 推进器
    public static final DeferredItem<Item> AERODYNAMIC_DESIGN_DRAFT = ITEMS.registerSimpleItem("aerodynamic_design_draft", new Item.Properties()); // 流线设计稿
    public static final DeferredItem<Item> COORDINATE = ITEMS.registerSimpleItem("coordinate", new Item.Properties()); // 坐标
    public static final DeferredItem<Item> PRECISION_CONSTRUCT_PART = ITEMS.registerSimpleItem("precision_construct_part", new Item.Properties()); // 精密构造零件
    public static final DeferredItem<Item> SHIELD_TRANSFER_PART = ITEMS.registerSimpleItem("shield_transfer_part", new Item.Properties()); // 护盾移植零件
    public static final DeferredItem<Item> DRONE_PART = ITEMS.registerSimpleItem("drone_part", new Item.Properties()); // 无人机零件
    public static final DeferredItem<Item> WIDE_PROTOCOL = ITEMS.registerSimpleItem("wide_protocol", new Item.Properties()); // 宽限协议
    public static final DeferredItem<Item> ADVANCED_ENGINEERING_PART = ITEMS.registerSimpleItem("advanced_engineering_part", new Item.Properties()); // 高等工程零件
    public static final DeferredItem<Item> ASSAULT_DRONE_PART = ITEMS.registerSimpleItem("assault_drone_part", new Item.Properties()); // 突击无人机零件
    public static final DeferredItem<Item> LAST_ORDER_PART = ITEMS.registerSimpleItem("last_order_part", new Item.Properties()); // 最后指令零件
    public static final DeferredItem<Item> LINE_FORMATION_PART = ITEMS.registerSimpleItem("line_formation_part", new Item.Properties()); // 列队阵列零件
    public static final DeferredItem<Item> WING_COMMANDER_PART = ITEMS.registerSimpleItem("wing_commander_part", new Item.Properties()); // 指挥官零件
    public static final DeferredItem<Item> DEFENSE_DRONE_PART = ITEMS.registerSimpleItem("defense_drone_part", new Item.Properties()); // 防御无人机零件
    public static final DeferredItem<Item> COUNTER_PULSE_PART = ITEMS.registerSimpleItem("counter_pulse_part", new Item.Properties()); // 反制脉冲零件
    public static final DeferredItem<Item> ARC_BARRIER_PART = ITEMS.registerSimpleItem("arc_barrier_part", new Item.Properties()); // 弧形屏障零件
    public static final DeferredItem<Item> RESHAPING_PART = ITEMS.registerSimpleItem("reshaping_part", new Item.Properties()); // 重塑零件
}
