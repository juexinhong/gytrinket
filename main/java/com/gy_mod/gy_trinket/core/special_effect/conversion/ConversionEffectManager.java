package com.gy_mod.gy_trinket.core.special_effect.conversion;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.modifier.player.health.PlayerHealthManager;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
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

        // 获取排除转化自身影响后的护盾值，避免循环膨胀
        double shieldValue = AttributeManager.getGroupAttributeExcludingNamespace(playerUUID, "shield", DYNAMIC_ATTRIBUTE_KEY);

        // 获取玩家最大生命值
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }

        double healthValue = 20.0;
        if (player != null && player.isAlive()) {
            double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
            double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
            double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

            if (healthBase != 0 || healthPercent != 1.0 || healthIndependent != 1.0) {
                PlayerHealthManager.onAttributesCalculated(event);
            }

            healthValue = player.getMaxHealth();
        }

        PLAYER_BASE_VALUES.put(playerUUID, new ConversionBaseValues(shieldValue, healthValue));

        // 直接计算并设置（不再先清除再设置）
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
            double convertAmount = health * conversionRatio;
            healthMultiplier = (health - convertAmount) / health - 1;
            shieldMultiplier = (shield + convertAmount) / shield - 1;
        } else {
            double convertAmount = shield * conversionRatio;
            shieldMultiplier = (shield - convertAmount) / shield - 1;
            healthMultiplier = (health + convertAmount) / health - 1;
        }

        // 直接设置，setDynamicAttribute 内部会检查值是否变化，相同则跳过
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
