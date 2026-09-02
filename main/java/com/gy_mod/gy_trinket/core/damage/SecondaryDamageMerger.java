package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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

    /** 爆炸伤害收集期时长：0.5秒 = 10刻 */
    private static final long EXPLOSION_COLLECT_TICKS = 10L;

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

    /**
     * 累积一次爆炸伤害（归属玩家时使用"先应用后收集"模式）。
     * <p>
     * 与其他伤害"先收集后应用"不同：归属玩家的爆炸伤害在目标第一次受到该类型时
     * 立即结算本次伤害，随后开启 0.5 秒收集期；收集期内的后续伤害累积，
     * 收集期结束时一次性施加。之后再受到该类型伤害时又立即结算并开启新的收集期。
     * <p>
     * 未归属玩家的爆炸伤害与普通伤害一致（先收集后应用）。
     *
     * @param ownedByPlayer 爆炸是否归属玩家
     */
    public static void accumulateExplosion(LivingEntity target, String typeId, float damage,
                                           boolean ownedByPlayer, HitApplier applier) {
        if (!ownedByPlayer) {
            accumulate(target, typeId, damage, applier);
            return;
        }
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
            // 第一次受到该爆炸伤害：立即结算本次，随后开启收集期（收集期从空开始）
            applier.apply(target, damage);
            if (!target.isAlive()) {
                return;
            }
            batch = new PendingBatch();
            batch.target = target;
            batch.expireTick = now + EXPLOSION_COLLECT_TICKS;
            batch.applier = applier;
            PENDING.put(key, batch);
            return;
        }
        batch.totalDamage += damage;
    }

    /** 每刻检查到期的合并批次并施加 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
