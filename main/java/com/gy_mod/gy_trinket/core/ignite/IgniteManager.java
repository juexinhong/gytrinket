package com.gy_mod.gy_trinket.core.ignite;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
/**
 * 点燃系统管理器
 * <p>
 * 功能：
 * 1. 管理所有实体的多个点燃状态
 * 2. 处理点燃伤害（每秒一次）
 * 3. 支持同名点燃叠加控制
 * 4. 每个点燃独立计时和伤害
 * 5. 自动清理完成的点燃
 * <p>
 * 扩展性：
 * - 可通过接口自定义点燃源
 * - 可添加点燃效果修饰器
 * - 可扩展不同类型的点燃伤害
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class IgniteManager {

    /** 伤害间隔（每20刻造成伤害，即每秒一次） */
    private static final int DAMAGE_INTERVAL = 20;

    /** 实体点燃数据映射（目标UUID -> 点燃数据列表） */
    private static final Map<UUID, List<IgniteData>> ENTITY_IGNITE_DATA = new HashMap<>();
    /** 上次处理的游戏刻（用于防止同一刻重复处理） */
    private static long lastProcessedTick = -1;

    /**
     * 对目标施加点燃
     * <p>
     * 使用默认参数
     *
     * @param target 目标
     * @param source 点燃源
     * @param igniteName 点燃名称
     * @param stackable 是否可叠加
     */
    public static void applyIgnite(LivingEntity target, IIgniteSource source, String igniteName, boolean stackable) {
        applyIgnite(target, igniteName, (float)Config.getIgniteDefaultDamage(), source, Config.getIgniteDefaultDuration(), stackable);
    }

    /**
     * 对目标施加点燃
     * <p>
     * 使用自定义参数
     *
     * @param target 目标
     * @param igniteName 点燃名称
     * @param damagePerTick 单次伤害
     * @param source 点燃源
     * @param durationSeconds 持续时间（秒）
     * @param stackable 是否可叠加
     */
    public static void applyIgnite(LivingEntity target, String igniteName, float damagePerTick, IIgniteSource source, int durationSeconds, boolean stackable) {
        int durationTicks = durationSeconds * 20 + 1;
        applyIgniteTicks(target, igniteName, damagePerTick, source, durationTicks, stackable);
    }

    /**
     * 对目标施加点燃（使用游戏刻）
     * @param target 目标
     * @param igniteName 点燃名称
     * @param damagePerTick 单次伤害
     * @param source 点燃源
     * @param durationTicks 持续时间（游戏刻）
     * @param stackable 是否可叠加
     */
    public static void applyIgniteTicks(LivingEntity target, String igniteName, float damagePerTick, IIgniteSource source, int durationTicks, boolean stackable) {
        if (target == null || target.isDeadOrDying()) {
            return;
        }

        UUID targetUUID = target.getUUID();
        long currentTick = target.level().getGameTime();

        List<IgniteData> igniteDataList = getOrCreateIgniteDataList(targetUUID);

        // 不可叠加时，检查是否已有同名点燃，有则不再创建
        if (!stackable) {
            boolean hasExisting = igniteDataList.stream()
                .anyMatch(data -> data.getIgniteName().equals(igniteName));
            if (hasExisting) {
                return;
            }
        }

        // 可叠加或没有找到同名点燃，创建新的点燃
        IgniteData newIgniteData = new IgniteData(target, igniteName, damagePerTick, source, durationTicks, stackable);
        newIgniteData.startIgnite(currentTick);
        igniteDataList.add(newIgniteData);
    }

    /**
     * 获取或创建实体的点燃数据列表
     * @param targetUUID 目标UUID
     * @return 点燃数据列表
     */
    private static List<IgniteData> getOrCreateIgniteDataList(UUID targetUUID) {
        return ENTITY_IGNITE_DATA.computeIfAbsent(
            targetUUID,
            uuid -> new ArrayList<>()
        );
    }

    /**
     * 获取实体的点燃数据列表
     * @param targetUUID 目标UUID
     * @return 点燃数据列表，如果没有返回空列表
     */
    public static List<IgniteData> getIgniteDataList(UUID targetUUID) {
        return ENTITY_IGNITE_DATA.getOrDefault(targetUUID, Collections.emptyList());
    }

    /**
     * 检查实体是否正在被点燃
     * @param target 目标
     * @return 是否正在点燃
     */
    public static boolean isIgniting(LivingEntity target) {
        if (target == null) {
            return false;
        }
        List<IgniteData> list = getIgniteDataList(target.getUUID());
        return list.stream().anyMatch(IgniteData::isIgniting);
    }

    /**
     * 检查实体是否有指定名称的点燃
     * @param target 目标
     * @param igniteName 点燃名称
     * @return 是否有该名称的点燃
     */
    public static boolean hasIgniteName(LivingEntity target, String igniteName) {
        if (target == null) {
            return false;
        }
        List<IgniteData> list = getIgniteDataList(target.getUUID());
        return list.stream()
            .filter(IgniteData::isIgniting)
            .anyMatch(data -> data.getIgniteName().equals(igniteName));
    }

    /**
     * 清除实体的所有点燃
     * @param target 目标
     */
    public static void clearIgnite(LivingEntity target) {
        if (target != null) {
            ENTITY_IGNITE_DATA.remove(target.getUUID());
        }
    }

    /**
     * 清除实体指定名称的点燃
     * @param target 目标
     * @param igniteName 点燃名称
     */
    public static void clearIgniteName(LivingEntity target, String igniteName) {
        if (target == null) {
            return;
        }
        List<IgniteData> list = ENTITY_IGNITE_DATA.get(target.getUUID());
        if (list != null) {
            list.removeIf(data -> data.getIgniteName().equals(igniteName));
            if (list.isEmpty()) {
                ENTITY_IGNITE_DATA.remove(target.getUUID());
            }
        }
    }

    /**
     * 服务器刻事件处理（处理所有实体）
     * 使用 ServerTickEvent 而非 LevelTickEvent，避免多维度加载时每维度各触发一次导致计时加速。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long currentTick = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD).getGameTime();

        // 确保每个游戏刻只处理一次
        if (currentTick == lastProcessedTick) {
            return;
        }
        lastProcessedTick = currentTick;

        for (UUID uuid : new HashSet<>(ENTITY_IGNITE_DATA.keySet())) {
            processIgniteTick(uuid, currentTick);
        }
    }

    /**
     * 处理点燃刻
     * @param targetUUID 目标UUID
     * @param currentTick 当前游戏刻
     */
    private static void processIgniteTick(UUID targetUUID, long currentTick) {
        List<IgniteData> igniteDataList = ENTITY_IGNITE_DATA.get(targetUUID);
        if (igniteDataList == null || igniteDataList.isEmpty()) {
            return;
        }

        // 获取目标实体
        LivingEntity target = null;
        for (IgniteData data : igniteDataList) {
            if (data.getTarget() != null && data.getTarget().isAlive()) {
                target = data.getTarget();
                break;
            }
        }

        if (target == null || !target.isAlive()) {
            ENTITY_IGNITE_DATA.remove(targetUUID);
            return;
        }

        // 更新所有点燃数据的目标引用
        for (IgniteData data : igniteDataList) {
            data.setTarget(target);
        }

        List<IgniteData> toRemove = new ArrayList<>();

        for (IgniteData igniteData : igniteDataList) {
            if (!igniteData.isIgniting()) {
                continue;
            }

            if (igniteData.isComplete(currentTick)) {
                toRemove.add(igniteData);
                continue;
            }

            // 每DAMAGE_INTERVAL刻造成伤害
            long elapsed = currentTick - igniteData.getStartTick();
            if (elapsed % DAMAGE_INTERVAL == 0) {
                applyIgniteDamage(target, igniteData);
            }
        }

        igniteDataList.removeAll(toRemove);

        if (igniteDataList.isEmpty()) {
            ENTITY_IGNITE_DATA.remove(targetUUID);
        }
    }

    /**
     * 应用点燃伤害
     * @param target 目标
     * @param igniteData 点燃数据
     */
    private static void applyIgniteDamage(LivingEntity target, IgniteData igniteData) {
        if (target.level().isClientSide) {
            return;
        }

        float damage = igniteData.getDamagePerTick();
        Entity initiator = igniteData.getInitiator();

        // 不足以斩杀时不归属攻击者，足够斩杀时根据斩杀归属开关决定
        boolean canKill = damage >= target.getHealth();
        Entity actualInitiator = canKill && isExecuteAttributionEnabled(initiator) ? initiator : null;

        com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager.markNoKnockback(target.getUUID());
        target.invulnerableTime = 0;
        target.hurt(ModDamageTypes.getOnFireDamageSource(target.level(), actualInitiator), damage);
        target.invulnerableTime = 0;

        spawnIgniteParticles(target);
    }

    /**
     * 判断斩杀归属是否启用
     */
    private static boolean isExecuteAttributionEnabled(Entity initiator) {
        if (initiator instanceof net.minecraft.world.entity.player.Player player) {
            return com.gy_mod.gy_trinket.core.execute.ExecuteToggleManager.isExecuteEnabled(player);
        }
        if (initiator instanceof com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity drone) {
            net.minecraft.world.entity.Entity owner = drone.getOwner();
            if (owner instanceof net.minecraft.world.entity.player.Player player) {
                return com.gy_mod.gy_trinket.core.execute.ExecuteToggleManager.isExecuteEnabled(player);
            }
        }
        return true;
    }

    /**
     * 在目标位置生成点燃粒子效果
     * @param target 目标实体
     */
    @SuppressWarnings("resource")
    private static void spawnIgniteParticles(LivingEntity target) {
        ServerLevel serverLevel = (ServerLevel) target.level();
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2.0;
        double z = target.getZ();

        serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.3, 0.3, 0.3, 0.05);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.5, 0.5, 0.5, 0.05);
    }

    /**
     * 获取实体的所有点燃数据
     * @param target 目标
     * @return 点燃数据列表
     */
    public static List<IgniteData> getAllIgniteData(LivingEntity target) {
        if (target == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(getIgniteDataList(target.getUUID()));
    }

    /**
     * 清除所有点燃数据
     */
    public static void clearAllIgniteData() {
        ENTITY_IGNITE_DATA.clear();
    }
}
