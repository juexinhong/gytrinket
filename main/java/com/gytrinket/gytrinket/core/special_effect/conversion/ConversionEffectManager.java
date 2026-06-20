package com.gytrinket.gytrinket.core.special_effect.conversion;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.modifier.player.health.PlayerHealthManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class ConversionEffectManager {

    private static final String DYNAMIC_ATTRIBUTE_KEY = "conversion";

    private static final Map<UUID, ConversionBaseValues> PLAYER_BASE_VALUES = new ConcurrentHashMap<>();

    private static class ConversionBaseValues {
        double shield;
        double health;

        ConversionBaseValues(double shield, double health) {
            this.shield = shield;
            this.health = health;
        }
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            removeConversionAttributes(playerUUID);
            PLAYER_BASE_VALUES.remove(playerUUID);
            return;
        }

        boolean hasConversionItem = false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack) && Config.isConversionItem(stack.getItem())) {
                hasConversionItem = true;
                break;
            }
        }

        if (!hasConversionItem) {
            removeConversionAttributes(playerUUID);
            PLAYER_BASE_VALUES.remove(playerUUID);
            return;
        }

        // 步骤1：立即清除转化施加的动态属性
        removeConversionAttributes(playerUUID);

        // 步骤2：确保获取到的是应用了所有修饰符后的生命值
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }

        // 获取护盾值（属性组计算后的值）
        double shieldValue = AttributeManager.getGroupAttribute(playerUUID, "shield");

        // 获取玩家最大生命值（确保应用了修饰符后的值）
        double healthValue = 20.0;
        if (player != null && player.isAlive()) {
            // 先确保生命修饰符已应用
            double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
            double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
            double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

            // 如果有生命属性需要应用，手动调用 PlayerHealthManager 的逻辑
            if (healthBase != 0 || healthPercent != 1.0 || healthIndependent != 1.0) {
                // 应用生命修饰符到玩家
                PlayerHealthManager.onAttributesCalculated(event);
            }

            // 现在获取的就是应用了修饰符后的生命值
            healthValue = player.getMaxHealth();
        }

        // 保存到映射中，防止循环膨胀
        PLAYER_BASE_VALUES.put(playerUUID, new ConversionBaseValues(shieldValue, healthValue));

        // 步骤3：执行转化
        // 步骤4：设置动态属性
        performConversion(playerUUID);
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

        // 设置动态属性（独立乘区）
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