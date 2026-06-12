package com.gy_mod.gy_trinket.core.tooltip;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TooltipHandler {

    private static final String TOOLTIP_PREFIX = "tooltip.gytrinket.";

    // 配置驱动的工具提示规则列表
    private static final List<TooltipConfig> TOOLTIP_RULES = createTooltipRules();

    private static List<TooltipConfig> createTooltipRules() {
        List<TooltipConfig> rules = new ArrayList<>();

        // 电气放电
        rules.add(new TooltipConfig(
            Config.ELECTRIC_DISCHARGE_ITEMS,
            "electric_discharge", "electric_discharge_effect",
            ChatFormatting.AQUA
        ));

        // 攻击冷却效率
        rules.add(new TooltipConfig(
            Config.ATTACK_COOLDOWN_EFFICIENCY_ITEMS,
            "efficiency", "efficiency_effect",
            ChatFormatting.GOLD,
            () -> new Object[]{"20"}
        ));

        // 护盾自然恢复
        rules.add(new TooltipConfig(
            Config.SHIELD_NATURAL_RECOVERY_ITEMS,
            "regen_shield", "regen_shield_effect",
            ChatFormatting.GREEN,
            () -> new Object[]{
                (int)(Config.getNaturalRecoveryShieldPresentHealthModifier() * 100),
                (int)(Config.getNaturalRecoveryShieldPresentShieldModifier() * 100)
            }
        ));

        // 反射伤害
        rules.add(new TooltipConfig(
            Config.REFLECT_DAMAGE_ITEMS,
            "reflect_damage", "reflect_damage_effect",
            ChatFormatting.RED
        ));

        // 爆炸护盾
        rules.add(new TooltipConfig(
            Config.EXPLOSIVE_SHIELD_ITEMS,
            "explosive_shield", "explosive_shield_effect",
            ChatFormatting.DARK_RED,
            () -> new Object[]{Config.EXPLOSIVE_SHIELD_DAMAGE.get()}
        ));

        // 护盾转移
        rules.add(new TooltipConfig(
            Config.SHIELD_TRANSFER_ITEMS,
            "shield_transfer", "shield_transfer_effect",
            ChatFormatting.LIGHT_PURPLE,
            () -> new Object[]{Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get() * 100}
        ));

        // 二进制协议
        rules.add(new TooltipConfig(
            Config.BINARY_PROTOCOL_ITEMS,
            "binary_protocol", "binary_protocol_effect",
            ChatFormatting.DARK_GREEN,
            () -> new Object[]{50}
        ));

        // 武装护盾
        rules.add(new TooltipConfig(
            Config.WEAPONIZED_SHIELD_ITEMS,
            "weaponized_shield", "weaponized_shield_effect",
            ChatFormatting.RED,
            () -> new Object[]{(int)(Config.WEAPONIZED_SHIELD_VULNERABILITY.get() * 100)}
        ));

        // 濒死保护
        rules.add(new TooltipConfig(
            Config.NEAR_DEATH_PROTECTION_ITEMS,
            "near_death_protection", "near_death_protection_effect",
            ChatFormatting.GREEN,
            () -> new Object[]{
                Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get() / 20.0,
                Config.NEAR_DEATH_PROTECTION_COOLDOWN.get() / 20.0
            }
        ));

        // 濒死爆炸
        rules.add(new TooltipConfig(
            Config.NEAR_DEATH_EXPLOSION_ITEMS,
            "near_death_explosion", "near_death_explosion_effect",
            ChatFormatting.RED,
            () -> new Object[]{
                Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get() / 20.0,
                Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get(),
                Config.NEAR_DEATH_EXPLOSION_RADIUS.get()
            }
        ));

        // 追击阵列
        rules.add(new TooltipConfig(
            Config.PURSUIT_ARRAY_REQUIRED_ITEMS,
            "pursuit_array", "pursuit_array_effect",
            ChatFormatting.GOLD
        ));

        // 编队阵列
        rules.add(new TooltipConfig(
            Config.FORMATION_ARRAY_REQUIRED_ITEMS,
            "formation_array", "formation_array_effect",
            ChatFormatting.AQUA
        ));

        // 守卫阵列
        rules.add(new TooltipConfig(
            Config.GUARD_ARRAY_REQUIRED_ITEMS,
            "guard_array", "guard_array_effect",
            ChatFormatting.BLUE
        ));

        // 弧形屏障
        rules.add(new TooltipConfig(
            Config.ARC_BARRIER_ITEMS,
            "arc_barrier", "arc_barrier_effect",
            ChatFormatting.DARK_AQUA,
            () -> new Object[]{Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get()}
        ));

        // 重塑
        rules.add(new TooltipConfig(
            Config.RESHAPING_ITEMS,
            "reshaping", "reshaping_effect",
            ChatFormatting.GREEN,
            () -> new Object[]{
                Config.RESHAPING_HEAL_RATE.get() * 100,
                Config.RESHAPING_BASE_DAMAGE_REDUCTION.get(),
                Config.RESHAPING_DAMAGE_REDUCTION_DURATION.get() / 20.0
            }
        ));

        // 反击脉冲
        rules.add(new TooltipConfig(
            Config.COUNTER_PULSE_ITEMS,
            "counter_pulse", "counter_pulse_effect",
            ChatFormatting.RED,
            () -> new Object[]{
                Config.COUNTER_PULSE_COOLDOWN.get() / 20.0,
                Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get(),
                Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get(),
                Config.COUNTER_PULSE_CHARGE_INTERVAL.get(),
                Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.get()
            }
        ));

        // 精密构造
        rules.add(new TooltipConfig(
            Config.PRECISION_CONSTRUCT_ITEMS,
            "precision_construct", "precision_construct_effect",
            ChatFormatting.AQUA,
            () -> new Object[]{Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get() * 100}
        ));

        // 高级工程
        rules.add(new TooltipConfig(
            Config.ADVANCED_ENGINEERING_ITEMS,
            "advanced_engineering", "advanced_engineering_effect",
            ChatFormatting.GOLD,
            () -> new Object[]{Config.ADVANCED_ENGINEERING_BONUS_PER_LEVEL.get() * 100}
        ));

        // 指挥官
        rules.add(new TooltipConfig(
            Config.COMMANDER_REQUIRED_ITEMS,
            "commander", "commander_effect",
            ChatFormatting.LIGHT_PURPLE,
            () -> new Object[]{
                Config.COMMANDER_MAX_COUNT.get(),
                Config.COMMANDER_APPOINT_DELAY.get() / 20.0
            }
        ));

        // 强袭
        rules.add(new TooltipConfig(
            Config.ASSAULT_ITEMS,
            "assault", "assault_effect",
            ChatFormatting.RED
        ));

        // 充能攻击
        rules.add(new TooltipConfig(
            Config.CHARGED_ATTACK_ITEMS,
            "charged_attack", "charged_attack_effect",
            ChatFormatting.GOLD
        ));

        // 充能护盾
        rules.add(new TooltipConfig(
            Config.CHARGED_SHIELD_ITEMS,
            "charged_shield", "charged_shield_effect",
            ChatFormatting.AQUA,
            () -> new Object[]{
                (int)(Config.getChargedShieldChargeRatio() * 100),
                (int)(Config.getChargedShieldMaxBonus() * 100)
            }
        ));

        return rules;
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        net.minecraft.resources.ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) {
            return;
        }

        String itemId = itemKey.toString();

        // 特殊工具提示（逻辑独特，保持原有方法）
        addItemAttributesTooltip(event, itemId);
        addShieldTypesTooltip(event, itemId);
        addModuleTooltips(event, itemId);
        addAdaptiveArmorTooltip(event, itemId);

        // 配置驱动的通用工具提示
        for (TooltipConfig config : TOOLTIP_RULES) {
            addConfiguredTooltip(event, config);
        }
    }

    /**
     * 通用的配置驱动工具提示处理方法
     * 替代所有重复的 add*Tooltip 方法
     */
    private static void addConfiguredTooltip(ItemTooltipEvent event, TooltipConfig config) {
        String itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (!config.matchesItem(itemId)) {
            return;
        }

        event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));

        if (config.hasTitle()) {
            addTooltip(event, config.getTitleKey(), config.getTitleColor());
        }

        if (config.needsFormatting()) {
            addFormattedTooltip(event, config.getDescriptionKey(), ChatFormatting.GRAY, config.getFormatter());
        } else if (config.getDescriptionKey() != null) {
            addTooltip(event, config.getDescriptionKey(), ChatFormatting.GRAY);
        }
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
                } else if (type.equals("siphon")) {
                    formattedDesc = String.format(descTooltip.getString(),
                        Config.SIPHON_TICK_INTERVAL.get(),
                        Config.SIPHON_DAMAGE.get(),
                        (int)(Config.SIPHON_HEAL_RATIO.get() * 100),
                        (int)(Config.SIPHON_MAX_EFFECT.get() * 100));
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
            addDroneModuleDescTooltip(event);
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
            addFormattedTooltip(event, "barrier_effect", ChatFormatting.DARK_PURPLE,
                () -> new Object[]{5, 5});
        }
    }

    /**
     * 无人机模块描述工具提示（需要特殊的动态参数计算）
     */
    private static void addDroneModuleDescTooltip(ItemTooltipEvent event) {
        String translationKey = TOOLTIP_PREFIX + "drone_module_desc";
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                ConstructType droneType = ConstructManager.getInstance().getConstructType(DroneConstructTypes.DRONE);
                int maxCount = droneType != null ? droneType.getMaxCount() : 3;
                double maxHealth = droneType != null ? droneType.getMaxHealth() : 5.0;
                double attackSpeed = 1.0 / Config.ORBIT_ATTACK_INTERVAL.get();
                formattedText = String.format(formattedText,
                    maxCount, (int) maxHealth, DroneBullet.BASE_DAMAGE, attackSpeed);
                event.getToolTip().add(Component.literal(formattedText).withStyle(ChatFormatting.GRAY));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void addAdaptiveArmorTooltip(ItemTooltipEvent event, String itemId) {
        boolean isAdaptiveArmorItem = Config.ADAPTIVE_ARMOR_ITEMS.get().contains(itemId);
        boolean isBondItem = Config.ADAPTIVE_ARMOR_SHIELD_EFFECT_ITEMS.get().contains(itemId);

        if (isAdaptiveArmorItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_enabled", ChatFormatting.GREEN);
            addTooltip(event, "adaptive_armor_effect", ChatFormatting.GRAY);
        }

        if (isBondItem) {
            event.getToolTip().add(Component.literal("").withStyle(ChatFormatting.GRAY));
            addTooltip(event, "adaptive_armor_bond", ChatFormatting.LIGHT_PURPLE);
            addTooltip(event, "adaptive_armor_bond_effect", ChatFormatting.GRAY);
        }
    }

    /**
     * 带格式化参数的工具提示
     */
    private static void addFormattedTooltip(ItemTooltipEvent event, String key, ChatFormatting color,
                                            TooltipFormatter formatter) {
        String translationKey = TOOLTIP_PREFIX + key;
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            String formattedText = tooltip.getString();
            try {
                formattedText = String.format(formattedText, formatter.formatParameters());
                event.getToolTip().add(Component.literal(formattedText).withStyle(color));
            } catch (Exception e) {
                event.getToolTip().add(tooltip.withStyle(color));
            }
        }
    }

    private static void addTooltip(ItemTooltipEvent event, String key, ChatFormatting color) {
        String translationKey = TOOLTIP_PREFIX + key;
        MutableComponent tooltip = Component.translatable(translationKey);

        if (!isDefaultTranslation(tooltip, translationKey)) {
            event.getToolTip().add(tooltip.withStyle(color));
        }
    }

    private static boolean isDefaultTranslation(Component component, String key) {
        return component.getString().equals(key);
    }
}
