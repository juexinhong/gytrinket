package com.gy_mod.gy_trinket.items;

import com.gy_mod.gy_trinket.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, com.gy_mod.gy_trinket.gytrinket.MODID);

    // 基础方块
    public static final RegistryObject<Item> LIGHT_POINT_CORE = ITEMS.register("light_point_core", () -> new BlockItem(ModBlocks.LIGHT_POINT_CORE.get(), new Item.Properties())); // 光点核心方块
    
    // 机身物品
    public static final RegistryObject<Item> GUARDIAN = ITEMS.register("guardian", () -> new Item(new Item.Properties())); // 哨卫机身
    public static final RegistryObject<Item> SWARM_MODULE = ITEMS.register("mothership_body", () -> new Item(new Item.Properties())); // 母舰机身

    // 基础护盾物品
    public static final RegistryObject<Item> SHIELD_GY = ITEMS.register("shield_gy", () -> new Item(new Item.Properties())); // 基础护盾
    public static final RegistryObject<Item> SHIELD_GY1 = ITEMS.register("shield_gy1", () -> new Item(new Item.Properties())); // 基础护盾+
    public static final RegistryObject<Item> SHIELD_GY2 = ITEMS.register("shield_gy2", () -> new Item(new Item.Properties())); // 基础护盾++
    public static final RegistryObject<Item> SHIELD_GY3 = ITEMS.register("shield_gy3", () -> new Item(new Item.Properties())); // 基础护盾+++

    // 光环护盾物品
    public static final RegistryObject<Item> SHIELD_AURA_RING = ITEMS.register("shield_aura_ring", () -> new Item(new Item.Properties())); // 光环护盾
    public static final RegistryObject<Item> SHIELD_AURA_RING1 = ITEMS.register("shield_aura_ring1", () -> new Item(new Item.Properties())); // 光环护盾+
    public static final RegistryObject<Item> SHIELD_AURA_RING2 = ITEMS.register("shield_aura_ring2", () -> new Item(new Item.Properties())); // 光环护盾++
    public static final RegistryObject<Item> SHIELD_AURA_RING3 = ITEMS.register("shield_aura_ring3", () -> new Item(new Item.Properties())); // 光环护盾+++

    // 虹吸护盾物品
    public static final RegistryObject<Item> SHIELD_SIPHON = ITEMS.register("shield_siphon", () -> new Item(new Item.Properties())); // 虹吸护盾
    public static final RegistryObject<Item> SHIELD_SIPHON1 = ITEMS.register("shield_siphon1", () -> new Item(new Item.Properties())); // 虹吸护盾+
    public static final RegistryObject<Item> SHIELD_SIPHON2 = ITEMS.register("shield_siphon2", () -> new Item(new Item.Properties())); // 虹吸护盾++
    public static final RegistryObject<Item> SHIELD_SIPHON3 = ITEMS.register("shield_siphon3", () -> new Item(new Item.Properties())); // 虹吸护盾+++

    // 反射护盾物品
    public static final RegistryObject<Item> SHIELD_REFLECT = ITEMS.register("shield_reflect", () -> new Item(new Item.Properties())); // 反射护盾
    public static final RegistryObject<Item> SHIELD_REFLECT1 = ITEMS.register("shield_reflect1", () -> new Item(new Item.Properties())); // 反射护盾+
    public static final RegistryObject<Item> SHIELD_REFLECT2 = ITEMS.register("shield_reflect2", () -> new Item(new Item.Properties())); // 反射护盾++
    public static final RegistryObject<Item> SHIELD_REFLECT3 = ITEMS.register("shield_reflect3", () -> new Item(new Item.Properties())); // 反射护盾+++
    //增幅护盾物品
    public static final RegistryObject<Item> SHIELD_AMPLIFIER = ITEMS.register("shield_amplifier", () -> new Item(new Item.Properties())); // 增幅护盾
    public static final RegistryObject<Item> SHIELD_AMPLIFIER1 = ITEMS.register("shield_amplifier1", () -> new Item(new Item.Properties())); // 增幅护盾+
    public static final RegistryObject<Item> SHIELD_AMPLIFIER2 = ITEMS.register("shield_amplifier2", () -> new Item(new Item.Properties())); // 增幅护盾++
    public static final RegistryObject<Item> SHIELD_AMPLIFIER3 = ITEMS.register("shield_amplifier3", () -> new Item(new Item.Properties())); // 增幅护盾+++

    // 跃传护盾物品
    public static final RegistryObject<Item> SHIELD_WARP = ITEMS.register("shield_warp", () -> new Item(new Item.Properties())); // 跃传护盾
    public static final RegistryObject<Item> SHIELD_WARP1 = ITEMS.register("shield_warp1", () -> new Item(new Item.Properties())); // 跃传护盾+
    public static final RegistryObject<Item> SHIELD_WARP2 = ITEMS.register("shield_warp2", () -> new Item(new Item.Properties())); // 跃传护盾++
    public static final RegistryObject<Item> SHIELD_WARP3 = ITEMS.register("shield_warp3", () -> new Item(new Item.Properties())); // 跃传护盾+++

    // 框架物品
    public static final RegistryObject<Item> MODULAR_FRAMEWORK = ITEMS.register("modular_framework", () -> new Item(new Item.Properties())); // 模块化框架
    public static final RegistryObject<Item> CONSTRUCT_FRAME = ITEMS.register("construct_frame", () -> new Item(new Item.Properties())); // 构造体框架
    public static final RegistryObject<Item> MOVEMENT_FRAME = ITEMS.register("movement_frame", () -> new Item(new Item.Properties())); // 移动框架
    public static final RegistryObject<Item> LIFE_FRAME = ITEMS.register("life_frame", () -> new Item(new Item.Properties())); // 生命框架
    public static final RegistryObject<Item> ATTACK_FRAME = ITEMS.register("attack_frame", () -> new Item(new Item.Properties())); // 攻击框架
    public static final RegistryObject<Item> SHIELD_RECEIVER = ITEMS.register("shield_receiver", () -> new ShieldReceiverItem(new Item.Properties())); // 护盾接收器

    // 护盾值物品
    public static final RegistryObject<Item> SHIELD_AMPLIFIER_MODULE = ITEMS.register("shield_amplifier_module", () -> new Item(new Item.Properties())); // 护盾值提升模块
    public static final RegistryObject<Item> BARRIER_SHIELD_MODULE = ITEMS.register("barrier_shield_module", () -> new Item(new Item.Properties())); // 屏障护盾模块
    public static final RegistryObject<Item> REFLECT_SHIELD_MODULE = ITEMS.register("reflect_shield_module", () -> new Item(new Item.Properties())); // 反射护盾模块
    public static final RegistryObject<Item> ULTIMATE_SHIELD_MODULE = ITEMS.register("ultimate_shield_module", () -> new Item(new Item.Properties())); // 至高之盾模块

    // 护盾冷却物品
    public static final RegistryObject<Item> SHIELD_COOLDOWN_REDUCTION_MODULE = ITEMS.register("shield_cooldown_reduction_module", () -> new Item(new Item.Properties())); // 护盾冷却缩减模块
    public static final RegistryObject<Item> SHIELD_QUICK_CHARGE_MODULE = ITEMS.register("shield_quick_charge_module", () -> new Item(new Item.Properties())); // 闪充护盾模块
    public static final RegistryObject<Item> EXPLOSIVE_SHIELD_MODULE = ITEMS.register("explosive_shield_module", () -> new Item(new Item.Properties())); // 易爆护盾模块
    public static final RegistryObject<Item> ELECTRIC_ENERGY_RELEASE_MODULE = ITEMS.register("electric_energy_release_module", () -> new Item(new Item.Properties())); // 电能释放模块

    // 护盾效果物品
    public static final RegistryObject<Item> SHIELD_EFFECT_BOOST_MODULE = ITEMS.register("shield_effect_boost_module", () -> new Item(new Item.Properties())); // 护盾效果增强模块
    public static final RegistryObject<Item> DIVERGENT_SHIELD_MODULE = ITEMS.register("divergent_shield_module", () -> new Item(new Item.Properties())); // 发散护盾模块
    public static final RegistryObject<Item> FOCUSED_SHIELD_MODULE = ITEMS.register("focused_shield_module", () -> new Item(new Item.Properties())); // 收束护盾模块
    public static final RegistryObject<Item> WEAPONIZED_SHIELD_MODULE = ITEMS.register("weaponized_shield_module", () -> new Item(new Item.Properties())); // 武器化护盾模块
    
    // 生命物品
    public static final RegistryObject<Item> HEALTH_BOOST_MODULE = ITEMS.register("health_boost_module", () -> new Item(new Item.Properties())); // 生命提升模块
    public static final RegistryObject<Item> COATING_MODULE = ITEMS.register("coating_module", () -> new Item(new Item.Properties())); // 镀层模块
    public static final RegistryObject<Item> COLOSSUS_MODULE = ITEMS.register("colossus_module", () -> new Item(new Item.Properties())); // 巨像模块

    // 适应性装甲物品
    public static final RegistryObject<Item> ADAPTIVE_ARMOR_MODULE = ITEMS.register("adaptive_armor_module", () -> new Item(new Item.Properties())); // 适应性装甲模块
    public static final RegistryObject<Item> BOND_MODULE = ITEMS.register("bond_module", () -> new Item(new Item.Properties())); // 联结模块
    public static final RegistryObject<Item> CORE_ARMOR_MODULE = ITEMS.register("core_armor_module", () -> new Item(new Item.Properties())); // 核心装甲模块

    // 再生物品
    public static final RegistryObject<Item> REGEN_MODULE = ITEMS.register("regen_module", () -> new Item(new Item.Properties())); // 再生模块
    public static final RegistryObject<Item> QUICK_RECONSTRUCTION_MODULE = ITEMS.register("quick_reconstruction_module", () -> new Item(new Item.Properties())); // 快速重构模块
    public static final RegistryObject<Item> REGEN_SHIELD_MODULE = ITEMS.register("regen_shield_module", () -> new Item(new Item.Properties())); // 护盾再生模块

    // 协同物品
    public static final RegistryObject<Item> EFFICIENCY_MODULE = ITEMS.register("efficiency_module", () -> new Item(new Item.Properties())); // 效率模块
    public static final RegistryObject<Item> TRANSFORMATION_MODULE = ITEMS.register("transformation_module", () -> new Item(new Item.Properties())); // 转化模块
    public static final RegistryObject<Item> BINARY_PROTOCOL_MODULE = ITEMS.register("binary_protocol_module", () -> new Item(new Item.Properties())); // 二原协议模块
    
    // 攻击速度物品
    public static final RegistryObject<Item> FAST_SHOOTING_MODULE = ITEMS.register("fast_shooting_module", () -> new Item(new Item.Properties())); // 快速射击模块
    public static final RegistryObject<Item> BURST_FIRE_MODULE = ITEMS.register("burst_fire_module", () -> new Item(new Item.Properties())); // 点射模块
    public static final RegistryObject<Item> ASSAULT_MODULE = ITEMS.register("assault_module", () -> new Item(new Item.Properties())); // 强袭模块

    // 攻击伤害物品
    public static final RegistryObject<Item> CHARGED_ATTACK_MODULE = ITEMS.register("charged_attack_module", () -> new Item(new Item.Properties())); // 充能攻击模块

    // 速度物品
    public static final RegistryObject<Item> THRUST_BOOST_MODULE = ITEMS.register("thrust_boost_module", () -> new Item(new Item.Properties())); // 推进改良模块
    public static final RegistryObject<Item> AERODYNAMIC_FRAMEWORK_MODULE = ITEMS.register("aerodynamic_framework_module", () -> new Item(new Item.Properties())); // 流线构型模块
    public static final RegistryObject<Item> FLASH_MODULE = ITEMS.register("flash_module", () -> new Item(new Item.Properties())); // 闪现模块
    // 构造体物品
    public static final RegistryObject<Item> PRECISION_CONSTRUCT_MODULE = ITEMS.register("precision_construct_module", () -> new Item(new Item.Properties())); // 精密构造模块
    public static final RegistryObject<Item> SHIELD_TRANSFER_MODULE = ITEMS.register("shield_transfer_module", () -> new Item(new Item.Properties())); // 护盾移植模块
    public static final RegistryObject<Item> SELF_DESTRUCT_MODULE = ITEMS.register("self_destruct_module", () -> new Item(new Item.Properties())); // 自毁装置模块
    public static final RegistryObject<Item> TASKMASTER_MODULE = ITEMS.register("taskmaster_module", () -> new Item(new Item.Properties())); // 督战者模块

    // 无人机构建物品
    public static final RegistryObject<Item> DRONE_MODULE = ITEMS.register("drone_module", () -> new Item(new Item.Properties())); // 无人机模块
    public static final RegistryObject<Item> WIDE_PROTOCOL_MODULE = ITEMS.register("wide_protocol_module", () -> new Item(new Item.Properties())); // 宽限协议模块
    public static final RegistryObject<Item> ADVANCED_ENGINEERING_MODULE = ITEMS.register("advanced_engineering_module", () -> new Item(new Item.Properties())); // 高等工程模块

    // 突击无人机物品
    public static final RegistryObject<Item> ASSAULT_DRONE_MODULE = ITEMS.register("assault_drone_module", () -> new Item(new Item.Properties())); // 突击无人机模块
    public static final RegistryObject<Item> LAST_ORDER_MODULE = ITEMS.register("last_order_module", () -> new Item(new Item.Properties())); // 最后指令模块
    public static final RegistryObject<Item> LINE_FORMATION_MODULE = ITEMS.register("line_formation_module", () -> new Item(new Item.Properties())); // 列队阵列模块
    public static final RegistryObject<Item> WING_COMMANDER_MODULE = ITEMS.register("wing_commander_module", () -> new Item(new Item.Properties())); // 指挥官模块

    // 防御无人机物品
    public static final RegistryObject<Item> DEFENSE_DRONE_MODULE = ITEMS.register("defense_drone_module", () -> new Item(new Item.Properties())); // 防御无人机模块
    public static final RegistryObject<Item> COUNTER_PULSE_MODULE = ITEMS.register("counter_pulse_module", () -> new Item(new Item.Properties())); // 反制脉冲模块
    public static final RegistryObject<Item> ARC_BARRIER_MODULE = ITEMS.register("arc_barrier_module", () -> new Item(new Item.Properties())); // 弧形屏障模块
    public static final RegistryObject<Item> RESHAPING_MODULE = ITEMS.register("reshaping_module", () -> new Item(new Item.Properties())); // 重塑模块

    // 僚机物品
    public static final RegistryObject<Item> WINGMAN_MODULE = ITEMS.register("wingman_module", () -> new Item(new Item.Properties())); // 僚机模块

    // 超越模块
    public static final RegistryObject<Item> CHARGED_SHIELD_MODULE = ITEMS.register("charged_shield_module", () -> new Item(new Item.Properties())); // 充能护盾模块
    public static final RegistryObject<Item> GRUDGE_MODULE = ITEMS.register("grudge_module", () -> new Item(new Item.Properties())); // 积怨模块
    
    // 零件
    public static final RegistryObject<Item> BEAM = ITEMS.register("beam", () -> new Item(new Item.Properties())); // 光束
    public static final RegistryObject<Item> BARRIER = ITEMS.register("barrier", () -> new Item(new Item.Properties())); // 屏障
    public static final RegistryObject<Item> ULTIMATE_SHIELD = ITEMS.register("ultimate_shield", () -> new Item(new Item.Properties())); // 至高之盾
    public static final RegistryObject<Item> CLOCK = ITEMS.register("clock", () -> new Item(new Item.Properties())); // 时钟
    public static final RegistryObject<Item> GALE = ITEMS.register("gale", () -> new Item(new Item.Properties())); // 旋风
    public static final RegistryObject<Item> LIGHT_BULB = ITEMS.register("light_bulb", () -> new Item(new Item.Properties())); // 电球
    public static final RegistryObject<Item> EFFECT_CORE = ITEMS.register("effect_core", () -> new Item(new Item.Properties())); // 效果核心
    public static final RegistryObject<Item> DIVERGENT_SHIELD = ITEMS.register("divergent_shield", () -> new Item(new Item.Properties())); // 发散护盾
    public static final RegistryObject<Item> CONVERGENT_SHIELD = ITEMS.register("convergent_shield", () -> new Item(new Item.Properties())); // 收束护盾
    public static final RegistryObject<Item> WEAPONIZED_CORE = ITEMS.register("weaponized_core", () -> new Item(new Item.Properties())); // 武器化核心
    public static final RegistryObject<Item> LIFE_ESSENCE = ITEMS.register("life_essence", () -> new Item(new Item.Properties())); // 生命精华
    public static final RegistryObject<Item> COATING = ITEMS.register("coating", () -> new Item(new Item.Properties())); // 镀层模块
    public static final RegistryObject<Item> STATUE = ITEMS.register("statue", () -> new Item(new Item.Properties())); // 雕像
    public static final RegistryObject<Item> ADAPTIVE_ARMOR = ITEMS.register("adaptive_armor", () -> new Item(new Item.Properties())); // 适应性装甲
    public static final RegistryObject<Item> BOND = ITEMS.register("bond", () -> new Item(new Item.Properties())); // 联结
    public static final RegistryObject<Item> CORE_ARMOR = ITEMS.register("core_armor", () -> new Item(new Item.Properties())); // 核心装甲
    public static final RegistryObject<Item> GEAR = ITEMS.register("gear", () -> new Item(new Item.Properties())); // 齿轮
    public static final RegistryObject<Item> TRANSMUTE_RUNE = ITEMS.register("transmute_rune", () -> new Item(new Item.Properties())); // 转化符
    public static final RegistryObject<Item> PROTOCOL = ITEMS.register("protocol", () -> new Item(new Item.Properties())); // 协议
    public static final RegistryObject<Item> PARTICLE_STREAM = ITEMS.register("particle_stream", () -> new Item(new Item.Properties())); // 粒子流
    public static final RegistryObject<Item> OVERCLOCK = ITEMS.register("overclock", () -> new Item(new Item.Properties())); // 超频
    public static final RegistryObject<Item> STAR_SPEAR = ITEMS.register("star_spear", () -> new Item(new Item.Properties())); // 星宇之矛
    public static final RegistryObject<Item> FOCUS_CORE = ITEMS.register("focus_core", () -> new Item(new Item.Properties())); // 聚能核心
    public static final RegistryObject<Item> THRUSTER = ITEMS.register("thruster", () -> new Item(new Item.Properties())); // 推进器
    public static final RegistryObject<Item> AERODYNAMIC_DESIGN_DRAFT = ITEMS.register("aerodynamic_design_draft", () -> new Item(new Item.Properties())); // 流线设计稿
    public static final RegistryObject<Item> COORDINATE = ITEMS.register("coordinate", () -> new Item(new Item.Properties())); // 坐标
    public static final RegistryObject<Item> PRECISION_CONSTRUCT_PART = ITEMS.register("precision_construct_part", () -> new Item(new Item.Properties())); // 精密构造零件
    public static final RegistryObject<Item> SHIELD_TRANSFER_PART = ITEMS.register("shield_transfer_part", () -> new Item(new Item.Properties())); // 护盾移植零件
    public static final RegistryObject<Item> DRONE_PART = ITEMS.register("drone_part", () -> new Item(new Item.Properties())); // 无人机零件
    public static final RegistryObject<Item> WIDE_PROTOCOL = ITEMS.register("wide_protocol", () -> new Item(new Item.Properties())); // 宽限协议
    public static final RegistryObject<Item> ADVANCED_ENGINEERING_PART = ITEMS.register("advanced_engineering_part", () -> new Item(new Item.Properties())); // 高等工程零件
    public static final RegistryObject<Item> ASSAULT_DRONE_PART = ITEMS.register("assault_drone_part", () -> new Item(new Item.Properties())); // 突击无人机零件
    public static final RegistryObject<Item> LAST_ORDER_PART = ITEMS.register("last_order_part", () -> new Item(new Item.Properties())); // 最后指令零件
    public static final RegistryObject<Item> LINE_FORMATION_PART = ITEMS.register("line_formation_part", () -> new Item(new Item.Properties())); // 列队阵列零件
    public static final RegistryObject<Item> WING_COMMANDER_PART = ITEMS.register("wing_commander_part", () -> new Item(new Item.Properties())); // 指挥官零件
    public static final RegistryObject<Item> DEFENSE_DRONE_PART = ITEMS.register("defense_drone_part", () -> new Item(new Item.Properties())); // 防御无人机零件
    public static final RegistryObject<Item> COUNTER_PULSE_PART = ITEMS.register("counter_pulse_part", () -> new Item(new Item.Properties())); // 反制脉冲零件
    public static final RegistryObject<Item> ARC_BARRIER_PART = ITEMS.register("arc_barrier_part", () -> new Item(new Item.Properties())); // 弧形屏障零件
    public static final RegistryObject<Item> RESHAPING_PART = ITEMS.register("reshaping_part", () -> new Item(new Item.Properties())); // 重塑零件

}