package com.gy_mod.gy_trinket.core.modifier.player.attack;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackSpeedManager {

    private static final String MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "attack_speed";
    private static final UUID MODIFIER_UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-012345678902");

    // 账本 attack_speed_flat（充能攻速修正值等加法贡献）投影到原版属性的修饰符。
    // 点射冷却的攻速捕获（BurstFireSupport）共用此修饰符：同种减益不重复施加，避免双重减益把攻速压至 0 以下
    public static final String FLAT_MODIFIER_NAME = ModifierHelper.MOD_PREFIX + "attack_speed_flat";
    public static final UUID FLAT_MODIFIER_UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-012345678912");

    private static final Map<UUID, Double> PLAYER_ATTACK_SPEED_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double attackSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_percent");
        double attackSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_independent");
        double attackSpeedFlat = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_flat");

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

        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        // 加法贡献（充能攻速修正值等）投影
        if (attackSpeedFlat != 0.0) {
            attribute.addTransientModifier(new AttributeModifier(FLAT_MODIFIER_UUID, FLAT_MODIFIER_NAME, attackSpeedFlat, AttributeModifier.Operation.ADDITION));
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("attack_speed_percent") && !attrName.equals("attack_speed_independent") && !attrName.equals("attack_speed_flat")) {
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
        double attackSpeedFlat = AttributeManager.getPlayerAttribute(playerUUID, "attack_speed_flat");

        double totalMultiplier = attackSpeedPercent * attackSpeedIndependent;

        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }

        ModifierHelper.removeAllModModifiers(attribute);

        // 加法贡献（充能攻速修正值等）投影
        if (attackSpeedFlat != 0.0) {
            attribute.addTransientModifier(new AttributeModifier(FLAT_MODIFIER_UUID, FLAT_MODIFIER_NAME, attackSpeedFlat, AttributeModifier.Operation.ADDITION));
        }

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, totalMultiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        PLAYER_ATTACK_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.ATTACK_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AttributeManager.recalculateAndCachePlayerAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
        PLAYER_ATTACK_SPEED_MAP.remove(player.getUUID());
    }

    public static double getPlayerAttackSpeed(UUID playerUUID) {
        return PLAYER_ATTACK_SPEED_MAP.getOrDefault(playerUUID, 4.0);
    }

    /**
     * 获取本模组攻击速度属性对拦截机的乘数（排除强袭命名空间的动态贡献）
     * <p>
     * 拦截机只继承本模组的攻击速度属性加成，不继承其他模组或强袭状态的加成。
     * 公式：attack_speed_percent(排除强袭) × attack_speed_independent(排除强袭)
     *
     * @param playerUUID 玩家UUID
     * @return 攻击速度乘数（1.0 = 无加成）
     */
    public static double getInterceptorAttackSpeedMultiplier(UUID playerUUID) {
        double attackSpeedPercent = AttributeManager.getPlayerAttributeExcludingNamespace(
                playerUUID, "attack_speed_percent", "assault:");
        double attackSpeedIndependent = AttributeManager.getPlayerAttributeExcludingNamespace(
                playerUUID, "attack_speed_independent", "assault:");
        return attackSpeedPercent * attackSpeedIndependent;
    }

    /**
     * 获取不含模组修正的攻击速度
     * <p>
     * 临时移除模组修正器，读取基础值，再恢复。
     * 用于电能释放等不应受模组攻击速度修正影响的计算。
     */
    public static double getBaseAttackSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return 4.0;
        }
        // 收集模组修正器
        List<AttributeModifier> modModifiers = new ArrayList<>();
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getName().startsWith(ModifierHelper.MOD_PREFIX)) {
                modModifiers.add(modifier);
            }
        }
        // 临时移除模组修正器
        for (AttributeModifier modifier : modModifiers) {
            attribute.removeModifier(modifier);
        }
        // 读取不含模组修正的值
        double baseValue = attribute.getValue();
        // 恢复模组修正器
        for (AttributeModifier modifier : modModifiers) {
            attribute.addTransientModifier(modifier);
        }
        return baseValue;
    }

    public static void clearAllData() {
        PLAYER_ATTACK_SPEED_MAP.clear();
    }
}
