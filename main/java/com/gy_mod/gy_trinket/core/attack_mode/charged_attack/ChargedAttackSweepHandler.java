package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能攻击横扫增强处理
 * <p>
 * 充能释放时使用剑类物品：
 * 1. 必定触发横扫攻击（无视冲刺、移动等原版限制）
 * 2. 横扫伤害根据充能值提升（每点充能值+10%，最高100%加成）
 * 3. 横扫范围根据充能值扩大（每点+10%，无上限）
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackSweepHandler {

    private ChargedAttackSweepHandler() {}

    /**
     * 当前攻击的主要目标，用于区分直接攻击目标和横扫目标
     */
    private static final Map<UUID, Entity> PRIMARY_TARGETS = new ConcurrentHashMap<>();

    /**
     * 当前攻击中已被原版横扫命中的实体ID集合
     * 在attack()开头清空，在LivingHurtEvent中记录，在补伤时查询
     */
    private static final Map<UUID, Set<Integer>> SWEPT_ENTITY_IDS = new ConcurrentHashMap<>();

    public static void setPrimaryTarget(UUID playerUUID, Entity target) {
        PRIMARY_TARGETS.put(playerUUID, target);
    }

    public static void removePrimaryTarget(UUID playerUUID) {
        PRIMARY_TARGETS.remove(playerUUID);
    }

    /**
     * 开始一次新的攻击，清空已命中实体记录
     */
    public static void startSweepAttack(UUID playerUUID) {
        SWEPT_ENTITY_IDS.put(playerUUID, ConcurrentHashMap.newKeySet());
    }

    /**
     * 记录一个被横扫命中的实体
     */
    public static void recordSweptEntity(UUID playerUUID, int entityId) {
        Set<Integer> set = SWEPT_ENTITY_IDS.get(playerUUID);
        if (set != null) {
            set.add(entityId);
        }
    }

    /**
     * 检查实体是否已被横扫命中
     */
    public static boolean isEntitySwept(UUID playerUUID, int entityId) {
        Set<Integer> set = SWEPT_ENTITY_IDS.get(playerUUID);
        return set != null && set.contains(entityId);
    }

    /**
     * 结束攻击，清空记录
     */
    public static void endSweepAttack(UUID playerUUID) {
        SWEPT_ENTITY_IDS.remove(playerUUID);
    }

    /**
     * 判断目标是否为横扫目标（非主要攻击目标）
     */
    public static boolean isSweepTarget(UUID playerUUID, Entity target) {
        Entity primaryTarget = PRIMARY_TARGETS.get(playerUUID);
        return primaryTarget != null && primaryTarget != target;
    }

    /**
     * 判断物品是否支持横扫动作（剑类）
     */
    public static boolean isSwordItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
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
