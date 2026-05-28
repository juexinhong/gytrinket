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
 * 攻击速度修饰符管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，获取攻击速度属性并应用
 * 2. 使用 MULTIPLY_TOTAL 操作，应用百分比加成
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackSpeedManager {

    private static final String MODIFIER_NAME = "player_attack_speed_modifier";
    private static final UUID MODIFIER_UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-012345678902");

    private static final Map<UUID, Double> PLAYER_ATTACK_SPEED_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double attackSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_percent");
        double attackSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_independent");

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

        // 计算总倍数
        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        if (totalMultiplier != 1.0) {
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL, MODIFIER_UUID, MODIFIER_NAME);
        } else {
            removeModifier(player, MODIFIER_UUID);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("attack_speed_percent") && !attrName.equals("attack_speed_independent")) {
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

        double attackSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_percent");
        double attackSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_independent");

        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        if (totalMultiplier != 1.0) {
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL, MODIFIER_UUID, MODIFIER_NAME);
        } else {
            removeModifier(player, MODIFIER_UUID);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    private static void addModifier(Player player, double value, AttributeModifier.Operation operation, UUID modifierUuid, String modifierName) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }

        removeModifier(player, modifierUuid);

        AttributeModifier modifier = new AttributeModifier(modifierUuid, modifierName, value, operation);
        attribute.addPermanentModifier(modifier);
    }

    private static void removeModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
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
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        removeModifier(player, MODIFIER_UUID);
        PLAYER_ATTACK_SPEED_MAP.remove(player.getUUID());
    }

    public static double getPlayerAttackSpeed(UUID playerUUID) {
        return PLAYER_ATTACK_SPEED_MAP.getOrDefault(playerUUID, 4.0);
    }

    public static void clearAllData() {
        PLAYER_ATTACK_SPEED_MAP.clear();
    }
}