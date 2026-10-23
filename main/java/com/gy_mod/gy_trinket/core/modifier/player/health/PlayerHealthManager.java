package com.gy_mod.gy_trinket.core.modifier.player.health;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PlayerHealthManager {

    private static final String HEALTH_BASE_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "health_base";
    private static final String HEALTH_PERCENT_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "health_percent";
    private static final String HEALTH_FLAT_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "health_flat";
    private static final UUID HEALTH_BASE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID HEALTH_PERCENT_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID HEALTH_FLAT_MODIFIER_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-012345678901");

    private static final Map<UUID, Float> PLAYER_MAX_HEALTH_MAP = new ConcurrentHashMap<>();
    /** 未加转化固定修正的乘后最大生命（转化机制的比较基准，避免从含修正的 maxHealth 反推失真） */
    private static final Map<UUID, Float> PLAYER_NO_FLAT_MAX_HEALTH_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            player = server.getPlayerList().getPlayer(playerUUID);
        }
        if (player == null || !player.isAlive()) {
            return;
        }

        applyHealthModifiers(playerUUID, player);
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("player_health_percent") && !attrName.equals("player_health_independent")
                && !attrName.equals("player_health_flat")) {
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

        applyHealthModifiers(playerUUID, player);
    }

    /**
     * 计算并应用生命修饰符：
     * 底数/百分比乘区（player_health、player_health_percent、player_health_independent）
     * + 转化固定修正 player_health_flat（POST_ADD 语义："在所有计算之后加固定值"）。
     * <p>
     * 固定修正经 MULTIPLY_TOTAL 反推实现：最终 = noFlat × (1 + flat/noFlat) = noFlat + flat，
     * 其中 noFlat 为底数/乘区修饰符加入后、固定修正加入前的属性现值（含其他模组修饰符）。
     * noFlat 作为比较基准记录，供转化机制直接读取（避免从含修正的 maxHealth 反推导致的除零/失真）。
     */
    private static void applyHealthModifiers(UUID playerUUID, ServerPlayer player) {
        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double healthPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent");
        double healthIndependent = AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");
        double healthFlat = AttributeManager.getPlayerAttribute(playerUUID, "player_health_flat");

        double totalMultiplier = healthPercent * healthIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        if (healthBase != 0) {
            AttributeModifier baseModifier = new AttributeModifier(HEALTH_BASE_MODIFIER_UUID, HEALTH_BASE_MODIFIER_NAME, healthBase, AttributeModifier.Operation.ADDITION);
            attribute.addTransientModifier(baseModifier);
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier percentModifier = new AttributeModifier(HEALTH_PERCENT_MODIFIER_UUID, HEALTH_PERCENT_MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_BASE);
            attribute.addTransientModifier(percentModifier);
        }

        // 未加转化固定修正的乘后值（转化机制比较基准）：直接读属性现值——
        // 本模组底数/乘区修饰符已加入，其他模组的生命修饰符天然保留在内
        double noFlat = attribute.getValue();

        // 转化固定修正：final = noFlat + flat，经 MULTIPLY_TOTAL 反推为末尾固定加法
        if (healthFlat != 0 && noFlat > 0.0001) {
            AttributeModifier flatModifier = new AttributeModifier(HEALTH_FLAT_MODIFIER_UUID, HEALTH_FLAT_MODIFIER_NAME, healthFlat / noFlat, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(flatModifier);
        }

        float maxHealth = player.getMaxHealth();
        PLAYER_MAX_HEALTH_MAP.put(playerUUID, maxHealth);
        PLAYER_NO_FLAT_MAX_HEALTH_MAP.put(playerUUID, (float) noFlat);

        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        ModifierHelper.removeAllModModifiers(attribute);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        Float maxHealth = PLAYER_MAX_HEALTH_MAP.get(playerUUID);

        if (maxHealth != null && maxHealth > 0) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerUUID = player.getUUID();
        PLAYER_MAX_HEALTH_MAP.remove(playerUUID);
        PLAYER_NO_FLAT_MAX_HEALTH_MAP.remove(playerUUID);
    }

    public static float getPlayerMaxHealth(UUID playerUUID) {
        return PLAYER_MAX_HEALTH_MAP.getOrDefault(playerUUID, 20.0f);
    }

    /** 未加转化固定修正的乘后最大生命（转化机制比较基准用） */
    public static float getNoFlatMaxHealth(UUID playerUUID) {
        return PLAYER_NO_FLAT_MAX_HEALTH_MAP.getOrDefault(playerUUID, 20.0f);
    }

    /**
     * 计算自然恢复使用的恢复基数（有限资源制）：
     * <p>
     * 1. 取玩家 MAX_HEALTH 属性值并剔除本模组的生命修饰符（其他模组的修饰符保留），
     *    得到"原版最大生命值"：高于 20 时限为 20，低于 20 则使用低值；
     * 2. 再叠加本模组的生命修改属性：
     *    (原版最大生命 + player_health) × player_health_percent × player_health_independent + player_health_flat。
     * <p>
     * 例：原版最大生命 20（其他模组 +20 不计入，限为 20），本模组 45% 生命提升 -> 20 × 1.45 = 29。
     */
    public static double getNaturalRecoveryMaxHealth(UUID playerUUID, ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        double vanillaMax = 20.0;
        if (attribute != null) {
            double base = attribute.getBaseValue();
            double multipliedBase = 1.0;
            double multipliedTotal = 1.0;
            for (AttributeModifier modifier : attribute.getModifiers()) {
                // 1.20.1: 按名称前缀识别本模组修饰符（等价于 1.21.1 的 id().getNamespace()）
                if (modifier.getName().startsWith(ModifierHelper.MOD_PREFIX)) continue;
                switch (modifier.getOperation()) {
                    case ADDITION -> base += modifier.getAmount();
                    case MULTIPLY_BASE -> multipliedBase *= 1.0 + modifier.getAmount();
                    case MULTIPLY_TOTAL -> multipliedTotal *= 1.0 + modifier.getAmount();
                }
            }
            // 原版最大生命值：高于 20 限为 20，低于 20 使用低值
            vanillaMax = Math.min(base * multipliedBase * multipliedTotal, 20.0);
        }

        double healthBase = AttributeManager.getPlayerAttribute(playerUUID, "player_health");
        double percentMultiplier = AttributeManager.getPlayerAttribute(playerUUID, "player_health_percent")
                * AttributeManager.getPlayerAttribute(playerUUID, "player_health_independent");
        return (vanillaMax + healthBase) * percentMultiplier
                + AttributeManager.getPlayerAttribute(playerUUID, "player_health_flat");
    }

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_MAX_HEALTH_MAP.remove(playerUUID);
        PLAYER_NO_FLAT_MAX_HEALTH_MAP.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_MAX_HEALTH_MAP.clear();
        PLAYER_NO_FLAT_MAX_HEALTH_MAP.clear();
    }
}
