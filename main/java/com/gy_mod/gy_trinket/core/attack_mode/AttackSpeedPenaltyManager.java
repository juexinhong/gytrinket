package com.gy_mod.gy_trinket.core.attack_mode;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attack_mode.assault.AssaultManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.core.charged_shield.ChargedShieldManager;
import com.gy_mod.gy_trinket.core.grudge.GrudgeManager;
import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttackSpeedPenaltyManager {

    private static final String MODIFIER_PREFIX = ModifierHelper.MOD_PREFIX + "attack_penalty_";

    // 各减速来源的独立 UUID
    private static final UUID ASSAULT_PENALTY_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456780");
    private static final UUID CHARGED_PENALTY_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456781");
    private static final UUID CHARGED_SHIELD_PENALTY_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456782");
    private static final UUID GRUDGE_PENALTY_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456783");

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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
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

        applyOrRemoveModifier(speedAttribute, ASSAULT_PENALTY_UUID, MODIFIER_PREFIX + "assault", currentState.assault, Config.getAssaultMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, CHARGED_PENALTY_UUID, MODIFIER_PREFIX + "charged", currentState.charged, Config.getChargedAttackMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, CHARGED_SHIELD_PENALTY_UUID, MODIFIER_PREFIX + "charged_shield", currentState.chargedShield, Config.getChargedShieldMovementSpeedPenalty());
        applyOrRemoveModifier(speedAttribute, GRUDGE_PENALTY_UUID, MODIFIER_PREFIX + "grudge", currentState.grudge, Config.getGrudgeMovementSpeedPenalty());
    }

    private static void applyOrRemoveModifier(AttributeInstance attribute, UUID uuid, String name, boolean shouldApply, double amount) {
        AttributeModifier existing = attribute.getModifier(uuid);
        if (shouldApply) {
            if (existing == null) {
                attribute.addTransientModifier(new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
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
        removeModifierByUuid(speedAttribute, ASSAULT_PENALTY_UUID);
        removeModifierByUuid(speedAttribute, CHARGED_PENALTY_UUID);
        removeModifierByUuid(speedAttribute, CHARGED_SHIELD_PENALTY_UUID);
        removeModifierByUuid(speedAttribute, GRUDGE_PENALTY_UUID);
    }

    private static void removeModifierByUuid(AttributeInstance attribute, UUID uuid) {
        AttributeModifier modifier = attribute.getModifier(uuid);
        if (modifier != null) {
            attribute.removeModifier(modifier);
        }
    }

    public static void clearAllData() {
        PLAYER_PREV_STATE.clear();
    }
}
