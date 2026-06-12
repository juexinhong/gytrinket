package com.gy_mod.gy_trinket.core.attack_mode.burst_fire;

import com.gy_mod.gy_trinket.core.attack_mode.AttackModeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

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
 * 6. 连击结束，进入冷却
 * <p>
 * 跨系统交互通过 AttackModeManager 策略管理：
 * - 点射自动攻击后 → 管理器根据策略决定是否触发强袭/电能释放
 * - 强袭自动攻击后 → 管理器调用 startBurstFromAssault 触发点射
 * - 充能释放后 → 管理器标记触发点射
 * - 在含充能的组合中，点射不会自主触发，只能由充能释放后触发
 * <p>
 * 启用条件：玩家的 combo 属性 > 0
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class BurstFireManager {

    // 延迟时间：2刻 = 0.08秒
    private static final int BURST_DELAY_TICKS = 2;

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

    /**
     * 获取玩家的连击段数加成
     */
    private static int getComboStacksBonus(Player player) {
        double combo = AttributeManager.getPlayerAttribute(player.getUUID(), "combo");
        return (int) Math.floor(combo);
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

        // 移除目标无敌时间，确保所有攻击都能命中
        target.invulnerableTime = 0;

        // 检查是否正在进行自动攻击
        boolean isAutoAttack = IS_AUTO_ATTACKING.getOrDefault(playerUUID, false);

        if (!isAutoAttack) {
            // 检查攻击强度是否为100%
            float attackStrength = player.getAttackStrengthScale(0.0F);
            if (attackStrength < 1.0F) {
                return;
            }

            // 检查是否是充能释放后触发的点射
            boolean fromChargedRelease = AttackModeManager.consumePendingBurstFromCharged(playerUUID);

            // 根据策略检查点射是否可以由正常攻击触发
            boolean canTriggerFromNormal = AttackModeManager.canBurstFireTriggerFromNormalAttack(playerUUID);
            if (!canTriggerFromNormal && !fromChargedRelease) {
                return; // 当前组合不允许点射自主触发，也没有充能释放触发标记
            }

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
            com.gy_mod.gy_trinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);

            // 设置首次自动攻击延迟
            AUTO_ATTACK_DELAY.put(playerUUID, BURST_DELAY_TICKS);
        } else {
            // 自动攻击命中，确保目标无敌时间被移除
            target.invulnerableTime = 0;
        }
    }

    /**
     * 监听玩家每刻更新事件
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
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
                com.gy_mod.gy_trinket.network.NetworkHandler.sendComboCooldownToPlayer(player, false, 0);
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
        com.gy_mod.gy_trinket.network.NetworkHandler.sendAttackStrengthToPlayer(player, true);

        // 临时增加攻击速度模拟满攻击强度
        var attackSpeedAttribute = player.getAttribute(Attributes.ATTACK_SPEED);
        UUID tempModifierUuid = UUID.fromString("d4e5f6a7-b8c9-0123-def4-567890abcdef");
        String tempModifierName = "burst_fire_temporary_attack_speed";
        if (attackSpeedAttribute != null) {
            attackSpeedAttribute.getModifiers().stream()
                .filter(modifier -> modifier.getId().equals(tempModifierUuid))
                .forEach(attackSpeedAttribute::removeModifier);

            var modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                tempModifierUuid,
                tempModifierName,
                100000000.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL
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
                .filter(modifier -> modifier.getId().equals(tempModifierUuid))
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

        // 计算连击冷却时间：基于基础攻击速度（不含强袭加成）
        double baseAttackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double attackCooldown = 20.0 / baseAttackSpeed;
        int cooldownTicks = (int) Math.ceil(attackCooldown * totalComboStacks);

        COMBO_COOLDOWN.put(playerUUID, cooldownTicks);

        com.gy_mod.gy_trinket.network.NetworkHandler.sendComboCooldownToPlayer(player, true, cooldownTicks);

        // 同步点射结束状态到客户端
        com.gy_mod.gy_trinket.network.NetworkHandler.sendBurstFiringToPlayer(player, false);

        IS_AUTO_ATTACKING.put(playerUUID, false);
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
    }

    /**
     * 由强袭自动攻击触发点射。
     * 记录目标并开始点射自动攻击流程。
     */
    public static void startBurstFromAssault(ServerPlayer player, LivingEntity target) {
        UUID playerUUID = player.getUUID();

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

        // 同步点射进行中状态到客户端
        com.gy_mod.gy_trinket.network.NetworkHandler.sendBurstFiringToPlayer(player, true);
    }

    /**
     * 清理玩家所有状态
     */
    private static void cleanupPlayerState(UUID playerUUID) {
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        IS_AUTO_ATTACKING.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
    }

    /**
     * 获取玩家是否处于连击冷却状态
     */
    public static boolean isInComboCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN.containsKey(playerUUID);
    }

    /**
     * 获取玩家剩余连击数
     */
    public static int getRemainingCombo(Player player) {
        return REMAINING_COMBO.getOrDefault(player.getUUID(), 0);
    }

    /**
     * 获取玩家剩余冷却时间（刻）
     */
    public static int getRemainingCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN.getOrDefault(playerUUID, 0);
    }

    /**
     * 获取玩家是否处于点射状态
     */
    public static boolean isInBurstFireState(Player player) {
        return IS_AUTO_ATTACKING.getOrDefault(player.getUUID(), false);
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
        CURRENT_TARGETS.remove(playerUUID);
        REMAINING_COMBO.remove(playerUUID);
        IS_AUTO_ATTACKING.remove(playerUUID);
        AUTO_ATTACK_DELAY.remove(playerUUID);
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
        COMBO_COOLDOWN.clear();
    }
}
