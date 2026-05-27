package com.gy_mod.gy_trinket.core.modifier.player.health;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
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
 * 玩家生命上限管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，获取玩家生命属性并应用
 * 2. 支持底数属性（player_health）和百分比加成属性（player_health_bonus）
 * 3. 使用原版修饰符系统，应用到玩家的 MAX_HEALTH 属性
 * 4. 玩家重生时恢复生命至上限
 * 5. 玩家退出时清理数据
 * <p>
 * 修饰符说明：
 * - player_health：使用 ADDITION 操作，直接加到底数
 * - player_health_bonus：使用 MULTIPLY_BASE 操作，应用百分比加成
 * - 原版会自己计算最终的最大生命值
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerHealthManager {

    /** 修饰符名称 */
    private static final String HEALTH_BASE_MODIFIER_NAME = "player_health_modifier";
    private static final String HEALTH_PERCENT_MODIFIER_NAME = "player_health_bonus_modifier";

    /** 修饰符UUID */
    private static final UUID HEALTH_BASE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID HEALTH_PERCENT_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    /** 玩家最大生命数据映射：玩家UUID -> 最大生命值 */
    private static final Map<UUID, Float> PLAYER_MAX_HEALTH_MAP = new ConcurrentHashMap<>();

    /**
     * 监听属性计算完毕事件
     * 获取玩家生命属性并作为修饰符应用到玩家身上
     *
     * @param event 属性计算完毕事件
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
        double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

        ServerPlayer player = event.getPlayer();
        
        // 如果事件没有携带玩家对象，从服务器获取
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                gytrinket.LOGGER.warn("玩家 {} 属性计算完毕事件，但服务器为空", playerUUID);
                return;
            }
            player = server.getPlayerList().getPlayer(playerUUID);
        }
        
        if (player == null || !player.isAlive()) {
            return;
        }

        // 计算总倍数 (百分比和独立相乘)
        double totalMultiplier = healthPercent * healthIndependent;

        if (healthBase != 0) {
            addModifier(player, healthBase, AttributeModifier.Operation.ADDITION, HEALTH_BASE_MODIFIER_UUID, HEALTH_BASE_MODIFIER_NAME);
        } else {
            removeModifier(player, HEALTH_BASE_MODIFIER_UUID);
        }

        if (totalMultiplier != 1.0) {
            // 原版修饰符乘法会自动+1，所以需要-1来补偿
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_BASE, HEALTH_PERCENT_MODIFIER_UUID, HEALTH_PERCENT_MODIFIER_NAME);
        } else {
            removeModifier(player, HEALTH_PERCENT_MODIFIER_UUID);
        }

        float maxHealth = player.getMaxHealth();
        PLAYER_MAX_HEALTH_MAP.put(playerUUID, maxHealth);

        // 确保玩家当前生命值不超过新上限，并在生命值低于上限时不自动补充
        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("player_health_percent") && 
            !attrName.equals("player_health_independent")) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null || !player.isAlive()) {
            return;
        }

        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
        double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");

        double totalMultiplier = healthPercent * healthIndependent;

        if (healthBase != 0) {
            addModifier(player, healthBase, AttributeModifier.Operation.ADDITION, HEALTH_BASE_MODIFIER_UUID, HEALTH_BASE_MODIFIER_NAME);
        } else {
            removeModifier(player, HEALTH_BASE_MODIFIER_UUID);
        }

        if (totalMultiplier != 1.0) {
            addModifier(player, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_BASE, HEALTH_PERCENT_MODIFIER_UUID, HEALTH_PERCENT_MODIFIER_NAME);
        } else {
            removeModifier(player, HEALTH_PERCENT_MODIFIER_UUID);
        }

        float maxHealth = player.getMaxHealth();
        PLAYER_MAX_HEALTH_MAP.put(playerUUID, maxHealth);

        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    /**
     * 添加修饰符到玩家的 MAX_HEALTH 属性
     *
     * @param player 玩家
     * @param value 修饰符值
     * @param operation 操作类型
     * @param modifierUuid 修饰符UUID
     * @param modifierName 修饰符名称
     */
    private static void addModifier(Player player, double value, AttributeModifier.Operation operation, UUID modifierUuid, String modifierName) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            gytrinket.LOGGER.warn("玩家 {} 无法获取 MAX_HEALTH 属性", player.getUUID());
            return;
        }

        removeModifier(player, modifierUuid);

        AttributeModifier modifier = new AttributeModifier(
            modifierUuid,
            modifierName,
            value,
            operation
        );

        attribute.addPermanentModifier(modifier);
    }

    /**
     * 从玩家的 MAX_HEALTH 属性移除修饰符
     *
     * @param player 玩家
     * @param modifierUuid 修饰符UUID
     */
    private static void removeModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
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
     * 监听玩家重生事件
     * 玩家重生时恢复生命至上限
     *
     * @param event 玩家重生事件
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        Float maxHealth = PLAYER_MAX_HEALTH_MAP.get(playerUUID);

        if (maxHealth != null && maxHealth > 0) {
            player.setHealth(maxHealth);
            gytrinket.LOGGER.debug("玩家 {} 重生，恢复生命至: {}", playerUUID, maxHealth);
        }
    }

    /**
     * 监听玩家退出事件
     * 清理缓存数据，防止内存泄漏
     * 注意：不清理原版的 persistent modifiers，让它们自动持久化
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        PLAYER_MAX_HEALTH_MAP.remove(playerUUID);
        gytrinket.LOGGER.debug("玩家 {} 退出，清理生命上限缓存数据", playerUUID);
    }

    /**
     * 获取玩家的最大生命值
     *
     * @param playerUUID 玩家UUID
     * @return 最大生命值，如果不存在则返回默认值20.0
     */
    public static float getPlayerMaxHealth(UUID playerUUID) {
        return PLAYER_MAX_HEALTH_MAP.getOrDefault(playerUUID, 20.0f);
    }

    /**
     * 清除指定玩家的数据
     *
     * @param playerUUID 玩家UUID
     */
    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_MAX_HEALTH_MAP.remove(playerUUID);
    }

    /**
     * 清除所有玩家的数据
     */
    public static void clearAllData() {
        PLAYER_MAX_HEALTH_MAP.clear();
    }
}