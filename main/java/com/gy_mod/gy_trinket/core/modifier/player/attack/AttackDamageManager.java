package com.gy_mod.gy_trinket.core.modifier.player.attack;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击伤害修饰符管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，获取攻击伤害属性并应用
 * 2. attack_damage_base：使用 ADDITION 操作，加到底数
 * 3. attack_damage_percent：使用 MULTIPLY_TOTAL 操作，应用百分比加成（原版会自动+1）
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackDamageManager {

    private static final String BASE_MODIFIER_NAME = "player_attack_damage_base_modifier";
    private static final String PERCENT_MODIFIER_NAME = "player_attack_damage_bonus_percent_modifier";
    private static final UUID BASE_MODIFIER_UUID = UUID.fromString("d2e3f4a5-b6c7-8901-def0-123456789013");
    private static final UUID PERCENT_MODIFIER_UUID = UUID.fromString("e3f4a5b6-c7d8-9012-ef01-234567890124");

    private static final Map<UUID, Double> PLAYER_ATTACK_DAMAGE_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

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

        // 计算总倍数 (百分比和独立相乘)
        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        if (attackDamageBase != 0) {
            addModifier(player, attackDamageBase, AttributeModifier.Operation.ADDITION, BASE_MODIFIER_UUID, BASE_MODIFIER_NAME);
        } else {
            removeModifier(player, BASE_MODIFIER_UUID);
        }

        if (totalMultiplier != 1.0) {
            // 原版MULTIPLY_TOTAL会自动+1，所以需要-1来补偿
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL, PERCENT_MODIFIER_UUID, PERCENT_MODIFIER_NAME);
        } else {
            removeModifier(player, PERCENT_MODIFIER_UUID);
        }

        PLAYER_ATTACK_DAMAGE_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private static void addModifier(Player player, double value, AttributeModifier.Operation operation, UUID modifierUuid, String modifierName) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }

        removeModifier(player, modifierUuid);

        AttributeModifier modifier = new AttributeModifier(modifierUuid, modifierName, value, operation);
        attribute.addPermanentModifier(modifier);
    }

    private static void removeModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
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

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("attack_damage_percent") && !attrName.equals("attack_damage_independent")) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null || !player.isAlive()) {
            return;
        }

        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        if (attackDamageBase != 0) {
            addModifier(player, attackDamageBase, AttributeModifier.Operation.ADDITION, BASE_MODIFIER_UUID, BASE_MODIFIER_NAME);
        } else {
            removeModifier(player, BASE_MODIFIER_UUID);
        }

        if (totalMultiplier != 1.0) {
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL, PERCENT_MODIFIER_UUID, PERCENT_MODIFIER_NAME);
        } else {
            removeModifier(player, PERCENT_MODIFIER_UUID);
        }

        PLAYER_ATTACK_DAMAGE_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        removeModifier(player, BASE_MODIFIER_UUID);
        removeModifier(player, PERCENT_MODIFIER_UUID);
        PLAYER_ATTACK_DAMAGE_MAP.remove(player.getUUID());
    }

    public static double getPlayerAttackDamage(UUID playerUUID) {
        return PLAYER_ATTACK_DAMAGE_MAP.getOrDefault(playerUUID, 1.0);
    }

    public static void clearAllData() {
        PLAYER_ATTACK_DAMAGE_MAP.clear();
    }
}