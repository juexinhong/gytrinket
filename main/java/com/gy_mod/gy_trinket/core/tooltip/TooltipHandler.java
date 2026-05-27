package com.gy_mod.gy_trinket.core.tooltip;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TooltipHandler {

    private static final String TOOLTIP_PREFIX = "tooltip.gytrinket.";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        net.minecraft.resources.ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) {
            return;
        }
        
        String itemId = itemKey.toString();
        
        addItemAttributesTooltip(event, itemId);
        addShieldTypesTooltip(event, itemId);
        addModuleTooltips(event, itemId);
        addAdaptiveArmorTooltip(event, itemId);
        addElectricDischargeTooltip(event, itemId);
        addAttackCooldownEfficiencyTooltip(event, itemId);
        addBarrierTooltip(event, itemId);
        addShieldNaturalRecoveryTooltip(event, itemId);
        addReflectDamageTooltip(event, itemId);
        addExplosiveShieldTooltip(event, itemId);
        addShieldTransferTooltip(event, itemId);
        addBinaryProtocolTooltip(event, itemId);
        addWeaponizedShieldTooltip(event, itemId);
        addNearDeathProtectionTooltip(event, itemId);
        addNearDeathExplosionTooltip(event, itemId);
        addPursuitArrayTooltip(event, itemId);
        addFormationArrayTooltip(event, itemId);
        addGuardArrayTooltip(event, itemId);
        addArcBarrierTooltip(event, itemId);
        addReshapingTooltip(event, itemId);
        addCounterPulseTooltip(event, itemId);
        addPrecisionConstructTooltip(event, itemId);
        addAdvancedEngineeringTooltip(event, itemId);
        addCommanderTooltip(event, itemId);
    }
    
    private static void addItemAttributesTooltip(ItemTooltipEvent event, String itemId) {
        List<? extends String> itemAttributesConfig = Config.ITEM_ATTRIBUTES_CONFIG.get();
        
        for (String configLine : itemAttributesConfig) {
            if (configLine.startsWith(itemId + "|")) {
                String attributesPart = configLine.substring(itemId.length() + 1);
                String[] attrPairs = attributesPart.split("\\|");
                
                event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.literal("属性:").withStyle(ChatFormatting.GOLD));
                
                for (String attrPair : attrPairs) {
                    String[] parts = attrPair.split("=");
                    if (parts.length == 2) {
                        String attrName = parts[0];
                        String attrValue = parts[1];
                        
                        Component attrTooltip = Component.translatable(TOOLTIP_PREFIX + "attr." + attrName)
                            .withStyle(ChatFormatting.WHITE);
                        
                        if (isDefaultTranslation(attrTooltip, TOOLTIP_PREFIX + "attr." + attrName)) {
                            attrTooltip = Component.literal(attrName).withStyle(ChatFormatting.WHITE);
                        }
                        
                        event.getToolTip().add(Component.literal("  +").withStyle(ChatFormatting.GREEN)
                            .append(attrTooltip)
                            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(attrValue).withStyle(ChatFormatting.YELLOW)));
                    }
                }
                break;
            }
        }
    }
    
    private static void addShieldTypesTooltip(ItemTooltipEvent event, String itemId) {
        List<? extends String> itemShieldTypesConfig = Config.ITEM_SHIELD_TYPES_CONFIG.get();
        
        for (String configLine : itemShieldTypesConfig) {
            if (configLine.startsWith(itemId + "|")) {
                String shieldTypes = configLine.substring(itemId.length() + 1);
                
                event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.literal("护盾类型:").withStyle(ChatFormatting.GOLD));
                
                String[] types = shieldTypes.split(",");
                for (String type : types) {
                    Component typeTooltip = Component.translatable(TOOLTIP_PREFIX + "shield_type." + type)
                        .withStyle(ChatFormatting.WHITE);
                    
                    if (isDefaultTranslation(typeTooltip, TOOLTIP_PREFIX + "shield_type." + type)) {
                        typeTooltip = Component.literal(type).withStyle(ChatFormatting.WHITE);
                    }
                    
                    event.getToolTip().add(Component.literal("  +").withStyle(ChatFormatting.GREEN)
                        .append(typeTooltip));
                    
                    // 添加护盾类型描述
                    addShieldTypeDescriptionTooltip(event, type);
                }
                break;
            }
        }
    }
    
    private static void addShieldTypeDescriptionTooltip(ItemTooltipEvent event, String type) {
        String descKey = TOOLTIP_PREFIX + "shield_type." + type + "_desc";
        Component descTooltip = Component.translatable(descKey);
        
        if (!isDefaultTranslation(descTooltip, descKey)) {
            // 根据护盾类型选择不同的格式化方式
            String formattedDesc = descTooltip.getString();
            try {
                if (type.equals("aura")) {
                    formattedDesc = String.format(descTooltip.getString(), 
                        Config.getIgniteDefaultDamage(), 
                        Config.getIgniteDefaultDuration());
                } else if (type.equals("reflect")) {
                    formattedDesc = String.format(descTooltip.getString(), 
                        (int)(Config.getReflectSpeedBaseModifier() * 100), 
                        Config.getReflectDamageEffectMultiplier());
                } else if (type.equals("amplification")) {
                    formattedDesc = String.format(descTooltip.getString(), 
                        (int)(Config.getAmplificationBaseAmplification() * 100), 
                        (int)(Config.getAmplificationMaxAmplification() * 100));
                } else if (type.equals("warp")) {
                    formattedDesc = String.format(descTooltip.getString(), 
                        Config.getWarpShieldExplosionDamage());
                }
                event.getToolTip().add(Component.literal("    ").append(Component.literal(formattedDesc).withStyle(ChatFormatting.GRAY)));
            } catch (Exception e) {
                event.getToolTip().add(Component.literal("    ").append(descTooltip.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
    }
    
    private static void addModuleTooltips(ItemTooltipEvent event, String itemId) {
        if (Config.DRONE_MODULE_ITEMS.get().contains(itemId)) {
            addTooltip(event, "drone_module", ChatFormatting.GRAY);
            addFormattedTooltip(event, "drone_module_desc", ChatFormatting.GRAY);
        }
        
        if (Config.ASSAULT_DRONE_MODULE_ITEMS.get().contains(itemId)) {
            addTooltip(event, "assault_drone_module", ChatFormatting.GOLD);
        }
        
        if (Config.DEFENSE_DRONE_MODULE_ITEMS.get().contains(itemId)) {
            addTooltip(event, "defense_drone_module", ChatFormatting.BLUE);
        }
        
        if (Config.ADAPTIVE_ARMOR_ITEMS.get().contains(itemId)) {
            addTooltip(event, "adaptive_armor", ChatFormatting.GREEN);
        }
        
        if (Config.BARRIER_ITEMS.get().contains(itemId)) {
            addTooltip(event, "barrier", ChatFormatting.DARK_PURPLE);
        }
    }
    
    private static void addAdaptiveArmorTooltip(ItemTooltipEvent event, String itemId) {
        boolean isAdaptiveArmorItem = Config.ADAPTIVE_ARMOR_ITEMS.get().contains(itemId);
        boolean isBondItem = Config.ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEMS.get().contains(itemId);
        
        if (isAdaptiveArmorItem) {
            // 适应性装甲启用物品的描述
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_enabled", ChatFormatting.GREEN);
            addTooltip(event, "adaptive_armor_effect", ChatFormatting.GRAY);
        }
        
        if (isBondItem) {
            // 联结物品的描述
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_bond", ChatFormatting.LIGHT_PURPLE);
            addTooltip(event, "adaptive_armor_bond_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addElectricDischargeTooltip(ItemTooltipEvent event, String itemId) {
        boolean isElectricDischargeItem = Config.ELECTRIC_DISCHARGE_ITEMS.get().contains(itemId);
        
        if (isElectricDischargeItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "electric_discharge", ChatFormatting.AQUA);
            addTooltip(event, "electric_discharge_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addAttackCooldownEfficiencyTooltip(ItemTooltipEvent event, String itemId) {
        boolean isEfficiencyItem = Config.ATTACK_COOLDOWN_EFFICIENCY_ITEMS.get().contains(itemId);
        
        if (isEfficiencyItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "efficiency", ChatFormatting.GOLD);
            addFormattedTooltip(event, "efficiency_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addBarrierTooltip(ItemTooltipEvent event, String itemId) {
        boolean isBarrierItem = Config.BARRIER_ITEMS.get().contains(itemId);
        
        if (isBarrierItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addFormattedTooltip(event, "barrier_effect", ChatFormatting.DARK_PURPLE);
        }
    }
    
    private static void addShieldNaturalRecoveryTooltip(ItemTooltipEvent event, String itemId) {
        boolean isNaturalRecoveryItem = Config.SHIELD_NATURAL_RECOVERY_ITEMS.get().contains(itemId);
        
        if (isNaturalRecoveryItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "regen_shield", ChatFormatting.GREEN);
            addFormattedTooltip(event, "regen_shield_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addReflectDamageTooltip(ItemTooltipEvent event, String itemId) {
        boolean isReflectDamageItem = Config.REFLECT_DAMAGE_ITEMS.get().contains(itemId);
        
        if (isReflectDamageItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "reflect_damage", ChatFormatting.RED);
            addTooltip(event, "reflect_damage_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addExplosiveShieldTooltip(ItemTooltipEvent event, String itemId) {
        boolean isExplosiveShieldItem = Config.EXPLOSIVE_SHIELD_ITEMS.get().contains(itemId);
        
        if (isExplosiveShieldItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "explosive_shield", ChatFormatting.DARK_RED);
            addFormattedTooltip(event, "explosive_shield_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addShieldTransferTooltip(ItemTooltipEvent event, String itemId) {
        boolean isShieldTransferItem = Config.SHIELD_TRANSFER_ITEMS.get().contains(itemId);
        
        if (isShieldTransferItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "shield_transfer", ChatFormatting.LIGHT_PURPLE);
            addFormattedTooltip(event, "shield_transfer_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addBinaryProtocolTooltip(ItemTooltipEvent event, String itemId) {
        boolean isBinaryProtocolItem = Config.BINARY_PROTOCOL_ITEMS.get().contains(itemId);
        
        if (isBinaryProtocolItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "binary_protocol", ChatFormatting.DARK_GREEN);
            addFormattedTooltip(event, "binary_protocol_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addWeaponizedShieldTooltip(ItemTooltipEvent event, String itemId) {
        boolean isWeaponizedShieldItem = Config.WEAPONIZED_SHIELD_ITEMS.get().contains(itemId);
        
        if (isWeaponizedShieldItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "weaponized_shield", ChatFormatting.RED);
            addFormattedTooltip(event, "weaponized_shield_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addNearDeathProtectionTooltip(ItemTooltipEvent event, String itemId) {
        boolean isNearDeathProtectionItem = Config.NEAR_DEATH_PROTECTION_ITEMS.get().contains(itemId);
        
        if (isNearDeathProtectionItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "near_death_protection", ChatFormatting.GREEN);
            addFormattedTooltip(event, "near_death_protection_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addNearDeathExplosionTooltip(ItemTooltipEvent event, String itemId) {
        boolean isNearDeathExplosionItem = Config.NEAR_DEATH_EXPLOSION_ITEMS.get().contains(itemId);
        
        if (isNearDeathExplosionItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "near_death_explosion", ChatFormatting.RED);
            addFormattedTooltip(event, "near_death_explosion_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addPursuitArrayTooltip(ItemTooltipEvent event, String itemId) {
        boolean isPursuitArrayItem = Config.PURSUIT_ARRAY_REQUIRED_ITEMS.get().contains(itemId);
        
        if (isPursuitArrayItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "pursuit_array", ChatFormatting.GOLD);
            addTooltip(event, "pursuit_array_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addFormationArrayTooltip(ItemTooltipEvent event, String itemId) {
        boolean isFormationArrayItem = Config.FORMATION_ARRAY_REQUIRED_ITEMS.get().contains(itemId);
        
        if (isFormationArrayItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "formation_array", ChatFormatting.AQUA);
            addTooltip(event, "formation_array_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addGuardArrayTooltip(ItemTooltipEvent event, String itemId) {
        boolean isGuardArrayItem = Config.GUARD_ARRAY_REQUIRED_ITEMS.get().contains(itemId);
        
        if (isGuardArrayItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "guard_array", ChatFormatting.BLUE);
            addTooltip(event, "guard_array_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addArcBarrierTooltip(ItemTooltipEvent event, String itemId) {
        boolean isArcBarrierItem = Config.ARC_BARRIER_ITEMS.get().contains(itemId);
        
        if (isArcBarrierItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "arc_barrier", ChatFormatting.DARK_AQUA);
            addFormattedTooltip(event, "arc_barrier_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addReshapingTooltip(ItemTooltipEvent event, String itemId) {
        boolean isReshapingItem = Config.RESHAPING_ITEMS.get().contains(itemId);
        
        if (isReshapingItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "reshaping", ChatFormatting.GREEN);
            addFormattedTooltip(event, "reshaping_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addCounterPulseTooltip(ItemTooltipEvent event, String itemId) {
        boolean isCounterPulseItem = Config.COUNTER_PULSE_ITEMS.get().contains(itemId);
        
        if (isCounterPulseItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "counter_pulse", ChatFormatting.RED);
            addFormattedTooltip(event, "counter_pulse_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addPrecisionConstructTooltip(ItemTooltipEvent event, String itemId) {
        boolean isPrecisionConstructItem = Config.PRECISION_CONSTRUCT_ITEMS.get().contains(itemId);
        
        if (isPrecisionConstructItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "precision_construct", ChatFormatting.AQUA);
            addFormattedTooltip(event, "precision_construct_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addAdvancedEngineeringTooltip(ItemTooltipEvent event, String itemId) {
        boolean isAdvancedEngineeringItem = Config.ADVANCED_ENGINEERING_ITEMS.get().contains(itemId);
        
        if (isAdvancedEngineeringItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "advanced_engineering", ChatFormatting.GOLD);
            addFormattedTooltip(event, "advanced_engineering_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addCommanderTooltip(ItemTooltipEvent event, String itemId) {
        boolean isCommanderItem = Config.COMMANDER_REQUIRED_ITEMS.get().contains(itemId);
        
        if (isCommanderItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "commander", ChatFormatting.LIGHT_PURPLE);
            addFormattedTooltip(event, "commander_effect", ChatFormatting.GRAY);
        }
    }
    
    private static void addFormattedTooltip(ItemTooltipEvent event, String key, ChatFormatting color) {
        String translationKey = TOOLTIP_PREFIX + key;
        net.minecraft.network.chat.MutableComponent tooltip = Component.translatable(translationKey);
        
        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                if (key.equals("efficiency_effect")) {
                    formattedText = String.format(formattedText, "20");
                } else if (key.equals("drone_module_desc")) {
                    ConstructType droneType = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
                    int maxCount = droneType != null ? droneType.getMaxCount() : 3;
                    double maxHealth = droneType != null ? droneType.getMaxHealth() : 5.0;
                    double attackSpeed = 1.0 / Config.ORBIT_ATTACK_INTERVAL.get();
                    formattedText = String.format(formattedText,
                        maxCount, (int) maxHealth, DroneBullet.BASE_DAMAGE, attackSpeed);
                } else if (key.equals("barrier_effect")) {
                    formattedText = String.format(formattedText, 5, 5);
                } else if (key.equals("regen_shield_effect")) {
                    formattedText = String.format(formattedText, 
                        (int)(Config.getNaturalRecoveryShieldPresentHealthModifier() * 100),
                        (int)(Config.getNaturalRecoveryShieldPresentShieldModifier() * 100));
                } else if (key.equals("explosive_shield_effect")) {
                    formattedText = String.format(formattedText, Config.EXPLOSIVE_SHIELD_DAMAGE.get());
                } else if (key.equals("binary_protocol_effect")) {
                    formattedText = String.format(formattedText, 50);
                } else if (key.equals("weaponized_shield_effect")) {
                    formattedText = String.format(formattedText, (int)(Config.WEAPONIZED_SHIELD_VULNERABILITY.get() * 100));
                } else if (key.equals("near_death_protection_effect")) {
                    formattedText = String.format(formattedText,
                        Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get() / 20.0,
                        Config.NEAR_DEATH_PROTECTION_COOLDOWN.get() / 20.0);
                } else if (key.equals("near_death_explosion_effect")) {
                    formattedText = String.format(formattedText,
                        Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get() / 20.0,
                        Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get(),
                        Config.NEAR_DEATH_EXPLOSION_RADIUS.get());
                } else if (key.equals("commander_effect")) {
                    formattedText = String.format(formattedText,
                        Config.COMMANDER_MAX_COUNT.get(),
                        Config.COMMANDER_APPOINT_DELAY.get() / 20.0);
                } else if (key.equals("arc_barrier_effect")) {
                    formattedText = String.format(formattedText,
                        Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get());
                } else if (key.equals("reshaping_effect")) {
                    formattedText = String.format(formattedText,
                        Config.RESHAPING_HEAL_RATE.get() * 100,
                        Config.RESHAPING_BASE_DAMAGE_REDUCTION.get(),
                        Config.RESHAPING_DAMAGE_REDUCTION_DURATION.get() / 20.0);
                } else if (key.equals("counter_pulse_effect")) {
                    formattedText = String.format(formattedText,
                        Config.COUNTER_PULSE_COOLDOWN.get() / 20.0,
                        Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get(),
                        Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get(),
                        Config.COUNTER_PULSE_CHARGE_INTERVAL.get(),
                        Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.get());
                } else if (key.equals("precision_construct_effect")) {
                    formattedText = String.format(formattedText,
                        Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get() * 100);
                } else if (key.equals("advanced_engineering_effect")) {
                    formattedText = String.format(formattedText,
                        Config.ADVANCED_ENGINEERING_BONUS_PER_LEVEL.get() * 100);
                } else if (key.equals("shield_transfer_effect")) {
                    formattedText = String.format(formattedText,
                        Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get() * 100);
                }
                event.getToolTip().add(Component.literal(formattedText).withStyle(color));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(color));
            }
        }
    }
    
    private static void addTooltip(ItemTooltipEvent event, String key, ChatFormatting color) {
        String translationKey = TOOLTIP_PREFIX + key;
        net.minecraft.network.chat.MutableComponent tooltip = Component.translatable(translationKey);
        
        if (!isDefaultTranslation(tooltip, translationKey)) {
            event.getToolTip().add(tooltip.withStyle(color));
        }
    }
    
    private static boolean isDefaultTranslation(Component component, String key) {
        return component.getString().equals(key);
    }
}