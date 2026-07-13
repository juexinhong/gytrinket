package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Boid鸟群算法计算器
 * <p>
 * 提供三大核心力：
 * - 分离（Separation）：远离过近的邻居，避免碰撞和死锁
 * - 聚合（Cohesion）：向邻居群中心靠拢，保持编队紧凑
 * - 对齐（Alignment）：匹配邻居的平均速度方向，统一运动方向
 */
public final class BoidCalculator {

    private BoidCalculator() {}

    public static Vec3 separation(Vec3 dronePos, List<Vec3> neighbors, double comfortRange, double range, double strength) {
        if (neighbors.isEmpty()) return Vec3.ZERO;

        Vec3 separationForce = Vec3.ZERO;
        int count = 0;

        for (Vec3 neighborPos : neighbors) {
            Vec3 diff = dronePos.subtract(neighborPos);
            double dist = diff.length();
            if (dist < 0.001) {
                diff = new Vec3(
                    Math.random() - 0.5,
                    0,
                    Math.random() - 0.5
                ).normalize().scale(comfortRange * 0.5);
                dist = diff.length();
            }
            if (dist < comfortRange) {
                double weight = 1.0 - (dist / comfortRange);
                separationForce = separationForce.add(diff.normalize().scale(weight));
                count++;
            }
        }

        if (count == 0) return Vec3.ZERO;

        separationForce = separationForce.scale(1.0 / count);
        double magnitude = separationForce.length();
        if (magnitude > 0.001) {
            separationForce = separationForce.normalize().scale(strength);
        }

        return separationForce;
    }

    public static Vec3 separation(Vec3 dronePos, List<Vec3> neighbors, double range, double strength) {
        return separation(dronePos, neighbors, range, range, strength);
    }

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

    public static Vec3 cohesion(Vec3 dronePos, List<Vec3> neighbors, double range, double strength) {
        return cohesion(dronePos, neighbors, 0, range, strength);
    }

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
