package com.gytrinket.gytrinket.core.attack_mode;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attack_mode.assault.AssaultManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gytrinket.gytrinket.core.charged_shield.ChargedShieldManager;
import com.gytrinket.gytrinket.core.grudge.GrudgeManager;
import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击模式移动速度惩罚管理器
 * <p>
 * 为攻击模式的各个状态施加独立的移动速度减速修改器。
 * 每个减速来源使用独立的 UUID 和 MULTIPLY_TOTAL 操作，
 * 确保减速效果是独立乘区，不会合并导致移速归零。
 * <p>
 * 减速来源（具体值由Config配置）：
 * - 强袭（按住左键期间）：默认-60% 移动速度
 * - 充能攻击（充能期间）：默认-20% 移动速度
 * - 充能护盾模块（充能期间）：默认-15% 移动速度
 * - 积怨模块（充能期间）：默认-15% 移动速度
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class AttackSpeedPenaltyManager {

    private static final String MODIFIER_PREFIX = ModifierHelper.MOD_PREFIX + "attack_penalty_";

    // 各减速来源的独立 UUID
    private static final ResourceLocation ASSAULT_PENALTY_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_penalty_assault");
    private static final ResourceLocation CHARGED_PENALTY_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_penalty_charged");
    private static final ResourceLocation CHARGED_SHIELD_PENALTY_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_penalty_charged_shield");
    private static final ResourceLocation GRUDGE_PENALTY_ID = ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_penalty_grudge");

    // 记录每个玩家上一tick的惩罚状态，用于检测变化
    private static final Map<UUID, PenaltyState> PLAYER_PREV_STATE = new ConcurrentHashMap<>();

    // 标记需要强制刷新的玩家（MovementSpeedManager 清除修改器后需要重新添加）
    private static final Set<UUID> FORCE_REFRESH = new java.util.concurrent.CopyOnWriteArraySet<>();

    private AttackSpeedPenaltyManager() {}

    private static class PenaltyState {
        boolean assault;
        boolean charged;
        boolean chargedShield;
        boolean grudge;

        boolean equals(PenaltyState other) {
            return assault == other.assault && charged == other.charged
                    && chargedShield == other.chargedShield && grudge == other.grudge;
        }
    }

    /**
     * 标记指定玩家需要强制刷新惩罚修改器。
     * 由 MovementSpeedManager 在清除所有模组修改器后调用。
     */
    public static void markForceRefresh(UUID playerUUID) {
        FORCE_REFRESH.add(playerUUID);
        PLAYER_PREV_STATE.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        // 计算当前惩罚状态
        PenaltyState currentState = new PenaltyState();
        currentState.assault = AssaultManager.hasAssault(player) && AssaultManager.getAssaultStacks(player) > 0;
        currentState.charged = ChargedAttackManager.hasChargedAttack(player) && ChargedAttackManager.isCharging(player);
        currentState.chargedShield = currentState.charged && ChargedShieldManager.hasChargedShield(player);
        currentState.grudge = currentState.charged && GrudgeManager.hasGrudge(player);

        // 检查是否需要刷新（状态变化或强制刷新标记）
        boolean forceRefresh = FORCE_REFRESH.remove(uuid);
        PenaltyState prevState = PLAYER_PREV_STATE.get(uuid);
        if (!forceRefresh && prevState != null && prevState.equals(currentState)) {
            return;
        }

        PLAYER_PREV_STATE.put(uuid, currentState);

        // 应用/移除修改器
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }

        applyOrRemoveModifier(speedAttribute, ASSAULT_PENALTY_ID, MODIFIER_PREFIX + "assault", currentState.assault, Config.getAssaultMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, CHARGED_PENALTY_ID, MODIFIER_PREFIX + "charged", currentState.charged, Config.getChargedAttackMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, CHARGED_SHIELD_PENALTY_ID, MODIFIER_PREFIX + "charged_shield", currentState.chargedShield, Config.getChargedShieldMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, GRUDGE_PENALTY_ID, MODIFIER_PREFIX + "grudge", currentState.grudge, Config.getGrudgeMovementSpeedPenalty());
    }

    private static void applyOrRemoveModifier(AttributeInstance attribute, ResourceLocation id, String name, boolean shouldApply, double amount) {
        AttributeModifier existing = attribute.getModifier(id);
        if (shouldApply) {
            if (existing == null) {
                attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            if (existing != null) {
                attribute.removeModifier(existing);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PLAYER_PREV_STATE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PLAYER_PREV_STATE.remove(player.getUUID());
    }

    /**
     * 清除指定玩家所有攻击惩罚修改器（用于玩家登出/重生等场景）
     */
    public static void removeAllPenalties(Player player) {
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }
        ModifierHelper.removeModifier(speedAttribute, ASSAULT_PENALTY_ID);
        ModifierHelper.removeModifier(speedAttribute, CHARGED_PENALTY_ID);
        ModifierHelper.removeModifier(speedAttribute, CHARGED_SHIELD_PENALTY_ID);
        ModifierHelper.removeModifier(speedAttribute, GRUDGE_PENALTY_ID);
    }

    private static void removeModifierByUuid(AttributeInstance attribute, ResourceLocation id) {
        ModifierHelper.removeModifier(attribute, id);
    }

    public static void clearAllData() {
        PLAYER_PREV_STATE.clear();
    }
}
