package com.gytrinket.gytrinket.core.attack_mode.charged_attack;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能攻击横扫增强处理
 * <p>
 * 充能释放时使用剑类物品：
 * 1. 必定触发横扫攻击（无视冲刺、移动等原版限制）
 * 2. 横扫伤害根据充能值提升（每点充能值+10%，最高100%加成）
 * 3. 横扫范围根据充能值扩大（由 PlayerMixin 处理，每点+10%，无上限）
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackSweepHandler {

    private ChargedAttackSweepHandler() {}

    /**
     * 当前攻击的主要目标，用于区分直接攻击目标和横扫目标
     * 由 PlayerMixin 在 attack() 调用时设置，在伤害处理时读取
     */
    private static final Map<UUID, Entity> PRIMARY_TARGETS = new ConcurrentHashMap<>();

    public static void setPrimaryTarget(UUID playerUUID, Entity target) {
        PRIMARY_TARGETS.put(playerUUID, target);
    }

    public static void removePrimaryTarget(UUID playerUUID) {
        PRIMARY_TARGETS.remove(playerUUID);
    }

    /**
     * 判断目标是否为横扫目标（非主要攻击目标）
     */
    public static boolean isSweepTarget(UUID playerUUID, Entity target) {
        Entity primaryTarget = PRIMARY_TARGETS.get(playerUUID);
        return primaryTarget != null && primaryTarget != target;
    }

    /**
     * 充能释放时强制启用横扫攻击
     */
    @SubscribeEvent
    public static void onSweepAttack(SweepAttackEvent event) {
        Player player = (Player) event.getEntity();
        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(player.getUUID());
        if (chargeValue <= 0) {
            return;
        }

        // 主手物品必须支持横扫动作（剑类）
        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.canPerformAction(ItemAbilities.SWORD_SWEEP)) {
            return;
        }

        // 强制启用横扫
        event.setSweeping(true);
    }

    /**
     * 计算充能横扫伤害倍率
     * 每点充能值提升10%横扫伤害，最高100%加成
     *
     * @param chargeValue 充能值
     * @return 横扫伤害倍率（1.0 = 无加成，2.0 = 100%加成）
     */
    public static float getSweepDamageMultiplier(double chargeValue) {
        float bonus = (float) Math.min(chargeValue * 0.1, 1.0);
        return 1.0F + bonus;
    }

    /**
     * 计算充能横扫范围倍率
     * 每点充能值提升10%横扫范围，无上限
     *
     * @param chargeValue 充能值
     * @return 横扫范围倍率（1.0 = 无扩大）
     */
    public static float getSweepRangeMultiplier(double chargeValue) {
        return 1.0F + (float) (chargeValue * 0.1);
    }
}
