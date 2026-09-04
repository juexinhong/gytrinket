package com.gytrinket.gytrinket.core.attack_mode.burst_fire;

import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager;
import com.gytrinket.gytrinket.core.attack_mode.PlayerAttackLockManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点射系统管理器
 * <p>
 * 核心流程：
 * 1. 初次攻击命中目标（或由强袭/充能攻击触发）→ 启动一个独立的点射循环
 * 2. 每个循环每刻对各自目标自动攻击一段（移除目标无敌时间，保证全额伤害）
 * 3. 段数耗尽或目标死亡 → 移除该循环；所有循环结束 → 通知客户端点射结束
 * 4. 循环之间互不干扰：每次触发都追加新循环（高攻速下冷却先于循环结束到期，
 *    玩家即可再次攻击并叠加新循环，每次都享受完整连击段数）
 * 5. 触发时即挂冷却：冷却 = 连击段数 × 攻击间隔（按触发时快照的有效攻速计算：
 *    触发时借用属性系统临时施加修正值后读取——主手武器不加修正、白名单/默认临时施加，
 *    自动叠加玩家身上所有攻速修饰符）。首发不计——首发已承受自身原本的冷却，
 *    冷却等价于预支的 N 次未来攻击所需时间
 * <p>
 * 跨系统交互通过 AttackModeManager 策略管理：
 * - 点射自动攻击后 → 管理器根据策略决定是否触发强袭/电能释放
 * - 强袭攻击后 → 管理器调用 startBurstFromAssault 触发点射
 * - 充能释放后 → 管理器标记触发点射
 * - 在含充能的组合中，点射不会自主触发，只能由充能释放后触发
 * <p>
 * 启用条件：玩家的 combo 属性 > 0
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class BurstFireManager {

    // 延迟时间：1刻 = 0.05秒
    private static final int BURST_DELAY_TICKS = 1;

    // 单个点射循环状态：独立目标 + 剩余段数 + 段间延迟（循环之间互不干扰）
    private static final class ComboCopyState {
        final LivingEntity target;
        int remainingCopies;
        int delay;

        ComboCopyState(LivingEntity target, int comboStacksBonus) {
            this.target = target;
            // 本次首发攻击已消耗一段，剩余为复制的段数
            this.remainingCopies = comboStacksBonus;
            this.delay = BURST_DELAY_TICKS;
        }
    }

    // 进行中的点射循环列表：UUID -> 循环列表（每次触发追加新循环，高攻速下可并发叠加）
    private static final Map<UUID, List<ComboCopyState>> ACTIVE = new ConcurrentHashMap<>();

    // 自动攻击执行中的瞬时标记：防止自动攻击造成的伤害再次触发新点射循环（防套娃）
    private static final Map<UUID, Boolean> AUTO_ATTACK_REENTRANCY = new ConcurrentHashMap<>();

    // 存储玩家连击冷却计时器：UUID -> 剩余冷却刻数
    private static final Map<UUID, Integer> COMBO_COOLDOWN = new ConcurrentHashMap<>();

    /**
     * 监听玩家攻击事件
     * 处理点射状态下的自动多段攻击
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 如果攻击已被取消（如充能攻击首次攻击），不处理
        if (event.isCanceled()) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 本刻已触发点射的攻击（点射自动攻击段/强袭触发的首发攻击）：
        // 只清目标无敌帧并放行——即使处于连击冷却中，该冷却正是本次点射预支的
        // 攻击时间，不能反过来取消本次点射的攻击伤害；同时防止重复启动新循环（防套娃）
        if (AUTO_ATTACK_REENTRANCY.getOrDefault(playerUUID, false)) {
            if (event.getTarget() instanceof LivingEntity target) {
                target.invulnerableTime = 0;
            }
            return;
        }

        // 攻击锁定时禁用点射
        if (PlayerAttackLockManager.isLocked(playerUUID)) {
            return;
        }

        // 检查玩家是否处于连击冷却状态（冷却中禁用手动攻击）
        if (isInComboCooldown(playerUUID)) {
            event.setCanceled(true);
            return;
        }

        // 检查玩家的连击段数是否大于0
        int comboStacksBonus = BurstFireSupport.getComboStacksBonus(player);
        if (comboStacksBonus <= 0) {
            return;
        }

        // 检查目标是否有效
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        // 检查攻击强度是否达90%
        float attackStrength = player.getAttackStrengthScale(0.0F);
        if (attackStrength < 0.9F) {
            return;
        }

        // 检查是否是充能释放后触发的点射
        boolean fromChargedRelease = AttackModeManager.consumePendingBurstFromCharged(playerUUID);

        // 根据策略检查点射是否可以由正常攻击触发
        boolean canTriggerFromNormal = AttackModeManager.canBurstFireTriggerFromNormalAttack(playerUUID);
        if (!canTriggerFromNormal && !fromChargedRelease) {
            return; // 当前组合不允许点射自主触发，也没有充能释放触发标记
        }

        // 确认点射会触发，移除目标无敌时间
        target.invulnerableTime = 0;

        // 触发时即挂冷却：冷却 = 连击段数 × 攻击间隔（首发不计，已承受自身原本的冷却）
        double attackSpeed = BurstFireSupport.captureEffectiveAttackSpeed(player);
        int cooldownTicks = BurstFireSupport.calcBurstCooldownTicks(comboStacksBonus, attackSpeed);
        COMBO_COOLDOWN.put(playerUUID, cooldownTicks);
        com.gytrinket.gytrinket.network.NetworkHandler.sendComboCooldownToPlayer(player, true, cooldownTicks);

        // 启动一个独立的点射循环（高攻速下旧循环未结束时也可叠加，循环间互不干扰）
        ACTIVE.computeIfAbsent(playerUUID, k -> new ArrayList<>())
                .add(new ComboCopyState(target, comboStacksBonus));

        // 同步点射进行中状态到客户端
        com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);
    }

    /**
     * 监听玩家每刻更新事件
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 连击冷却递减（与循环处理并行：高攻速下冷却可先于循环结束到期，
        // 此时循环继续推进，冷却结束后玩家即可再次攻击叠加新循环）
        if (isInComboCooldown(playerUUID)) {
            // 重置攻击冷却进度为0
            player.resetAttackStrengthTicker();

            // 减少冷却时间
            int remainingCooldown = COMBO_COOLDOWN.getOrDefault(playerUUID, 0);
            remainingCooldown--;
            if (remainingCooldown <= 0) {
                COMBO_COOLDOWN.remove(playerUUID);
                com.gytrinket.gytrinket.network.NetworkHandler.sendComboCooldownToPlayer(player, false, 0);
            } else {
                COMBO_COOLDOWN.put(playerUUID, remainingCooldown);
            }
        }

        // 处理进行中的点射循环
        List<ComboCopyState> loops = ACTIVE.get(playerUUID);
        if (loops != null && !loops.isEmpty()) {
            handleCopyLoop(player, loops);
        } else {
            ACTIVE.remove(playerUUID);
            int comboStacksBonus = BurstFireSupport.getComboStacksBonus(player);
            if (comboStacksBonus <= 0) {
                cleanupPlayerState(playerUUID);
            }
        }

        // 清理本刻的触发标记（强袭触发的首发攻击标记存活至攻击事件链结束；
        // 自动攻击段标记在 executeAutoAttack 的 finally 中已自清理，此处幂等兜底）
        AUTO_ATTACK_REENTRANCY.remove(playerUUID);
    }

    /**
     * 处理点射循环列表：各循环独立倒计时/攻击/耗尽移除，循环间互不干扰
     */
    private static void handleCopyLoop(ServerPlayer player, List<ComboCopyState> loops) {
        UUID playerUUID = player.getUUID();

        Iterator<ComboCopyState> iterator = loops.iterator();
        while (iterator.hasNext()) {
            ComboCopyState state = iterator.next();

            // 段间延迟未到，本循环本刻跳过
            if (state.delay > 0) {
                state.delay--;
                continue;
            }

            LivingEntity target = state.target;
            if (target == null || !target.isAlive()) {
                // 目标失效，直接移除该循环（冷却已在触发时挂过）
                iterator.remove();
                continue;
            }

            // 执行一段自动攻击
            executeAutoAttack(player, target);

            state.remainingCopies--;
            if (state.remainingCopies > 0 && target.isAlive()) {
                state.delay = BURST_DELAY_TICKS;
            } else {
                iterator.remove();
            }
        }

        // 所有循环结束：通知客户端点射结束
        if (loops.isEmpty()) {
            ACTIVE.remove(playerUUID);
            com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, false);
        }
    }

    /**
     * 执行一段自动攻击（满攻击强度、移除目标无敌时间保证全额伤害）
     */
    private static void executeAutoAttack(ServerPlayer player, LivingEntity target) {
        UUID playerUUID = player.getUUID();

        // 发送网络包到客户端，设置攻击强度为100%
        com.gytrinket.gytrinket.network.NetworkHandler.sendAttackStrengthToPlayer(player, true);

        // 临时增加攻击速度模拟满攻击强度
        var attackSpeedAttribute = player.getAttribute(Attributes.ATTACK_SPEED);
        net.minecraft.resources.ResourceLocation tempModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gytrinket", "burst_fire_temp_attack_speed");
        if (attackSpeedAttribute != null) {
            attackSpeedAttribute.getModifiers().stream()
                .filter(modifier -> modifier.id().equals(tempModifierId))
                .forEach(attackSpeedAttribute::removeModifier);

            var modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                tempModifierId,
                100000000.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            attackSpeedAttribute.addTransientModifier(modifier);
        }

        // 瞬时标记自动攻击执行中（防套娃：自动攻击的伤害不启动新点射循环）
        AUTO_ATTACK_REENTRANCY.put(playerUUID, true);
        try {
            // 移除目标无敌时间
            target.invulnerableTime = 0;

            // 直接调用player.attack()触发自动攻击
            player.attack(target);

            // 攻击后立即移除临时攻击速度加成（必须在跨系统触发前移除，避免冷却计算读到×1亿攻速）
            if (attackSpeedAttribute != null) {
                attackSpeedAttribute.getModifiers().stream()
                    .filter(modifier -> modifier.id().equals(tempModifierId))
                    .forEach(attackSpeedAttribute::removeModifier);
            }

            // 攻击后再次移除目标无敌时间
            target.invulnerableTime = 0;

            // 通过管理器处理跨系统触发（强袭、电能释放）
            AttackModeManager.onBurstFireAutoAttack(player, target);
        } finally {
            AUTO_ATTACK_REENTRANCY.remove(playerUUID);
        }
    }

    /**
     * 由强袭攻击触发点射（点射+强袭组合）。
     * 触发时即挂冷却并启动一个独立的点射循环；循环进行中允许叠加新循环。
     */
    public static void startBurstFromAssault(ServerPlayer player, LivingEntity target) {
        UUID playerUUID = player.getUUID();

        // 攻击锁定时禁用点射
        if (PlayerAttackLockManager.isLocked(playerUUID)) {
            return;
        }

        // 冷却中不重复触发（预支的攻击时间尚未付清）；循环进行中允许叠加新循环
        if (isInComboCooldown(playerUUID)) {
            return;
        }

        int comboStacksBonus = BurstFireSupport.getComboStacksBonus(player);
        if (comboStacksBonus <= 0) {
            return;
        }

        // 标记本刻已触发点射：本次首发攻击已由强袭触发点射，
        // 同一攻击事件后续（BurstFireManager.onPlayerAttack，HIGH 优先级）
        // 不取消攻击也不重复启动循环（onPlayerTick 末尾统一清理）
        AUTO_ATTACK_REENTRANCY.put(playerUUID, true);

        // 触发时即挂冷却：冷却 = 连击段数 × 攻击间隔（首发不计，已承受自身原本的冷却）
        double attackSpeed = BurstFireSupport.captureEffectiveAttackSpeed(player);
        int cooldownTicks = BurstFireSupport.calcBurstCooldownTicks(comboStacksBonus, attackSpeed);
        COMBO_COOLDOWN.put(playerUUID, cooldownTicks);
        com.gytrinket.gytrinket.network.NetworkHandler.sendComboCooldownToPlayer(player, true, cooldownTicks);

        // 启动一个独立的点射循环
        ACTIVE.computeIfAbsent(playerUUID, k -> new ArrayList<>())
                .add(new ComboCopyState(target, comboStacksBonus));

        // 同步点射进行中状态到客户端
        com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);
    }

    /**
     * 本次攻击是否为点射自动攻击段（或本刻已由强袭触发的首发攻击）。
     * 供 AttackModeManager 在攻击事件中避免重复触发强袭/点射。
     */
    public static boolean isAutoAttacking(UUID playerUUID) {
        return AUTO_ATTACK_REENTRANCY.getOrDefault(playerUUID, false);
    }

    /**
     * 清理玩家所有状态
     */
    private static void cleanupPlayerState(UUID playerUUID) {
        ACTIVE.remove(playerUUID);
        AUTO_ATTACK_REENTRANCY.remove(playerUUID);
    }

    /**
     * 获取玩家是否处于连击冷却状态
     */
    public static boolean isInComboCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN.containsKey(playerUUID);
    }

    /**
     * 获取玩家是否存在进行中的点射循环
     */
    public static boolean isInBurstFireState(Player player) {
        return hasActiveLoops(player.getUUID());
    }

    /**
     * 玩家是否存在进行中的点射循环
     */
    private static boolean hasActiveLoops(UUID playerUUID) {
        List<ComboCopyState> loops = ACTIVE.get(playerUUID);
        return loops != null && !loops.isEmpty();
    }

    /**
     * 取消玩家的点射状态（由攻击锁定、登出、死亡调用）
     */
    public static void cancelBurstFire(UUID playerUUID) {
        ACTIVE.remove(playerUUID);
        AUTO_ATTACK_REENTRANCY.remove(playerUUID);
        COMBO_COOLDOWN.remove(playerUUID);
    }

    /**
     * 监听玩家退出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        cancelBurstFire(player.getUUID());
    }

    /**
     * 监听玩家死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 死亡时若处于连击冷却/点射中，先通知客户端取消对应状态；
        // 否则客户端冷却/点射状态残留，重生后攻击强度被永久锁定为 0（攻击永久禁用）
        if (COMBO_COOLDOWN.containsKey(playerUUID)) {
            com.gytrinket.gytrinket.network.NetworkHandler.sendComboCooldownToPlayer(player, false, 0);
        }
        if (hasActiveLoops(playerUUID)) {
            com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, false);
        }

        cancelBurstFire(playerUUID);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        ACTIVE.clear();
        AUTO_ATTACK_REENTRANCY.clear();
        COMBO_COOLDOWN.clear();
    }
}
