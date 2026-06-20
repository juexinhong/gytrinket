package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 无敌标记管理器
 * <p>
 * 替代原生的 setInvulnerable(true) 机制，使用自定义标记来阻止伤害。
 * 原生无敌状态会导致敌对生物丢失仇恨，而标记系统通过取消攻击事件来实现相同效果，
 * 不会影响AI目标选择。
 * </p>
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class InvincibilityMarkerManager {

    /** 实体无敌标记映射：实体UUID -> 剩余刻数 */
    private static final Map<UUID, Integer> MARKER_DATA = new HashMap<>();

    /**
     * 监听攻击事件（最高优先级）
     * <p>
     * 当被攻击实体拥有无敌标记时，取消攻击事件。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (hasMarker(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 监听世界刻事件
     * <p>
     * 每刻更新所有标记的剩余时间，到期自动移除。
     * </p>
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        MARKER_DATA.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            entry.setValue(remaining);
            return false;
        });
    }

    /**
     * 为实体添加无敌标记
     * @param entity 目标实体
     * @param ticks 持续时间（刻）
     */
    public static void addMarker(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0) {
            return;
        }
        MARKER_DATA.put(entity.getUUID(), ticks);
    }

    /**
     * 检查实体是否拥有无敌标记
     * @param entity 目标实体
     * @return 是否拥有标记
     */
    public static boolean hasMarker(LivingEntity entity) {
        return entity != null && MARKER_DATA.containsKey(entity.getUUID());
    }

    /**
     * 移除实体的无敌标记
     * @param entity 目标实体
     */
    public static void removeMarker(LivingEntity entity) {
        if (entity != null) {
            MARKER_DATA.remove(entity.getUUID());
        }
    }
}
