package com.gytrinket.gytrinket.core.attack_mode.burst_fire;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager;
import com.gytrinket.gytrinket.core.attack_mode.PlayerAttackLockManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点射系统管理器
 * <p>
 * 核心流程：
 * 1. 初次攻击命中目标（或由强袭/充能攻击触发）
 * 2. 记录目标信息
 * 3. 连击数-1
 * 4. 移除目标无敌时间
 * 5. 若连击数不为0且目标存活，重复自动攻击
 * 6. 连击结束，进入冷却（冷却时长按触发时快照的有效攻速计算：
 *    触发时借用属性系统临时施加修正值后读取——主手武器不加修正、白名单/默认临时施加，
 *    自动叠加玩家身上所有攻速修饰符）
 * <p>
 * 跨系统交互通过 AttackModeManager 策略管理：
 * - 点射自动攻击后 → 管理器根据策略决定是否触发强袭/电能释放
 * - 强袭自动攻击后 → 管理器调用 startBurstFromAssault 触发点射
 * - 充能释放后 → 管理器标记触发点射
 * - 在含充能的组合中，点射不会自主触发，只能由充能释放后触发
 * <p>
 * 启用条件：玩家的 combo 属性 > 0
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class BurstFireManager {

    // 延迟时间：1刻 = 0.05秒
    private static final int BURST_DELAY_TICKS = 1;

    // 点射冷却攻速计算的临时属性修饰符ID（借用属性系统读取最终攻速，读取后立即移除）
    private static final ResourceLocation BURST_COOLDOWN_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "burst_fire_cooldown_speed");

    // 存储玩家当前攻击目标：UUID -> 目标实体
    private static final Map<UUID, LivingEntity> CURRENT_TARGETS = new ConcurrentHashMap<>();

    // 存储玩家剩余连击数：UUID -> 剩余连击数
    private static final Map<UUID, Integer> REMAINING_COMBO = new ConcurrentHashMap<>();

    // 存储玩家是否正在进行自动攻击：UUID -> 是否正在自动攻击
    private static final Map<UUID, Boolean> IS_AUTO_ATTACKING = new ConcurrentHashMap<>();

    // 存储玩家自动攻击延迟计时器：UUID -> 剩余延迟刻数
    private static final Map<UUID, Integer> AUTO_ATTACK_DELAY = new ConcurrentHashMap<>();

    // 存储玩家连击冷却计时器：UUID -> 剩余冷却刻数
    private static final Map<UUID, Integer> COMBO_COOLDOWN = new ConcurrentHashMap<>();

    // 存储玩家点射触发时的有效攻速快照：UUID -> 有效攻速（连击冷却按触发时的攻速计算）
    private static final Map<UUID, Double> ATTACK_SPEED_SNAPSHOTS = new ConcurrentHashMap<>();

    /**
     * 获取玩家的连击段数加成
     */
    private static int getComboStacksBonus(Player player) {
        double combo = AttributeManager.getPlayerAttribute(player.getUUID(), "combo");
        return (int) Math.floor(combo);
    }

    /**
     * 借用属性系统捕获当前有效攻速（修正值施加方式与右键充能一致）：
     * 1. 主手物品为武器 → 不施加（武器自带攻速已在属性中生效）
     * 2. 主手物品命中充能物品白名单 → 临时施加白名单攻速修正值
     * 3. 其余（含空手）→ 临时施加默认攻速修正值
     * 读取属性最终攻速（自动叠加急迫等玩家身上所有攻速修饰符）后立即移除临时修饰符
     */
    static double captureEffectiveAttackSpeed(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        // 非武器（含空手）按充能逻辑取修正值：白名单命中取白名单值，未命中自动返回默认值
        double speedModifier = (mainHand.isEmpty() || !Config.isWeaponLikeItem(mainHand.getItem()))
                ? Config.getItemUseChargeSpeedModifier(mainHand.getItem())
                : 0;

        AttributeInstance attackSpeedAttribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speedModifier == 0 || attackSpeedAttribute == null) {
            return player.getAttributeValue(Attributes.ATTACK_SPEED);
        }

        // 临时施加修正值，读取最终攻速后立即移除（transient ADD_VALUE，与右键充能同一施加方式）
        attackSpeedAttribute.addTransientModifier(
                new AttributeModifier(BURST_COOLDOWN_SPEED_ID, speedModifier, AttributeModifier.Operation.ADD_VALUE));
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        attackSpeedAttribute.removeModifier(BURST_COOLDOWN_SPEED_ID);
        return attackSpeed;
    }

    /**
     * 按有效攻速计算连击冷却刻数：冷却 = (20 / 有效攻速) × 总连击段数
     */
    public static int calcComboCooldownTicks(int totalComboStacks, double attackSpeed) {
        return (int) Math.ceil((20.0 / Math.max(attackSpeed, 0.1)) * totalComboStacks);
    }

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

        // 攻击锁定时禁用点射
        if (PlayerAttackLockManager.isLocked(playerUUID)) {
            return;
        }

        // 检查玩家是否处于连击冷却状态
        if (isInComboCooldown(playerUUID)) {
            event.setCanceled(true);
            return;
        }

        // 检查玩家的连击段数是否大于0
        int comboStacksBonus = getComboStacksBonus(player);
        if (comboStacksBonus <= 0) {
            return;
        }

        // 检查目标是否有效
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        // 检查是否正在进行自动攻击
        boolean isAutoAttack = IS_AUTO_ATTACKING.getOrDefault(playerUUID, false);

        if (!isAutoAttack) {
            // 检查攻击强度是否达到90%
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

            // 初次攻击命中目标，记录目标信息
            CURRENT_TARGETS.put(playerUUID, target);

            // 获取玩家连击段数
            int comboStacks = 1 + comboStacksBonus;

            // 连击数-1（本次攻击已消耗一段）
            int remainingCombo = comboStacks - 1;
            REMAINING_COMBO.put(playerUUID, remainingCombo);

            // 开始自动攻击流程
            IS_AUTO_ATTACKING.put(playerUUID, true);

            // 同步点射进行中状态到客户端
            com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);

            // 设置首次自动攻击延迟
            AUTO_ATTACK_DELAY.put(playerUUID, BURST_DELAY_TICKS);

            // 快照触发时的有效攻速，用于连击冷却计算
            ATTACK_SPEED_SNAPSHOTS.put(playerUUID, captureEffectiveAttackSpeed(player));
        } else {
            // 自动攻击命中，确保目标无敌时间被移除
            target.invulnerableTime = 0;
        }
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

        // 检查玩家是否处于连击冷却期间
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
            return;
        }

        // 检查玩家是否正在进行自动攻击
        boolean isAutoAttacking = IS_AUTO_ATTACKING.getOrDefault(playerUUID, false);

        if (!isAutoAttacking) {
            int comboStacksBonus = getComboStacksBonus(player);
            if (comboStacksBonus <= 0) {
                cleanupPlayerState(playerUUID);
            }
            return;
        }

        // 处理自动攻击
        handleAutoAttack(player);
    }

    /**
     * 处理自动攻击逻辑
     */
    private static void handleAutoAttack(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        LivingEntity target = CURRENT_TARGETS.get(playerUUID);

        if (target == null || !target.isAlive()) {
            endAutoAttack(player);
            return;
        }

        int remainingCombo = REMAINING_COMBO.getOrDefault(playerUUID, 0);

        if (remainingCombo <= 0) {
            endAutoAttack(player);
            return;
        }

        int delay = AUTO_ATTACK_DELAY.getOrDefault(playerUUID, 0);
        if (delay > 0) {
            AUTO_ATTACK_DELAY.put(playerUUID, delay - 1);
            return;
        }

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

        // 移除目标无敌时间
        target.invulnerableTime = 0;

        // 直接调用player.attack()触发自动攻击
        player.attack(target);

        // 攻击后立即移除临时攻击速度加成
        if (attackSpeedAttribute != null) {
            attackSpeedAttribute.getModifiers().stream()
                .filter(modifier -> modifier.id().equals(tempModifierId))
                .forEach(attackSpeedAttribute::removeModifier);
        }

        // 攻击后再次移除目标无敌时间
        target.invulnerableTime = 0;

        // 通过管理器处理跨系统触发（强袭、电能释放）
        AttackModeManager.onBurstFireAutoAttack(player, target);

        // 连击数-1
        remainingCombo--;
        REMAINING_COMBO.put(playerUUID, remainingCombo);

        if (remainingCombo > 0 && target.isAlive()) {
            AUTO_ATTACK_DELAY.put(playerUUID, BURST_DELAY_TICKS);
        } else {
            endAutoAttack(player);
        }
    }

    /**
     * 结束自动攻击，进入连击冷却
     */
    private static void endAutoAttack(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        int totalComboStacks = 1 + getComboStacksBonus(player);

        // 连击冷却时间按触发时快照的有效攻速计算（借用属性系统读取，已叠加玩家所有攻速修饰符）
        double attackSpeed = ATTACK_SPEED_SNAPSHOTS.getOrDefault(playerUUID, captureEffectiveAttackSpeed(player));
        int cooldownTicks = calcComboCooldownTicks(totalComboStacks, attackSpeed);

        COMBO_COOLDOWN.put(playerUUID, cooldownTicks);

        com.gytrinket.gytrinket.network.NetworkHandler.sendComboCooldownToPlayer(player, true, cooldownTicks);

        // 同步点射结束状态到客户端
        com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, false);

        IS_AUTO_ATTACKING.put(playerUUID, false);
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
        ATTACK_SPEED_SNAPSHOTS.remove(playerUUID);
    }

    /**
     * 由强袭自动攻击触发点射。
     * 记录目标并开始点射自动攻击流程。
     */
    public static void startBurstFromAssault(ServerPlayer player, LivingEntity target) {
        UUID playerUUID = player.getUUID();

        // 攻击锁定时禁用点射
        if (PlayerAttackLockManager.isLocked(playerUUID)) {
            return;
        }

        // 如果已在点射中或冷却中，不重复触发
        if (IS_AUTO_ATTACKING.getOrDefault(playerUUID, false) || isInComboCooldown(playerUUID)) {
            return;
        }

        int comboStacksBonus = getComboStacksBonus(player);
        if (comboStacksBonus <= 0) {
            return;
        }

        // 记录目标
        CURRENT_TARGETS.put(playerUUID, target);

        // 连击段数
        int comboStacks = 1 + comboStacksBonus;
        int remainingCombo = comboStacks - 1; // 本次强袭攻击已消耗一段
        REMAINING_COMBO.put(playerUUID, remainingCombo);

        // 开始自动攻击流程
        IS_AUTO_ATTACKING.put(playerUUID, true);
        AUTO_ATTACK_DELAY.put(playerUUID, BURST_DELAY_TICKS);

        // 快照触发时的有效攻速，用于连击冷却计算
        ATTACK_SPEED_SNAPSHOTS.put(playerUUID, captureEffectiveAttackSpeed(player));

        // 同步点射进行中状态到客户端
        com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);
    }

    /**
     * 清理玩家所有状态
     */
    private static void cleanupPlayerState(UUID playerUUID) {
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        IS_AUTO_ATTACKING.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
        ATTACK_SPEED_SNAPSHOTS.remove(playerUUID);
    }

    /**
     * 获取玩家是否处于连击冷却状态
     */
    public static boolean isInComboCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN.containsKey(playerUUID);
    }

    /**
     * 获取玩家是否处于点射状态
     */
    public static boolean isInBurstFireState(Player player) {
        return IS_AUTO_ATTACKING.getOrDefault(player.getUUID(), false);
    }

    /**
     * 取消玩家的点射状态（由攻击锁定调用）
     */
    public static void cancelBurstFire(UUID playerUUID) {
        IS_AUTO_ATTACKING.remove(playerUUID);
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
        ATTACK_SPEED_SNAPSHOTS.remove(playerUUID);
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

        UUID playerUUID = player.getUUID();
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        IS_AUTO_ATTACKING.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
        ATTACK_SPEED_SNAPSHOTS.remove(playerUUID);
        COMBO_COOLDOWN.remove(playerUUID);
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
        if (IS_AUTO_ATTACKING.getOrDefault(playerUUID, false)) {
            com.gytrinket.gytrinket.network.NetworkHandler.sendBurstFiringToPlayer(player, false);
        }

        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        IS_AUTO_ATTACKING.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
        ATTACK_SPEED_SNAPSHOTS.remove(playerUUID);
        COMBO_COOLDOWN.remove(playerUUID);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        CURRENT_TARGETS.clear();
        REMAINING_COMBO.clear();
        IS_AUTO_ATTACKING.clear();
        AUTO_ATTACK_DELAY.clear();
        ATTACK_SPEED_SNAPSHOTS.clear();
        COMBO_COOLDOWN.clear();
    }
}
