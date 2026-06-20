package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 护盾待机粒子特效
 * <p>
 * 当玩家拥有护盾时，每隔60刻触发一次待机粒子效果：
 * 1. 在玩家脚底生成六边形护盾粒子（从中心向外扩展，与伤害系统无方向粒子一致）
 * 2. 20刻后在玩家头顶生成六边形护盾粒子（从外向中心收缩）
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldIdleParticleEvent {

    // 待机粒子触发间隔（刻）
    private static final int IDLE_INTERVAL_TICKS = 60;
    // 脚底粒子产生后，头顶粒子的延迟（刻）
    private static final int HEAD_PARTICLE_DELAY_TICKS = 20;
    // 粒子距离原点的半径
    private static final double RADIUS = 1.0;
    // 每个玩家的tick计数器，用于控制触发间隔
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();
    // 上方粒子延迟计数器，到期时以最新玩家位置生成
    private static final Map<UUID, Integer> upperParticleCounters = new HashMap<>();

    private ShieldIdleParticleEvent() {}

    /**
     * 重置指定玩家的待机粒子计时器和上方粒子延迟，
     * 伤害系统触发护盾粒子时调用，避免两者同时出现
     */
    public static void resetIdleTimer(UUID playerUUID) {
        tickCounters.remove(playerUUID);
        upperParticleCounters.remove(playerUUID);
    }

    /**
     * 立即触发一次完整的待机粒子流程（侧下方+延迟后侧上方），
     * 供伤害系统无方向伤害时调用
     */
    public static void triggerIdleParticles(ServerPlayer player) {
        if (!Config.SHIELD_IDLE_PARTICLE_ENABLED.get()) {
            return;
        }
        resetIdleTimer(player.getUUID());
        generateIdleParticles(player);
    }

    /**
     * 服务端每tick触发，检查是否到达待机粒子触发间隔
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.SHIELD_IDLE_PARTICLE_ENABLED.get()) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUUID = player.getUUID();
            double currentShield = ShieldManager.getCurrentShield(playerUUID);
            // 护盾值<=0时不生成粒子，并清除计数器
            if (currentShield <= 0) {
                tickCounters.remove(playerUUID);
                upperParticleCounters.remove(playerUUID);
                continue;
            }

            // 处理上方粒子延迟：到期时以最新玩家位置生成
            Integer upperTicks = upperParticleCounters.get(playerUUID);
            if (upperTicks != null) {
                if (upperTicks <= 1) {
                    upperParticleCounters.remove(playerUUID);
                    generateUpperParticles(player);
                } else {
                    upperParticleCounters.put(playerUUID, upperTicks - 1);
                }
            }

            int ticks = tickCounters.getOrDefault(playerUUID, 0) + 1;
            if (ticks >= IDLE_INTERVAL_TICKS) {
                ticks = 0;
                generateIdleParticles(player);
            }
            tickCounters.put(playerUUID, ticks);
        }
    }

    /**
     * 玩家退出时清理tick计数器，避免内存泄漏
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            UUID uuid = event.getEntity().getUUID();
            tickCounters.remove(uuid);
            upperParticleCounters.remove(uuid);
        }
    }

    /**
     * 生成待机粒子：
     * 1. 立即生成下方粒子（中心向外扩展）
     * 2. 注册上方粒子延迟计数器，20刻后以最新玩家位置生成
     */
    private static void generateIdleParticles(ServerPlayer player) {
        double originX = player.getX();
        double originY = player.getY() + player.getBbHeight() / 2.0;
        double originZ = player.getZ();

        float yawRad = (float) Math.toRadians(player.getYRot());
        double facingX = -Math.sin(yawRad);
        double facingZ = Math.cos(yawRad);

        double sideX = facingZ;
        double sideZ = -facingX;

        double cos45 = Math.cos(Math.PI / 4);
        double sin45 = Math.sin(Math.PI / 4);

        double lowerDirX = sideX * cos45;
        double lowerDirY = -sin45;
        double lowerDirZ = sideZ * cos45;

        // 下方立即发射（中心向外扩展）
        generateHexParticles(player, originX, originY, originZ, lowerDirX, lowerDirY, lowerDirZ, false, 0);

        // 上方粒子延迟20刻后以最新位置生成
        upperParticleCounters.put(player.getUUID(), HEAD_PARTICLE_DELAY_TICKS);
    }

    /**
     * 生成上方粒子（延迟到期后调用，使用最新玩家位置）
     */
    private static void generateUpperParticles(ServerPlayer player) {
        double originX = player.getX();
        double originY = player.getY() + player.getBbHeight() / 2.0;
        double originZ = player.getZ();

        float yawRad = (float) Math.toRadians(player.getYRot());
        double facingX = -Math.sin(yawRad);
        double facingZ = Math.cos(yawRad);

        double sideX = facingZ;
        double sideZ = -facingX;

        double cos45 = Math.cos(Math.PI / 4);
        double sin45 = Math.sin(Math.PI / 4);

        double upperDirX = -sideX * cos45;
        double upperDirY = sin45;
        double upperDirZ = -sideZ * cos45;

        // 上方发射（外向中心收缩）
        generateHexParticles(player, originX, originY, originZ, upperDirX, upperDirY, upperDirZ, true, 0);
    }

    /**
     * 生成六边形护盾粒子
     * <p>
     * 算法与 ShieldParticleHandler.generateShieldParticles() 一致：
     * - 计算方向向量的法线和切线，构建正六边形3圈粒子
     * - 每圈6个顶点，间隔60度，相邻圈偏移30度
     * - 第3圈有0.86的收缩因子
     * <p>
     * 当 reverseOrder=false 时，粒子从内圈到外圈依次生成（中心向外扩展）；
     * 当 reverseOrder=true 时，粒子从外圈到内圈依次生成（外向中心收缩）。
     *
     * @param player       目标玩家
     * @param originX      粒子原点X
     * @param originY      粒子原点Y
     * @param originZ      粒子原点Z
     * @param dirX         方向向量X
     * @param dirY         方向向量Y
     * @param dirZ         方向向量Z
     * @param reverseOrder 是否反向生成（外→中心）
     * @param baseDelayTicks  基础延迟（游戏刻）
     */
    private static void generateHexParticles(ServerPlayer player,
                                              double originX, double originY, double originZ,
                                              double dirX, double dirY, double dirZ,
                                              boolean reverseOrder, int baseDelayTicks) {
        // 正序时中心粒子最先出现（baseDelayTicks），反序时中心粒子最后出现（延迟最大）
        double particleX = originX + dirX * RADIUS;
        double particleY = originY + dirY * RADIUS;
        double particleZ = originZ + dirZ * RADIUS;

        if (!reverseOrder) {
            NetworkHandler.sendShieldParticleToPlayer(player, player, particleX, particleY, particleZ,
                    dirX, dirY, dirZ, originX, originY, originZ, baseDelayTicks);
        }

        // 计算与方向向量垂直的法线向量，用于构建六边形平面
        double[] normal = new double[3];
        if (Math.abs(dirY) < 0.999) {
            normal[0] = -dirZ;
            normal[1] = 0;
            normal[2] = dirX;
        } else {
            // 方向接近垂直时，使用特殊处理避免退化
            normal[0] = 0;
            normal[1] = dirZ;
            normal[2] = -dirY;
        }

        // 归一化法线向量
        double normalLength = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        if (normalLength > 0) {
            normal[0] /= normalLength;
            normal[1] /= normalLength;
            normal[2] /= normalLength;
        }

        // 计算切线向量 = 方向向量 × 法线向量
        double[] tangent = new double[3];
        tangent[0] = dirY * normal[2] - dirZ * normal[1];
        tangent[1] = dirZ * normal[0] - dirX * normal[2];
        tangent[2] = dirX * normal[1] - dirY * normal[0];

        // 归一化切线向量
        double tangentLength = Math.sqrt(tangent[0] * tangent[0] + tangent[1] * tangent[1] + tangent[2] * tangent[2]);
        if (tangentLength > 0) {
            tangent[0] /= tangentLength;
            tangent[1] /= tangentLength;
            tangent[2] /= tangentLength;
        }

        // 正六边形6个顶点角度
        double[] hexagonAngles = {0, Math.PI / 3, 2 * Math.PI / 3, Math.PI, 4 * Math.PI / 3, 5 * Math.PI / 3};

        // 根据是否反向决定圈的生成顺序
        // 正序：0→1→2（内→外，中心向外扩展）
        // 反序：2→1→0（外→内，外向中心收缩）
        int[] circleOrder = reverseOrder ? new int[]{2, 1, 0} : new int[]{0, 1, 2};

        for (int idx = 0; idx < circleOrder.length; idx++) {
            int circle = circleOrder[idx];
            // 每圈的间隔半径，随圈数递减（0.29 * 0.92^circle）
            double localCurrentInterval = 0.29 * Math.pow(0.92, circle);
            // 当前圈的实际半径 = 间隔 × (圈数+1)
            double localCurrentRadius = localCurrentInterval * (circle + 1);
            // 每圈偏移30度，使六边形交错排列
            double offsetAngle = circle * Math.PI / 6;

            // 计算延迟：中心0，一圈2，二圈和三圈4
            int delayTicks;
            if (reverseOrder) {
                delayTicks = idx == 0 ? 2 : 4;
            } else {
                delayTicks = circle == 0 ? 2 : 4;
            }

            for (double hexAngle : hexagonAngles) {
                double angle = hexAngle + offsetAngle;
                // 第3圈收缩因子0.86
                double shrinkFactor = 1.0;
                if (circle == 2) {
                    shrinkFactor = 0.86;
                }

                // 计算粒子点方向 = 主方向 + 半径偏移 × (法线·cos + 切线·sin)
                double[] pointDir = new double[3];
                pointDir[0] = dirX + localCurrentRadius * shrinkFactor * (normal[0] * Math.cos(angle) + tangent[0] * Math.sin(angle));
                pointDir[1] = dirY + localCurrentRadius * shrinkFactor * (normal[1] * Math.cos(angle) + tangent[1] * Math.sin(angle));
                pointDir[2] = dirZ + localCurrentRadius * shrinkFactor * (normal[2] * Math.cos(angle) + tangent[2] * Math.sin(angle));

                // 归一化点方向
                double pointDirLength = Math.sqrt(pointDir[0] * pointDir[0] + pointDir[1] * pointDir[1] + pointDir[2] * pointDir[2]);
                if (pointDirLength > 0) {
                    pointDir[0] /= pointDirLength;
                    pointDir[1] /= pointDirLength;
                    pointDir[2] /= pointDirLength;
                }

                // 计算粒子世界坐标 = 原点 + 方向 × 半径
                double pointX = originX + pointDir[0] * RADIUS;
                double pointY = originY + pointDir[1] * RADIUS;
                double pointZ = originZ + pointDir[2] * RADIUS;

                NetworkHandler.sendShieldParticleToPlayer(player, player, pointX, pointY, pointZ,
                        pointDir[0], pointDir[1], pointDir[2], originX, originY, originZ, baseDelayTicks + delayTicks);
            }
        }

        // 反序时中心粒子最后出现（3圈后，延迟6tick）
        if (reverseOrder) {
            NetworkHandler.sendShieldParticleToPlayer(player, player, particleX, particleY, particleZ,
                    dirX, dirY, dirZ, originX, originY, originZ, baseDelayTicks + 6);
        }
    }
}
