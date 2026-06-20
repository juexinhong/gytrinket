package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneArrayType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 待机行为
 * <p>
 * 无人机跟随玩家，距离超过6格时才触发跟随。
 * 超出距离越远，速度越快（每超出1格提升30%）。
 */
public class StandbyBehavior implements IDroneBehavior {
    /** 跟随触发距离（格） */
    private static final float FOLLOW_DISTANCE = 6.0f;

    /** 跟随速度（格/秒） */
    private static final float FOLLOW_SPEED = 4.0f;

    /** 每超出1格的速度提升比例 */
    private static final float SPEED_INCREASE_PER_BLOCK = 0.3f;

    /** 跟随目标偏移高度（格） */
    private static final float FOLLOW_Y_OFFSET = 1.5f;

    @Override
    public Set<String> getRequiredTags() {
        return Set.of(DroneArrayType.Tags.ARRAY, DroneArrayType.Tags.STANDBY);
    }

    @Override
    public Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime) {
        Level level = drone.level();
        if (level.isClientSide) {
            return drone.position();
        }

        Vec3 dronePos = drone.position();
        Vec3 ownerPos = owner.position().add(0, FOLLOW_Y_OFFSET, 0);

        double distance = dronePos.distanceTo(ownerPos);

        if (distance > FOLLOW_DISTANCE) {
            double excessDistance = distance - FOLLOW_DISTANCE;
            double speedMultiplier = 1.0 + (excessDistance * SPEED_INCREASE_PER_BLOCK);
            float actualSpeed = (float)(FOLLOW_SPEED * speedMultiplier);

            float yaw = drone.getYRot() * (float)Math.PI / 180.0f;
            Vec3 direction = new Vec3(
                -Math.sin(yaw),
                0,
                Math.cos(yaw)
            ).normalize();

            Vec3 newPos = dronePos.add(direction.scale(actualSpeed * deltaTime));

            double yDiff = ownerPos.y - dronePos.y;
            double yExcess = Math.abs(yDiff) - 1.0;
            if (yExcess > 0) {
                double ySpeedMultiplier = 1.0 + (yExcess * SPEED_INCREASE_PER_BLOCK);
                double ySpeed = FOLLOW_SPEED * ySpeedMultiplier * Math.signum(yDiff);
                newPos = new Vec3(newPos.x, dronePos.y + ySpeed * deltaTime, newPos.z);
            }

            drone.setPos(newPos.x, newPos.y, newPos.z);
        }

        return drone.position();
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        return Collections.emptyList();
    }

    @Override
    public void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
    }

    @Override
    public float getAttackInterval() {
        return 0;
    }

    @Override
    public float getAttackRange() {
        return 0;
    }

    @Override
    public boolean isCombatMode() {
        return false;
    }
}
