package com.gytrinket.gytrinket.core.weapon.flamespear;

import com.gytrinket.gytrinket.client.effect.energywave.EnergyWaveVisualManager;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attack_mode.GrudgeManager;
import com.gytrinket.gytrinket.core.attack_mode.assault.AssaultManager;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.items.ModItems;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 焰矛（充能武器）管理器。
 * <p>
 * 制衡充能：右键使用时开始充能（isUsingItem 期间持续充能）。充能速率基础 0.1/刻，
 * 玩家的攻击速度与爆炸半径属性等量提高充能速率。充能受阻力制衡：
 * 阻力 = 系数 × 当前充能值，阻力与充能速率达到平衡时充能达到上限（充能速率越快平衡值越高）。
 * <p>
 * 充能值 = 焰矛长度（能量波长度）。使用中每刻对光束伤害范围内的敌人施加充能值等量的灼烧，并默认点燃。
 * 松开右键：充能值每刻消退 1% 当前充能值（最低消退 0.000001），并移除能量波特效。
 * 伤害检查范围 = 基础半径 × 护盾效果半径属性。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class FlameSpearManager {

    /** 松开后充能消退：每刻 8% 当前值，最低 0.2 */
    private static final double DECAY_RATIO = 0.08;
    private static final double MIN_DECAY = 0.2;

    /** 强袭适配：焰矛充能时每刻触发一次强袭的频率（固定，不随攻击速度变化） */
    private static final int ASSAULT_TRIGGER_INTERVAL = 10;

    private static final Map<UUID, SpearData> PLAYER_DATA = new HashMap<>();

    /** 模拟充能状态：玩家实体ID → 是否按住右键使用 */
    private static final Map<Integer, Boolean> SIMULATED_USING = new HashMap<>();

    /**
     * 客户端数据包回调：更新模拟充能状态。
     */
    public static void setSimulatedUsing(int playerId, boolean using) {
        if (using) {
            SIMULATED_USING.put(playerId, true);
        } else {
            SIMULATED_USING.remove(playerId);
        }
    }

    private static final class SpearData {
        double charge = 0;
        boolean waveActive = false;
        int meltTicks = 0;
        int assaultTicks = 0;
        int syncTickCounter = 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID uuid = player.getUUID();

        boolean using = isUsingFlameSpear(player);
        SpearData data = PLAYER_DATA.computeIfAbsent(uuid, k -> new SpearData());

        if (!using) {
            // 未在使用（松开右键或未手持）：充能值快速消退（每刻 8% 当前值，最低 0.2）
            double decay = Math.max(data.charge * DECAY_RATIO, MIN_DECAY);
            data.charge = Math.max(0, data.charge - decay);

            // 消退期间依旧可以攻击：充能值尚未耗尽时，继续对范围内敌人施加灼烧+点燃（伤害随充能值消退）
            double effectiveLength = 0;
            if (data.charge > 0.001) {
                double spearLength = Math.min(data.charge, EnergyWaveVisualManager.WAVE_LENGTH_CAP);

                // 熔穿：每 N 刻对范围内敌人施加可叠加易伤
                data.meltTicks++;
                boolean applyMelt = data.meltTicks >= Config.FLAME_SPEAR_MELT_INTERVAL.get();
                if (applyMelt) {
                    data.meltTicks = 0;
                }
                effectiveLength = applyBeamDamage(player, uuid, spearLength, applyMelt);
                if (effectiveLength <= 0) {
                    effectiveLength = spearLength;
                }
            }

            // 能量波完全跟随消退中的充能值；长度 = 有限穿透后的有效长度（宽度不降低）
            if (data.charge > 0.001 && data.waveActive) {
                Vec3 center = spearCenter(player);
                Vec3 dir = player.getLookAngle();
                double length = Math.min(data.charge, EnergyWaveVisualManager.WAVE_LENGTH_CAP);
                double width = spearWidth(length);
                NetworkHandler.sendDynamicEnergyWaveToAll(serverLevel, player.getId(), true,
                        center, dir, effectiveLength, width);
            } else if (data.waveActive) {
                data.waveActive = false;
                NetworkHandler.sendDynamicEnergyWaveToAll(serverLevel, player.getId(), false,
                        player.position(), Vec3.ZERO, 0, 0);
            }

            // HUD同步：消退中每3刻同步充能值与速率0（不充能）
            data.syncTickCounter++;
            if (data.syncTickCounter >= 3) {
                data.syncTickCounter = 0;
                NetworkHandler.sendFlameSpearSyncToPlayer((ServerPlayer) player, data.charge, 0);
            }

            if (data.charge <= 0) {
                // 清空HUD显示
                NetworkHandler.sendFlameSpearSyncToPlayer((ServerPlayer) player, 0, 0);
                PLAYER_DATA.remove(uuid);
            }
            return;
        }

        // 制衡充能：净充能 = 充能速率 - 阻力
        double chargeRate = computeChargeRate(player, uuid);
        double resistance = Config.FLAME_SPEAR_RESISTANCE.get() * data.charge;
        double net = Math.max(0.0, chargeRate - resistance);
        data.charge += net;

        // 强袭适配：充能时每 ASSAULT_TRIGGER_INTERVAL 刻触发一次强袭（触发频率固定，不随攻击速度变化）
        data.assaultTicks++;
        if (data.assaultTicks >= ASSAULT_TRIGGER_INTERVAL) {
            data.assaultTicks = 0;
            if (AssaultManager.hasAssault(player)) {
                AssaultManager.triggerAssault(player);
            }
        }

        // HUD同步：每3刻同步充能值与当前充能速率
        data.syncTickCounter++;
        if (data.syncTickCounter >= 3) {
            data.syncTickCounter = 0;
            NetworkHandler.sendFlameSpearSyncToPlayer((ServerPlayer) player, data.charge, computeChargeRate(player, uuid));
        }

        // 焰矛有效长度 = 充能值（长度极限 20 格，同时是伤害范围与能量波长度极限）
        double spearLength = Math.min(data.charge, EnergyWaveVisualManager.WAVE_LENGTH_CAP);

        // 熔穿：每 N 刻对范围内敌人施加可叠加易伤
        data.meltTicks++;
        boolean applyMelt = data.meltTicks >= Config.FLAME_SPEAR_MELT_INTERVAL.get();
        if (applyMelt) {
            data.meltTicks = 0;
        }

        // 每刻对光束范围内的敌人施加灼烧 + 点燃，返回有限穿透后的有效长度
        double effectiveLength = applyBeamDamage(player, uuid, spearLength, applyMelt);
        if (effectiveLength <= 0) {
            effectiveLength = spearLength;
        }

        // 动态能量波特效：长度 = 有限穿透后的有效长度（假降低，充能值不变），宽度 = 全长宽度（不降低）
        Vec3 center = spearCenter(player);
        Vec3 dir = player.getLookAngle();
        double width = spearWidth(spearLength);
        NetworkHandler.sendDynamicEnergyWaveToAll(serverLevel, player.getId(), true,
                center, dir, effectiveLength, width);
        data.waveActive = true;
    }

    /** 能量波发生位置：玩家身高一半处、朝向前 1 格（与反射护盾模块一致） */
    private static Vec3 spearCenter(Player player) {
        Vec3 base = player.position().add(0, player.getBbHeight() * 0.5, 0);
        return base.add(player.getLookAngle().normalize().scale(1.0));
    }

    /** 能量波宽度：宽度 = 长度/16（宽度极限 = 长度/12，完全跟随能量波机制） */
    private static double spearWidth(double length) {
        return EnergyWaveVisualManager.waveWidth(length);
    }

    private static boolean isUsingFlameSpear(Player player) {
        // 模拟充能：由客户端按键检测驱动（不进入真实使用物品状态，避免移动减速）
        if (!Boolean.TRUE.equals(SIMULATED_USING.get(player.getId()))) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        return !stack.isEmpty() && Config.isFlameSpearItem(stack.getItem());
    }

    /**
     * 玩家是否正在充能焰矛（供充能护盾/积怨等系统识别焰矛充能状态）。
     */
    public static boolean isSimulatedUsing(Player player) {
        return isUsingFlameSpear(player);
    }

    /**
     * 对光束范围内的敌人施加灼烧 + 点燃 + 熔穿，并返回有限穿透后的有效长度。
     * <p>
     * 有限穿透：当攻击范围找到距离玩家最近的敌人时，有效长度降低为 敌人到玩家的距离。
     * 焰矛长度同步降低（能量波长度也随之降低），但宽度不降低（仍按全长计算）；
     * 有效长度范围内的敌人才会受到攻击（穿透受限，最近敌人之后的目标不受攻击）。
     * 充能值本身不降低（假降低能量波长度）。
     *
     * @return 有限穿透后的有效长度（无敌人时返回全长）
     */
    private static double applyBeamDamage(Player player, UUID uuid, double spearLength, boolean applyMelt) {
        if (spearLength <= 0) {
            return 0;
        }

        Vec3 origin = spearCenter(player);
        Vec3 dir = player.getLookAngle().normalize();
        double width = spearWidth(spearLength); // 宽度按全长计算，有限穿透时不降低宽度
        double behindLength = 1.0;

        List<LivingEntity> entities = findSpearTargets(player, origin, dir, spearLength, width, behindLength);

        // 有限穿透：找到距离玩家最近的敌人，有效长度 = min(全长, 最近敌人到玩家的距离)
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity entity : entities) {
            nearestDist = Math.min(nearestDist, player.distanceTo(entity));
        }
        double effectiveLength = entities.isEmpty() ? spearLength : Math.min(spearLength, nearestDist);
        if (effectiveLength <= 0) {
            return 0;
        }

        // 只攻击有效长度范围内的敌人（有限穿透：最近敌人之外的目标不受攻击）
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity entity : entities) {
            if (player.distanceTo(entity) <= effectiveLength + 0.001) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            return effectiveLength;
        }

        IBurnSource burnSource = new IBurnSource.DefaultBurnSource(player);
        IIgniteSource igniteSource = new IIgniteSource.DefaultIgniteSource(player);
        // 灼烧量 = 充能值 × 伤害倍率（充能值不因有限穿透而降低）
        float burn = (float) (spearLength * Config.FLAME_SPEAR_DAMAGE_MULTIPLIER.get());

        for (LivingEntity entity : targets) {
            BurnManager.applyBurnCharge(entity, burn, burnSource);
            // 默认点燃（不叠加，参考光环护盾的点燃施加）
            IgniteManager.applyIgnite(entity, igniteSource, "flame_spear", false);

            // 熔穿：每 N 刻施加可叠加易伤
            if (applyMelt) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new com.gytrinket.gytrinket.core.vulnerability.VulnerabilityApplyEvent(
                                "flame_spear_melt",
                                Config.FLAME_SPEAR_MELT_VULNERABILITY.get().floatValue(),
                                entity,
                                true));
            }
        }
        return effectiveLength;
    }

    /**
     * 查找攻击范围内的目标（长方形包围盒，与能量波/伤害范围几何一致，含身后判定）。
     */
    private static List<LivingEntity> findSpearTargets(Player player, Vec3 origin, Vec3 dir,
                                                       double length, double width, double behindLength) {
        Vec3 behindStart = origin.subtract(dir.scale(behindLength));
        Vec3 rayEnd = origin.add(dir.scale(length));
        double inflation = width + 1.0;
        AABB box = new AABB(
                Math.min(behindStart.x, rayEnd.x) - inflation,
                Math.min(behindStart.y, rayEnd.y) - inflation,
                Math.min(behindStart.z, rayEnd.z) - inflation,
                Math.max(behindStart.x, rayEnd.x) + inflation,
                Math.max(behindStart.y, rayEnd.y) + inflation,
                Math.max(behindStart.z, rayEnd.z) + inflation
        );
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player && HostileTargetManager.shouldAttackPlayer(e, player));

        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity entity : candidates) {
            AABB entityBox = entity.getBoundingBox();
            Vec3 toEntity = entityBox.getCenter().subtract(origin);
            double along = toEntity.dot(dir);

            double halfX = entityBox.getXsize() / 2;
            double halfY = entityBox.getYsize() / 2;
            double halfZ = entityBox.getZsize() / 2;
            double aabbRadius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

            // 沿方向：实体必须在身后判定与长度范围内（考虑AABB半径）
            if (along + aabbRadius < -behindLength || along - aabbRadius > length) {
                continue;
            }

            // 长方形包围盒：恒定半宽，垂直距离在宽度内（考虑AABB半径，确保不漏判）
            double perpDistSq = toEntity.lengthSqr() - along * along;
            double perpDist = Math.sqrt(Math.max(0, perpDistSq));
            if (perpDist >= width + aabbRadius) {
                continue;
            }

            result.add(entity);
        }
        return result;
    }

    /** 获取玩家当前充能值（焰矛长度） */
    public static double getCharge(UUID uuid) {
        SpearData data = PLAYER_DATA.get(uuid);
        return data != null ? data.charge : 0.0;
    }

    /**
     * 计算当前充能速率：基础速率 × 属性加成，含充能攻击模块 +50% 加成与积怨临时速率。
     * 与焰矛充能逻辑一致，供HUD显示。
     */
    public static double computeChargeRate(Player player, UUID uuid) {
        double attackSpeed = AttributeManager.getGroupAttribute(uuid, "attack_speed");
        double explosionRadius = AttributeManager.getGroupAttribute(uuid, "explosion_radius");
        double chargeRate = Config.FLAME_SPEAR_BASE_CHARGE_RATE.get() * (attackSpeed + explosionRadius - 1.0);
        if (PlayerStoreUtils.hasActiveItem(player, Config::isChargedAttackItem)) {
            chargeRate *= 1.5;
        }
        chargeRate += GrudgeManager.getTotalGrudgeChargeRate(uuid);
        return chargeRate;
    }

    /** 获取玩家当前充能速率（仅属性加成，不含充能攻击模块与积怨） */
    public static double getChargeRate(UUID uuid) {
        double attackSpeed = AttributeManager.getGroupAttribute(uuid, "attack_speed");
        double explosionRadius = AttributeManager.getGroupAttribute(uuid, "explosion_radius");
        return Config.FLAME_SPEAR_BASE_CHARGE_RATE.get() * (attackSpeed + explosionRadius - 1.0);
    }
}
