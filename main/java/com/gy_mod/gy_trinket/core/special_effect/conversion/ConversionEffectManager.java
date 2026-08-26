package com.gy_mod.gy_trinket.core.special_effect.conversion;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.modifier.player.health.PlayerHealthManager;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class ConversionEffectManager {

    private static final String DYNAMIC_ATTRIBUTE_KEY = "conversion";

    private static final Map<UUID, ConversionBaseValues> PLAYER_BASE_VALUES = new ConcurrentHashMap<>();

    private static class ConversionBaseValues {
        double shield;
        double health;
        double healthMult;

        ConversionBaseValues(double shield, double health, double healthMult) {
            this.shield = shield;
            this.health = health;
            this.healthMult = healthMult;
        }
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        if (!hasConversionItem(playerUUID)) {
            removeConversionAttributes(playerUUID);
            PLAYER_BASE_VALUES.remove(playerUUID);
            return;
        }

        // 确保生命修饰符已应用，maxHealth 为当前正确值（幂等，不触发重算）
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }
        PlayerHealthManager.onAttributesCalculated(event);

        // 基础护盾：排除转化命名空间的贡献（不物理移除动态属性，避免触发重算循环）
        double shieldBase = AttributeManager.getGroupAttributeExcludingNamespace(playerUUID, "shield", "conversion");

        // 基础生命：用当前 maxHealth 反推，除以转化独立乘区贡献 (1 + 上次转化生命倍率)
        ConversionBaseValues prev = PLAYER_BASE_VALUES.get(playerUUID);
        double lastHealthMult = prev != null ? prev.healthMult : 0.0;
        double healthBase = player.getMaxHealth() / (1.0 + lastHealthMult);

        PLAYER_BASE_VALUES.put(playerUUID, new ConversionBaseValues(shieldBase, healthBase, lastHealthMult));

        // 计算并设置转化倍率（setDynamicAttribute 值未变化时跳过，避免循环）
        performConversion(playerUUID);
    }

    private static boolean hasConversionItem(UUID playerUUID) {
        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack) && Config.isConversionItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static void performConversion(UUID playerUUID) {
        ConversionBaseValues baseValues = PLAYER_BASE_VALUES.get(playerUUID);
        if (baseValues == null) {
            return;
        }

        double shield = baseValues.shield;
        double health = baseValues.health;

        shield = Math.max(shield, 0.0001);
        health = Math.max(health, 0.0001);

        double healthMultiplier = 1.0;
        double shieldMultiplier = 1.0;

        double conversionRatio = Config.CONVERSION_RATIO.get();

        if (health <= shield) {
            // 生命值 <= 护盾值：将生命转化给护盾
            double convertAmount = health * conversionRatio;
            healthMultiplier = (health - convertAmount) / health - 1;
            shieldMultiplier = (shield + convertAmount) / shield - 1;
        } else {
            // 护盾值 < 生命值：将护盾转化给生命
            double convertAmount = shield * conversionRatio;
            shieldMultiplier = (shield - convertAmount) / shield - 1;
            healthMultiplier = (health + convertAmount) / health - 1;
        }

        // 记录当前转化生命倍率，供下次反推基础生命
        baseValues.healthMult = healthMultiplier;

        // 设置动态属性（独立乘区）；值未变化时 setDynamicAttribute 会跳过，不会继续触发重算
        AttributeManager.setDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "player_health_independent", healthMultiplier);
        AttributeManager.setDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "shield_independent", shieldMultiplier);
    }

    private static void removeConversionAttributes(UUID playerUUID) {
        AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "player_health_independent");
        AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "shield_independent");
    }

    public static void clearPlayerData(UUID playerUUID) {
        removeConversionAttributes(playerUUID);
        PLAYER_BASE_VALUES.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_BASE_VALUES.clear();
    }
}
