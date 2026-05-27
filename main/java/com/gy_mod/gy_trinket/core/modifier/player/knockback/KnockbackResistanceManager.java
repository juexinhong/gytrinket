package com.gy_mod.gy_trinket.core.modifier.player.knockback;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击退抗性修饰符管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，获取击退抗性属性并应用为属性修饰符
 * 2. 使用 ADDITION 操作，直接应用底数值
 * 3. 监听LivingKnockBackEvent，当被击退目标是玩家且击退抗性为负数时，
 *    按比例增强玩家被击退的力量
 *    公式：增强系数 = 1.0 - 抗性值
 *    例如：抗性-0.1=增强10%(系数1.1)，抗性-0.5=增强50%(系数1.5)，抗性-1.0=增强100%(系数2.0)
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class KnockbackResistanceManager {

    private static final String MODIFIER_NAME = "player_knockback_resistance_modifier";
    private static final UUID MODIFIER_UUID = UUID.fromString("a5b6c7d8-e9f0-1234-0123-456789012346");

    private static final Map<UUID, Double> PLAYER_KNOCKBACK_RESISTANCE_MAP = new ConcurrentHashMap<>();

    /**
     * 监听属性计算完毕事件
     * 根据玩家击退抗性属性值应用修饰符（底数加成）
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double knockbackResistance = AttributeManager.getPlayerAttribute(playerUUID, "knockback_resistance");

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        if (knockbackResistance != 0) {
            addModifier(player, knockbackResistance, AttributeModifier.Operation.ADDITION, MODIFIER_UUID, MODIFIER_NAME);
        } else {
            removeModifier(player, MODIFIER_UUID);
        }

        PLAYER_KNOCKBACK_RESISTANCE_MAP.put(playerUUID, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    private static void addModifier(Player player, double value, AttributeModifier.Operation operation, UUID modifierUuid, String modifierName) {
        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        removeModifier(player, modifierUuid);

        AttributeModifier modifier = new AttributeModifier(modifierUuid, modifierName, value, operation);
        attribute.addPermanentModifier(modifier);
    }

    private static void removeModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(modifierUuid)) {
                attribute.removeModifier(modifier);
                break;
            }
        }
    }

    /**
     * 监听实体击退事件
     * 当被击退目标是玩家且击退抗性为负数时，增强玩家被击退的力量
     *
     * @param event 击退事件
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        double knockbackResistance = getPlayerKnockbackResistance(player.getUUID());
        if (knockbackResistance < 0) {
            float multiplier = (float) (1.0 - knockbackResistance);
            event.setStrength(event.getStrength() * multiplier);
        }
    }

    /**
     * 监听玩家登出事件，清理数据
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        removeModifier(player, MODIFIER_UUID);
        PLAYER_KNOCKBACK_RESISTANCE_MAP.remove(player.getUUID());
    }

    /**
     * 获取玩家的击退抗性属性值
     *
     * @param playerUUID 玩家UUID
     * @return 击退抗性属性值
     */
    public static double getPlayerKnockbackResistance(UUID playerUUID) {
        return PLAYER_KNOCKBACK_RESISTANCE_MAP.getOrDefault(playerUUID, 0.0);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        PLAYER_KNOCKBACK_RESISTANCE_MAP.clear();
    }
}