package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 次级攻击伤害合并管理器
 * <p>
 * 无人机子弹、僚机爆破弹、模拟爆炸、能量波爆炸等次级攻击的伤害，
 * 对同一目标的同类型伤害在配置的时间窗口内累积合并，时间结束后一次性施加，
 * 降低实体受击频率（减少无敌帧重置/伤害事件开销）。
 * <p>
 * 合并仅推迟 {@code hurt} 的时机，伤害施加时保留原有机制
 * （移除无敌时间、易伤施加、斩杀归属等由调用方提供的 {@link HitApplier} 保证）。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class SecondaryDamageMerger {

    /** 伤害施加回调：在时间窗口结束时用累积后的总伤害执行完整命中逻辑 */
    @FunctionalInterface
    public interface HitApplier {
        void apply(LivingEntity target, float totalDamage);
    }

    private record MergeKey(UUID targetId, String typeId) {}

    private static final class PendingBatch {
        LivingEntity target;
        float totalDamage;
        long expireTick;
        HitApplier applier;
    }

    /** 待施加批次：键 = (目标UUID, 类型ID) */
    private static final Map<MergeKey, PendingBatch> PENDING = new ConcurrentHashMap<>();

    private SecondaryDamageMerger() {}

    /**
     * 累积一次次级攻击伤害。
     * <p>
     * 未启用合并或目标无效时立即调用 applier 施加。
     *
     * @param target   目标实体
     * @param typeId   伤害类型 ID（同类型才会合并，如 drone_bullet/energy_wave）
     * @param damage   本次伤害
     * @param applier  伤害施加回调（用累积总伤害执行完整命中逻辑）
     */
    public static void accumulate(LivingEntity target, String typeId, float damage, HitApplier applier) {
        if (target == null || !target.isAlive()) {
            return;
        }
        if (!Config.SECONDARY_DAMAGE_MERGE_ENABLED.get()) {
            applier.apply(target, damage);
            return;
        }

        long now = target.level().getGameTime();
        MergeKey key = new MergeKey(target.getUUID(), typeId);
        PendingBatch batch = PENDING.get(key);
        if (batch == null) {
            batch = new PendingBatch();
            batch.target = target;
            batch.expireTick = now + Config.SECONDARY_DAMAGE_MERGE_WINDOW_TICKS.get();
            batch.applier = applier;
            PENDING.put(key, batch);
        }
        batch.totalDamage += damage;
    }

    /** 每刻检查到期的合并批次并施加 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }

        long now = event.getServer().overworld().getGameTime();
        List<MergeKey> toApply = new ArrayList<>();
        for (Map.Entry<MergeKey, PendingBatch> entry : PENDING.entrySet()) {
            if (entry.getValue().expireTick <= now) {
                toApply.add(entry.getKey());
            }
        }

        for (MergeKey key : toApply) {
            PendingBatch batch = PENDING.remove(key);
            if (batch == null || batch.target == null || !batch.target.isAlive()
                    || batch.target.level().isClientSide || !(batch.target.level() instanceof ServerLevel)) {
                continue;
            }
            if (batch.totalDamage <= 0) {
                continue;
            }
            batch.applier.apply(batch.target, batch.totalDamage);
        }
    }

    /** 清除指定实体的所有待施加批次（实体死亡/移除时调用） */
    public static void removeTarget(UUID targetId) {
        PENDING.keySet().removeIf(key -> key.targetId().equals(targetId));
    }

    public static void clearAll() {
        PENDING.clear();
    }
}
