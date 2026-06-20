package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Boid鸟群算法计算器
 * <p>
 * 提供三大核心力：
 * - 分离（Separation）：远离过近的邻居，避免碰撞和死锁
 * - 聚合（Cohesion）：向邻居群中心靠拢，保持编队紧凑
 * - 对齐（Alignment）：匹配邻居的平均速度方向，统一运动方向
 * <p>
 * 每种力返回一个Vec3向量，调用方按权重叠加后应用到无人机运动。
 */
public final class BoidCalculator {

    private BoidCalculator() {}

    /**
     * 计算分离力（带舒适区）
     * <p>
     * 距离 < comfortRange：产生分离力，力度与距离成反比（越近越强）
     * 距离 comfortRange ~ range：舒适区，不产生力（避免抖动）
     * 距离 > range：不产生力
     *
     * @param dronePos      当前无人机位置
     * @param neighbors     邻居无人机位置列表
     * @param comfortRange  舒适区内边界（小于此距离才产生分离力）
     * @param range         分离检测外边界
     * @param strength      分离力强度
     * @return 分离力向量
     */
    public static Vec3 separation(Vec3 dronePos, List<Vec3> neighbors, double comfortRange, double range, double strength) {
        if (neighbors.isEmpty()) return Vec3.ZERO;

        Vec3 separationForce = Vec3.ZERO;
        int count = 0;

        for (Vec3 neighborPos : neighbors) {
            Vec3 diff = dronePos.subtract(neighborPos);
            double dist = diff.length();
            if (dist < 0.001) {
                // 完全重叠，使用随机方向打破对称
                diff = new Vec3(
                    Math.random() - 0.5,
                    0,
                    Math.random() - 0.5
                ).normalize().scale(comfortRange * 0.5);
                dist = diff.length();
            }
            if (dist < comfortRange) {
                // 距离小于舒适区：产生分离力，力度与距离成反比
                double weight = 1.0 - (dist / comfortRange);
                separationForce = separationForce.add(diff.normalize().scale(weight));
                count++;
            }
            // comfortRange <= dist <= range：舒适区，不产生力
        }

        if (count == 0) return Vec3.ZERO;

        separationForce = separationForce.scale(1.0 / count);
        double magnitude = separationForce.length();
        if (magnitude > 0.001) {
            separationForce = separationForce.normalize().scale(strength);
        }

        return separationForce;
    }

    /**
     * 计算分离力（无舒适区，兼容旧调用）
     */
    public static Vec3 separation(Vec3 dronePos, List<Vec3> neighbors, double range, double strength) {
        return separation(dronePos, neighbors, range, range, strength);
    }

    /**
     * 计算聚合力（带舒适区）
     * <p>
     * 距离 < comfortRange：不产生聚合力（避免与分离力冲突导致振荡）
     * 距离 comfortRange ~ range：产生聚合力，向群中心靠拢
     * 距离 > range：不产生聚合力
     *
     * @param dronePos      当前无人机位置
     * @param neighbors     邻居无人机位置列表
     * @param comfortRange  舒适区内边界（小于此距离不产生聚合力）
     * @param range         聚合检测外边界
     * @param strength      聚合力强度
     * @return 聚合力向量
     */
    public static Vec3 cohesion(Vec3 dronePos, List<Vec3> neighbors, double comfortRange, double range, double strength) {
        if (neighbors.isEmpty()) return Vec3.ZERO;

        Vec3 center = Vec3.ZERO;
        int count = 0;

        for (Vec3 neighborPos : neighbors) {
            double dist = dronePos.distanceTo(neighborPos);
            if (dist >= comfortRange && dist < range) {
                center = center.add(neighborPos);
                count++;
            }
        }

        if (count == 0) return Vec3.ZERO;

        center = center.scale(1.0 / count);
        Vec3 toCenter = center.subtract(dronePos);

        double dist = toCenter.length();
        if (dist < 0.001) return Vec3.ZERO;

        return toCenter.normalize().scale(strength);
    }

    /**
     * 计算聚合力（无舒适区，兼容旧调用）
     */
    public static Vec3 cohesion(Vec3 dronePos, List<Vec3> neighbors, double range, double strength) {
        return cohesion(dronePos, neighbors, 0, range, strength);
    }

    /**
     * 计算对齐力
     * <p>
     * 产生一个匹配邻居平均运动方向的力，使无人机与群体运动方向一致。
     *
     * @param droneVelocity  当前无人机速度
     * @param neighborVelocities 邻居无人机速度列表
     * @param range          对齐检测范围（这里用距离过滤，由调用方预过滤）
     * @param strength       对齐力强度
     * @return 对齐力向量
     */
    public static Vec3 alignment(Vec3 droneVelocity, List<Vec3> neighborVelocities, double range, double strength) {
        if (neighborVelocities.isEmpty()) return Vec3.ZERO;

        Vec3 avgVelocity = Vec3.ZERO;
        int count = 0;

        for (Vec3 vel : neighborVelocities) {
            avgVelocity = avgVelocity.add(vel);
            count++;
        }

        if (count == 0) return Vec3.ZERO;

        avgVelocity = avgVelocity.scale(1.0 / count);
        Vec3 steering = avgVelocity.subtract(droneVelocity);

        double magnitude = steering.length();
        if (magnitude > strength) {
            steering = steering.normalize().scale(strength);
        }

        return steering;
    }

    /**
     * 计算目标吸引力
     * <p>
     * 产生一个指向目标位置的力，力度与距离成正比（越远越强，有上限）。
     *
     * @param dronePos   当前无人机位置
     * @param targetPos  目标位置
     * @param strength   吸引力强度
     * @param maxForce   最大力上限
     * @return 目标吸引力向量
     */
    public static Vec3 seek(Vec3 dronePos, Vec3 targetPos, double strength, double maxForce) {
        Vec3 toTarget = targetPos.subtract(dronePos);
        double dist = toTarget.length();
        if (dist < 0.001) return Vec3.ZERO;

        Vec3 desired = toTarget.normalize().scale(strength);
        double magnitude = desired.length();
        if (magnitude > maxForce) {
            desired = desired.normalize().scale(maxForce);
        }

        return desired;
    }

    /**
     * 计算到达力（Arrive）
     * <p>
     * 与seek类似，但在接近目标时自动减速，避免过冲震荡。
     *
     * @param dronePos    当前无人机位置
     * @param targetPos   目标位置
     * @param maxSpeed    最大速度
     * @param slowRadius  开始减速的半径
     * @return 到达力向量
     */
    public static Vec3 arrive(Vec3 dronePos, Vec3 targetPos, double maxSpeed, double slowRadius) {
        Vec3 toTarget = targetPos.subtract(dronePos);
        double dist = toTarget.length();
        if (dist < 0.001) return Vec3.ZERO;

        double speed = maxSpeed;
        if (dist < slowRadius) {
            speed = maxSpeed * (dist / slowRadius);
        }

        return toTarget.normalize().scale(speed);
    }
}
