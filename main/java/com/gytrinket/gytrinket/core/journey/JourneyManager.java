package com.gytrinket.gytrinket.core.journey;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 征途管理器
 * <p>
 * 玩家每击杀一个实体累积 1 层战意：
 * <ul>
 *   <li>每层战意提供 +{@code Config.JOURNEY_ATTACK_SPEED_PER_STACK} 攻击速度动态独立乘区加成</li>
 *   <li>每层战意提供 +{@code Config.JOURNEY_MOVEMENT_SPEED_PER_STACK} 移动速度动态独立乘区加成</li>
 *   <li>叠加时刷新持续时间（{@code Config.JOURNEY_DURATION_TICKS}），期间持续保持层数</li>
 *   <li>持续时间耗尽后快速消退：每 {@code Config.JOURNEY_DECAY_INTERVAL_TICKS} 刻消退
 *       {@code Config.JOURNEY_DECAY_PER_INTERVAL} 层</li>
 *   <li>最多叠加 {@code Config.JOURNEY_MAX_STACKS} 层</li>
 * </ul>
 * 加成通过本模组属性池（{@link AttributeManager}）的 attack_speed_independent /
 * movement_speed_independent 动态属性施加：玩家面板可显示聚合值，
 * 实际修饰符由 {@link com.gytrinket.gytrinket.core.modifier.player.attack.AttackSpeedManager} /
 * {@link com.gytrinket.gytrinket.core.modifier.player.movement.MovementSpeedManager} 统一应用，
 * 移动速度镜头修正由 {@link com.gytrinket.gytrinket.client.FovHandler} 处理。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class JourneyManager {

    private static final String NAMESPACE = "journey";
    private static final String ATTR_ATTACK_SPEED = "attack_speed_independent";
    private static final String ATTR_MOVEMENT_SPEED = "movement_speed_independent";

    /** 玩家是否装备征途模块（由属性计算事件刷新缓存） */
    private static final Set<UUID> PLAYER_HAS_JOURNEY_MODULE = ConcurrentHashMap.newKeySet();
    /** 当前战意层数 */
    private static final Map<UUID, Integer> PLAYER_STACK_COUNT = new ConcurrentHashMap<>();
    /** 战意持续时间剩余刻数（叠加时刷新到满值） */
    private static final Map<UUID, Integer> PLAYER_STACK_TIMER = new ConcurrentHashMap<>();
    /** 消退阶段：距上次消退层数已过去的刻数（每 {JOURNEY_DECAY_INTERVAL_TICKS} 刻消退 {JOURNEY_DECAY_PER_INTERVAL} 层） */
    private static final Map<UUID, Integer> PLAYER_DECAY_TICKER = new ConcurrentHashMap<>();
    /** 上次已应用属性的层数（避免每刻重复刷新属性造成抖动） */
    private static final Map<UUID, Integer> PLAYER_APPLIED_STACKS = new ConcurrentHashMap<>();

    private JourneyManager() {}

    // ===== 模块检测 =====

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID uuid = event.getPlayerUUID();
        Player player = event.getPlayer();
        boolean hasModule = player != null && PlayerStoreUtils.hasActiveItem(player, Config::isJourneyModuleItem);
        if (hasModule) {
            PLAYER_HAS_JOURNEY_MODULE.add(uuid);
        } else {
            PLAYER_HAS_JOURNEY_MODULE.remove(uuid);
            // 失去模块：立即清空层数与属性
            if (player instanceof ServerPlayer serverPlayer) {
                clearStacks(serverPlayer);
            }
        }
    }

    // ===== 击杀累积战意 =====

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (!PLAYER_HAS_JOURNEY_MODULE.contains(killer.getUUID())) {
            return;
        }
        addStack(killer);
    }

    // ===== 每刻推进持续时间/消退 =====

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        // 惰性检查：只有拥有战意层数的玩家才参与处理，无层数直接返回
        if (PLAYER_STACK_COUNT.getOrDefault(uuid, 0) <= 0) {
            return;
        }
        // 兜底：玩家已失去模块（属性计算事件未拿到玩家引用时），清空层数与属性
        if (!PLAYER_HAS_JOURNEY_MODULE.contains(uuid)) {
            clearStacks(player);
            return;
        }

        int count = PLAYER_STACK_COUNT.get(uuid);
        int timer = PLAYER_STACK_TIMER.getOrDefault(uuid, 0);

        if (timer > 0) {
            // 持续时间倒计时
            timer--;
            PLAYER_STACK_TIMER.put(uuid, timer);
        } else {
            // 持续时间已耗尽：快速消退战意层数
            int decayTicker = PLAYER_DECAY_TICKER.getOrDefault(uuid, 0) + 1;
            if (decayTicker >= Config.getJourneyDecayIntervalTicks()) {
                PLAYER_DECAY_TICKER.remove(uuid);
                count = Math.max(0, count - Config.getJourneyDecayPerInterval());
                if (count <= 0) {
                    PLAYER_STACK_COUNT.remove(uuid);
                    PLAYER_STACK_TIMER.remove(uuid);
                    PLAYER_DECAY_TICKER.remove(uuid);
                } else {
                    PLAYER_STACK_COUNT.put(uuid, count);
                }
            } else {
                PLAYER_DECAY_TICKER.put(uuid, decayTicker);
            }
        }

        applyAttributes(player, PLAYER_STACK_COUNT.getOrDefault(uuid, 0));
    }

    // ===== 核心逻辑 =====

    /** 击杀叠加战意：层数+1（封顶），并刷新持续时间 */
    private static void addStack(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int count = PLAYER_STACK_COUNT.getOrDefault(uuid, 0);
        count = Math.min(count + 1, Config.getJourneyMaxStacks());
        PLAYER_STACK_COUNT.put(uuid, count);
        // 叠加时刷新持续时间，并停止消退
        PLAYER_STACK_TIMER.put(uuid, Config.getJourneyDurationTicks());
        PLAYER_DECAY_TICKER.remove(uuid);
        applyAttributes(player, count);
    }

    /** 按当前层数应用/更新动态属性（经本模组属性池，面板可显示，由各属性管理器统一施加修饰符） */
    private static void applyAttributes(ServerPlayer player, int stacks) {
        UUID uuid = player.getUUID();
        Integer applied = PLAYER_APPLIED_STACKS.get(uuid);
        if (applied != null && applied == stacks) {
            return;
        }
        PLAYER_APPLIED_STACKS.put(uuid, stacks);

        double attackSpeedBonus = stacks * Config.getJourneyAttackSpeedPerStack();
        if (attackSpeedBonus > 0) {
            AttributeManager.setDynamicAttribute(uuid, NAMESPACE, ATTR_ATTACK_SPEED, attackSpeedBonus);
        } else {
            AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, ATTR_ATTACK_SPEED);
        }

        double movementSpeedBonus = stacks * Config.getJourneyMovementSpeedPerStack();
        if (movementSpeedBonus > 0) {
            AttributeManager.setDynamicAttribute(uuid, NAMESPACE, ATTR_MOVEMENT_SPEED, movementSpeedBonus);
        } else {
            AttributeManager.removeDynamicAttribute(uuid, NAMESPACE, ATTR_MOVEMENT_SPEED);
        }
    }

    /** 清空指定玩家的战意层数与动态属性 */
    private static void clearStacks(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PLAYER_STACK_COUNT.remove(uuid);
        PLAYER_STACK_TIMER.remove(uuid);
        PLAYER_DECAY_TICKER.remove(uuid);
        applyAttributes(player, 0);
        PLAYER_APPLIED_STACKS.remove(uuid);
    }

    // ===== 查询 =====

    /** 获取玩家的当前战意层数 */
    public static int getStackCount(UUID playerUUID) {
        return PLAYER_STACK_COUNT.getOrDefault(playerUUID, 0);
    }

    /** 玩家是否装备征途模块 */
    public static boolean hasJourneyModule(UUID playerUUID) {
        return PLAYER_HAS_JOURNEY_MODULE.contains(playerUUID);
    }

    // ===== 清理 =====

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_HAS_JOURNEY_MODULE.remove(uuid);
        PLAYER_STACK_COUNT.remove(uuid);
        PLAYER_STACK_TIMER.remove(uuid);
        PLAYER_DECAY_TICKER.remove(uuid);
        PLAYER_APPLIED_STACKS.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        clearStacks(player);
    }
}
