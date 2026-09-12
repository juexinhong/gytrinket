package com.gytrinket.gytrinket.core.modifier.player.movement;

import com.gytrinket.gytrinket.core.attack_mode.AttackSpeedPenaltyManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = gytrinket.MODID)
public class MovementSpeedManager {


    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "movement_speed");

    /** 原版默认创造飞行速度（Abilities.flyingSpeed 基准值） */
    private static final float BASE_FLYING_SPEED = 0.05F;

    private static final Map<UUID, Double> PLAYER_MOVEMENT_SPEED_MAP = new ConcurrentHashMap<>();

    /** 上次施加的聚合乘数（用于短路无变化的重复施加，避免每 tick 属性同步包） */
    private static final Map<UUID, Double> LAST_APPLIED_MULTIPLIER = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

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

        applyMovementModifier(player, playerUUID);
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (!attrName.equals("movement_speed_percent") && !attrName.equals("movement_speed_independent")) {
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

        applyMovementModifier(player, playerUUID);
    }

    /** 按当前 movement_speed 属性聚合值重新施加修饰符 */
    private static void applyMovementModifier(ServerPlayer player, UUID playerUUID) {
        double movementSpeedPercent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_percent");
        double movementSpeedIndependent = AttributeManager.getPlayerAttribute(playerUUID, "movement_speed_independent");

        double totalMultiplier = movementSpeedPercent * movementSpeedIndependent;

        // 聚合值未变化时不动修改器：每次修改器增删都会向客户端同步属性包，
        // 强袭持续攻击时局部重算每 tick 触发本方法，若反复增删会导致疾跑镜头高频缩放
        Double lastApplied = LAST_APPLIED_MULTIPLIER.get(playerUUID);
        if (lastApplied != null && lastApplied == totalMultiplier) {
            PLAYER_MOVEMENT_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.MOVEMENT_SPEED));
            // 乘数未变仍需保证创造飞行速度/双端缓存就位（如重生后实体为新建，能力字段回默认值）
            applyFlyingSpeedAndCache(player, playerUUID, totalMultiplier);
            return;
        }
        LAST_APPLIED_MULTIPLIER.put(playerUUID, totalMultiplier);

        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        // 仅精确替换本聚合修改器：removeAllModModifiers 会连带删除攻击惩罚
        // （attack_penalty_*）等其他 gytrinket 移速修改器，导致属性值瞬时跳变
        ModifierHelper.removeModifier(attribute, MODIFIER_ID);

        if (totalMultiplier != 1.0) {
            AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, totalMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            attribute.addTransientModifier(modifier);
        }

        // 通知 AttackSpeedPenaltyManager 重新添加攻击惩罚修改器
        AttackSpeedPenaltyManager.markForceRefresh(playerUUID);

        PLAYER_MOVEMENT_SPEED_MAP.put(playerUUID, player.getAttributeValue(Attributes.MOVEMENT_SPEED));

        applyFlyingSpeedAndCache(player, playerUUID, totalMultiplier);
        // 乘数变化：同步客户端（游泳乘数在客户端 travel 中消费）
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new com.gytrinket.gytrinket.network.packet.SyncMovementSpeedMultiplierPayload(totalMultiplier));
    }

    /**
     * 使速度属性影响创造飞行：直接改写 Abilities.flyingSpeed（原版基准 0.05）。
     * 玩家未注册 FLYING_SPEED 属性（原版仅飞行生物注册），无法用属性修饰符实现。
     * 鞘翅滑翔为纯物理，不受此影响（也无需影响）。
     * 值未变化时不调用 onUpdateAbilities，避免每 tick 重发能力同步包。
     */
    private static void applyFlyingSpeedAndCache(ServerPlayer player, UUID playerUUID, double totalMultiplier) {
        float expected = (float) (BASE_FLYING_SPEED * Math.max(0.0D, totalMultiplier));
        if (Math.abs(player.getAbilities().getFlyingSpeed() - expected) > 1.0E-4F) {
            player.getAbilities().setFlyingSpeed(expected);
            player.onUpdateAbilities();
        }
        MovementSpeedMultiplierCache.set(playerUUID, totalMultiplier);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AttributeInstance attribute = event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // onPlayerClone 已清空修改器，需同步清除短路缓存以确保重新施加聚合修改器
        LAST_APPLIED_MULTIPLIER.remove(player.getUUID());
        AttributeManager.recalculateAndCachePlayerAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        ModifierHelper.removeAllModModifiers(attribute);
        // 登出重置创造飞行速度与乘数缓存（登出事件先于存档保存，重置值会写入 NBT，避免持久化 mod 值）
        player.getAbilities().setFlyingSpeed(BASE_FLYING_SPEED);
        PLAYER_MOVEMENT_SPEED_MAP.remove(player.getUUID());
        LAST_APPLIED_MULTIPLIER.remove(player.getUUID());
        MovementSpeedMultiplierCache.remove(player.getUUID());
    }

    public static double getPlayerMovementSpeed(UUID playerUUID) {
        return PLAYER_MOVEMENT_SPEED_MAP.getOrDefault(playerUUID, 0.1);
    }

    public static void clearAllData() {
        PLAYER_MOVEMENT_SPEED_MAP.clear();
        LAST_APPLIED_MULTIPLIER.clear();
        MovementSpeedMultiplierCache.clear();
    }
}
