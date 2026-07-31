package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;

/**
 * 守卫阵列仇恨集中管理器
 * <p>
 * 守卫阵列中所有防御无人机的伤害归属集中在圆弧中间的防御无人机上，
 * 避免实体仇恨在边缘防御无人机上导致绕圈打不到的问题。
 */
public class ConstructAggroLockManager {

    private ConstructAggroLockManager() {}

    /**
     * 获取守卫阵列中圆弧中间的防御无人机
     * <p>
     * 守卫阵列的防御无人机按ID排序后取中间索引，
     * 伤害归属集中到此无人机上，使实体仇恨集中在阵列中心而非边缘。
     *
     * @param attacker 发起攻击的防御无人机
     * @return 圆弧中间的防御无人机，如果不在守卫阵列中则返回自身
     */
    public static LivingEntity getGuardArrayAggroProxy(LivingEntity attacker) {
        if (!(attacker instanceof DroneConstructEntity droneAttacker)) {
            return attacker;
        }
        if (!droneAttacker.isGuardArray()) {
            return attacker;
        }

        Entity ownerEntity = droneAttacker.getOwner();
        if (!(ownerEntity instanceof LivingEntity owner)) {
            return attacker;
        }

        // 获取同一玩家的所有守卫阵列防御无人机
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance()
                .getActiveConstructEntities(owner.getUUID(), DroneConstructTypes.DRONE);

        java.util.List<DroneConstructEntity> guardDrones = new java.util.ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity d && d.isAlive() && d.isGuardArray() && d.isDefenseDrone()) {
                guardDrones.add(d);
            }
        }

        if (guardDrones.size() <= 1) {
            return attacker;
        }

        // 按ID排序确保一致性
        guardDrones.sort(java.util.Comparator.comparingInt(Entity::getId));

        // 取中间索引作为仇恨代理
        int centerIndex = guardDrones.size() / 2;
        DroneConstructEntity centerDrone = guardDrones.get(centerIndex);

        if (!centerDrone.isAlive()) {
            // 中心无人机已死亡，回退到自身
            return attacker;
        }

        return centerDrone;
    }

    public static void clearAllData() {
        // 无状态
    }
}
