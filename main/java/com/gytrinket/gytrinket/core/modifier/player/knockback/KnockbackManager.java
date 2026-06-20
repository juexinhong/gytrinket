package com.gytrinket.gytrinket.core.modifier.player.knockback;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家击退管理器
 * <p>
 * 功能：
 * 1. 监听属性计算完毕事件，缓存玩家击退属性
 * 2. 监听玩家攻击事件，在目标上标记击退比例
 * 3. 监听LivingKnockBackEvent，处理以下逻辑：
 *    - 检查目标是否被灼烧/点燃伤害标记，如果是则取消击退
 *    - 检查目标是否有击退比例标记，按比例调整击退力度（小于1减弱，大于1增强）
 * 4. 提供击退标记机制，用于取消特定伤害来源的击退效果
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class KnockbackManager {

    private static final Map<UUID, Double> PLAYER_KNOCKBACK_MAP = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> NO_KNOCKBACK_TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> KNOCKBACK_REDUCTION_TARGETS = new ConcurrentHashMap<>();

    /**
     * 标记目标实体，使其在下次被击退时取消击退效果
     * 用于灼烧、点燃等不希望产生击退的伤害来源
     *
     * @param targetUUID 目标实体的UUID
     */
    public static void markNoKnockback(UUID targetUUID) {
        NO_KNOCKBACK_TARGETS.put(targetUUID, true);
    }

    /**
     * 检查目标实体是否被标记为无击退
     *
     * @param targetUUID 目标实体的UUID
     * @return 是否被标记
     */
    public static boolean hasNoKnockbackMark(UUID targetUUID) {
        return NO_KNOCKBACK_TARGETS.containsKey(targetUUID);
    }

    /**
     * 移除目标的"无击退"标记
     *
     * @param targetUUID 目标实体的UUID
     */
    public static void removeNoKnockbackMark(UUID targetUUID) {
        NO_KNOCKBACK_TARGETS.remove(targetUUID);
    }

    /**
     * 标记目标实体，按比例减少击退力度
     * 用于玩家攻击时根据击退属性减少击退效果
     *
     * @param targetUUID 目标实体的UUID
     * @param reductionRatio 击退减少比例（0=完全取消，1=无减少）
     */
    public static void markKnockbackReduction(UUID targetUUID, double reductionRatio) {
        KNOCKBACK_REDUCTION_TARGETS.put(targetUUID, reductionRatio);
    }

    /**
     * 获取目标的击退减少比例
     *
     * @param targetUUID 目标实体的UUID
     * @return 击退减少比例，如果没有标记则返回1.0
     */
    public static double getKnockbackReduction(UUID targetUUID) {
        return KNOCKBACK_REDUCTION_TARGETS.getOrDefault(targetUUID, 1.0);
    }

    /**
     * 移除目标的击退减少标记
     *
     * @param targetUUID 目标实体的UUID
     */
    public static void removeKnockbackReduction(UUID targetUUID) {
        KNOCKBACK_REDUCTION_TARGETS.remove(targetUUID);
    }

    /**
     * 监听属性计算完毕事件
     * 缓存玩家的击退属性值（不再使用修饰符）
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        double knockbackPercent = AttributeManager.getPlayerAttribute(playerUUID, "player_knockback_percent");
        PLAYER_KNOCKBACK_MAP.put(playerUUID, knockbackPercent);
    }

    /**
     * 监听玩家攻击事件
     * 如果玩家的击退属性不等于1，在目标上标记击退比例（小于1减弱，大于1增强）
     */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        if (!(player instanceof ServerPlayer) || !(target instanceof LivingEntity)) {
            return;
        }

        double knockbackPercent = PLAYER_KNOCKBACK_MAP.getOrDefault(player.getUUID(), 1.0);

        if (knockbackPercent != 1.0) {
            markKnockbackReduction(target.getUUID(), knockbackPercent);
        }
    }

    /**
     * 监听实体击退事件
     * 1. 如果目标被标记为无击退（灼烧/点燃），取消击退
     * 2. 如果目标有击退减少标记，按比例减少击退力度
     *
     * @param event 击退事件
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        UUID targetUUID = target.getUUID();

        if (hasNoKnockbackMark(targetUUID)) {
            event.setCanceled(true);
            removeNoKnockbackMark(targetUUID);
            return;
        }

        double ratio = getKnockbackReduction(targetUUID);
        if (ratio != 1.0) {
            event.setStrength(event.getStrength() * (float) ratio);
            removeKnockbackReduction(targetUUID);
        }
    }

    /**
     * 监听玩家登出事件，清理数据
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PLAYER_KNOCKBACK_MAP.remove(player.getUUID());
    }

    /**
     * 获取玩家的击退属性值
     *
     * @param playerUUID 玩家UUID
     * @return 击退属性值，默认为1.0（无加成）
     */
    public static double getPlayerKnockback(UUID playerUUID) {
        return PLAYER_KNOCKBACK_MAP.getOrDefault(playerUUID, 1.0);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        PLAYER_KNOCKBACK_MAP.clear();
        NO_KNOCKBACK_TARGETS.clear();
        KNOCKBACK_REDUCTION_TARGETS.clear();
    }
}