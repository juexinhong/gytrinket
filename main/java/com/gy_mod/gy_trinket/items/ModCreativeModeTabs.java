package com.gy_mod.gy_trinket.items;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, gytrinket.MODID);

    public static final RegistryObject<CreativeModeTab> GY_TRINKET_TAB = CREATIVE_MODE_TABS.register("gy_trinket_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.LIGHT_POINT_CORE.get()))
                    .title(Component.translatable("creative_tab.gytrinket"))
                    .displayItems((parameters, output) -> {
                        // 基础方块
                        output.accept(ModItems.LIGHT_POINT_CORE.get());
                        
                        // 机身物品
                        output.accept(ModItems.GUARDIAN.get());

                        // 基础护盾物品
                        output.accept(ModItems.SHIELD_GY.get());
                        output.accept(ModItems.SHIELD_GY1.get());
                        output.accept(ModItems.SHIELD_GY2.get());
                        output.accept(ModItems.SHIELD_GY3.get());

                        // 光环护盾物品
                        output.accept(ModItems.SHIELD_AURA_RING.get());
                        output.accept(ModItems.SHIELD_AURA_RING1.get());
                        output.accept(ModItems.SHIELD_AURA_RING2.get());
                        output.accept(ModItems.SHIELD_AURA_RING3.get());

                        // 虹吸护盾物品
                        output.accept(ModItems.SHIELD_SIPHON.get());
                        output.accept(ModItems.SHIELD_SIPHON1.get());
                        output.accept(ModItems.SHIELD_SIPHON2.get());
                        output.accept(ModItems.SHIELD_SIPHON3.get());

                        // 反射护盾物品
                        output.accept(ModItems.SHIELD_REFLECT.get());
                        output.accept(ModItems.SHIELD_REFLECT1.get());
                        output.accept(ModItems.SHIELD_REFLECT2.get());
                        output.accept(ModItems.SHIELD_REFLECT3.get());

                        // 增幅护盾物品
                        output.accept(ModItems.SHIELD_AMPLIFIER.get());
                        output.accept(ModItems.SHIELD_AMPLIFIER1.get());
                        output.accept(ModItems.SHIELD_AMPLIFIER2.get());
                        output.accept(ModItems.SHIELD_AMPLIFIER3.get());

                        // 跃传护盾物品
                        output.accept(ModItems.SHIELD_WARP.get());
                        output.accept(ModItems.SHIELD_WARP1.get());
                        output.accept(ModItems.SHIELD_WARP2.get());
                        output.accept(ModItems.SHIELD_WARP3.get());

                        // 框架物品
                        output.accept(ModItems.MODULAR_FRAMEWORK.get());
                        output.accept(ModItems.CONSTRUCT_FRAME.get());
                        output.accept(ModItems.MOVEMENT_FRAME.get());
                        output.accept(ModItems.LIFE_FRAME.get());
                        output.accept(ModItems.SHIELD_RECEIVER.get());

                        // 护盾值物品
                        output.accept(ModItems.SHIELD_AMPLIFIER_MODULE.get());
                        output.accept(ModItems.BARRIER_SHIELD_MODULE.get());
                        output.accept(ModItems.REFLECT_SHIELD_MODULE.get());
                        output.accept(ModItems.ULTIMATE_SHIELD_MODULE.get());

                        // 护盾冷却物品
                        output.accept(ModItems.SHIELD_COOLDOWN_REDUCTION_MODULE.get());
                        output.accept(ModItems.SHIELD_QUICK_CHARGE_MODULE.get());
                        output.accept(ModItems.EXPLOSIVE_SHIELD_MODULE.get());
                        output.accept(ModItems.ELECTRIC_ENERGY_RELEASE_MODULE.get());

                        // 护盾效果物品
                        output.accept(ModItems.SHIELD_EFFECT_BOOST_MODULE.get());
                        output.accept(ModItems.DIVERGENT_SHIELD_MODULE.get());
                        output.accept(ModItems.FOCUSED_SHIELD_MODULE.get());
                        output.accept(ModItems.WEAPONIZED_SHIELD_MODULE.get());

                        // 生命物品
                        output.accept(ModItems.HEALTH_BOOST_MODULE.get());
                        output.accept(ModItems.COATING_MODULE.get());
                        output.accept(ModItems.COLOSSUS_MODULE.get());

                        // 适应性装甲物品
                        output.accept(ModItems.ADAPTIVE_ARMOR_MODULE.get());
                        output.accept(ModItems.BOND_MODULE.get());
                        output.accept(ModItems.CORE_ARMOR_MODULE.get());

                        // 再生物品
                        output.accept(ModItems.REGEN_MODULE.get());
                        output.accept(ModItems.REGEN_SHIELD_MODULE.get());

                        // 协同物品
                        output.accept(ModItems.EFFICIENCY_MODULE.get());
                        output.accept(ModItems.TRANSFORMATION_MODULE.get());
                        output.accept(ModItems.BINARY_PROTOCOL_MODULE.get());

                        // 攻击速度物品
                        output.accept(ModItems.FAST_SHOOTING_MODULE.get());
                        output.accept(ModItems.BURST_FIRE_MODULE.get());
                        output.accept(ModItems.ASSAULT_MODULE.get());

                        // 速度物品
                        output.accept(ModItems.THRUST_BOOST_MODULE.get());
                        output.accept(ModItems.AERODYNAMIC_FRAMEWORK_MODULE.get());
                        output.accept(ModItems.FLASH_MODULE.get());

                        // 构造体物品
                        output.accept(ModItems.PRECISION_CONSTRUCT_MODULE.get());
                        output.accept(ModItems.SHIELD_TRANSFER_MODULE.get());

                        // 无人机构建物品
                        output.accept(ModItems.DRONE_MODULE.get());
                        output.accept(ModItems.WIDE_PROTOCOL_MODULE.get());
                        output.accept(ModItems.ADVANCED_ENGINEERING_MODULE.get());

                        // 突击无人机物品
                        output.accept(ModItems.ASSAULT_DRONE_MODULE.get());
                        output.accept(ModItems.LAST_ORDER_MODULE.get());
                        output.accept(ModItems.LINE_FORMATION_MODULE.get());
                        output.accept(ModItems.WING_COMMANDER_MODULE.get());

                        // 防御无人机物品
                        output.accept(ModItems.DEFENSE_DRONE_MODULE.get());
                        output.accept(ModItems.COUNTER_PULSE_MODULE.get());
                        output.accept(ModItems.ARC_BARRIER_MODULE.get());
                        output.accept(ModItems.RESHAPING_MODULE.get());

                        // 零件
                        output.accept(ModItems.BEAM.get());
                        output.accept(ModItems.BARRIER.get());
                        output.accept(ModItems.ULTIMATE_SHIELD.get());
                        output.accept(ModItems.CLOCK.get());
                        output.accept(ModItems.GALE.get());
                        output.accept(ModItems.LIGHT_BULB.get());
                        output.accept(ModItems.EFFECT_CORE.get());
                        output.accept(ModItems.DIVERGENT_SHIELD.get());
                        output.accept(ModItems.CONVERGENT_SHIELD.get());
                        output.accept(ModItems.WEAPONIZED_CORE.get());
                        output.accept(ModItems.LIFE_ESSENCE.get());
                        output.accept(ModItems.COATING.get());
                        output.accept(ModItems.STATUE.get());
                        output.accept(ModItems.ADAPTIVE_ARMOR.get());
                        output.accept(ModItems.BOND.get());
                        output.accept(ModItems.CORE_ARMOR.get());
                        output.accept(ModItems.GEAR.get());
                        output.accept(ModItems.TRANSMUTE_RUNE.get());
                        output.accept(ModItems.PROTOCOL.get());
                        output.accept(ModItems.PARTICLE_STREAM.get());
                        output.accept(ModItems.OVERCLOCK.get());
                        output.accept(ModItems.STAR_SPEAR.get());
                        output.accept(ModItems.THRUSTER.get());
                        output.accept(ModItems.AERODYNAMIC_DESIGN_DRAFT.get());
                        output.accept(ModItems.COORDINATE.get());
                        output.accept(ModItems.PRECISION_CONSTRUCT_PART.get());
                        output.accept(ModItems.SHIELD_TRANSFER_PART.get());
                        output.accept(ModItems.DRONE_PART.get());
                        output.accept(ModItems.WIDE_PROTOCOL.get());
                        output.accept(ModItems.ADVANCED_ENGINEERING_PART.get());
                        output.accept(ModItems.ASSAULT_DRONE_PART.get());
                        output.accept(ModItems.LAST_ORDER_PART.get());
                        output.accept(ModItems.LINE_FORMATION_PART.get());
                        output.accept(ModItems.WING_COMMANDER_PART.get());
                        output.accept(ModItems.DEFENSE_DRONE_PART.get());
                        output.accept(ModItems.COUNTER_PULSE_PART.get());
                        output.accept(ModItems.ARC_BARRIER_PART.get());
                        output.accept(ModItems.RESHAPING_PART.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}