package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Boid 集群力通用助手
 * <p>
 * 抽取僚机/蜂群中重复的"邻居数据收集"与"集群力合成"逻辑。
 * 调用方提供自身实体、归属者、邻居实体类、Boid 参数即可获得合成后的力向量。
 */
public final class BoidHelper {

    /** 邻居扫描半径（格） */
    private static final double NEIGHBOR_SCAN_RANGE = 8.0;

    private BoidHelper() {}

    /**
     * 收集同一玩家的同类构造体邻居的位置和速度。
     *
     * @param self               当前实体
     * @param owner              归属者
     * @param neighborClass      邻居实体类（如 WingmanConstructEntity.class）
     * @param neighborPositions  输出：邻居位置列表
     * @param neighborVelocities 输出：邻居速度列表
     */
    public static <T extends AbstractConstructEntity> void collectNeighborData(
            Entity self, LivingEntity owner, Class<T> neighborClass,
            List<Vec3> neighborPositions, List<Vec3> neighborVelocities) {
        Level level = self.level();
        Vec3 pos = self.position();
        List<T> nearby = level.getEntitiesOfClass(
            neighborClass,
            new AABB(pos.x - NEIGHBOR_SCAN_RANGE, pos.y - NEIGHBOR_SCAN_RANGE, pos.z - NEIGHBOR_SCAN_RANGE,
                     pos.x + NEIGHBOR_SCAN_RANGE, pos.y + NEIGHBOR_SCAN_RANGE, pos.z + NEIGHBOR_SCAN_RANGE),
            other -> other != self && other.isAlive()
                     && other.getOwnerUUID() != null && other.getOwnerUUID().equals(owner.getUUID())
        );

        for (T other : nearby) {
            neighborPositions.add(other.position());
            neighborVelocities.add(other.getDeltaMovement());
        }
    }

    /**
     * 计算合成 Boid 集群力（分离 + 聚合 + 对齐）。
     *
     * @param self          当前实体
     * @param owner         归属者
     * @param neighborClass 邻居实体类
     * @param config        Boid 参数
     * @return 合成后的力向量
     */
    public static <T extends AbstractConstructEntity> Vec3 calculateBoidForce(
            Entity self, LivingEntity owner, Class<T> neighborClass, BoidConfig config) {
        List<Vec3> neighborPositions = new ArrayList<>();
        List<Vec3> neighborVelocities = new ArrayList<>();
        collectNeighborData(self, owner, neighborClass, neighborPositions, neighborVelocities);

        Vec3 pos = self.position();
        Vec3 velocity = self.getDeltaMovement();

        Vec3 separation = BoidCalculator.separation(pos, neighborPositions,
                config.getComfortRange(), config.getSeparationRange(), config.getSeparationStrength());
        Vec3 cohesion = BoidCalculator.cohesion(pos, neighborPositions,
                config.getComfortRange(), config.getCohesionRange(), config.getCohesionStrength());
        Vec3 alignment = BoidCalculator.alignment(velocity, neighborVelocities,
                config.getAlignmentRange(), config.getAlignmentStrength());

        return separation.add(cohesion).add(alignment);
    }
}
