package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 环绕行为
 * <p>
 * 基础环绕行为，匹配标签：array + orbit
 */
public class OrbitBehavior implements IDroneBehavior {
    /** 环绕半径（格） */
    private static final float ORBIT_RADIUS = 3.0f;

    /** 垂直偏移（格，玩家上方） */
    private static final float VERTICAL_OFFSET = 1.7f;

    /** 角速度（弧度/秒） */
    private static final float ANGULAR_VELOCITY = 0.2f;

    /** 移动速度（格/秒） */
    private static final float MOVE_SPEED = 10.0f;

    /** 搜索范围（格） */
    private static final float SEARCH_RANGE = 8.0f;

    private static float getConfigAttackRange() { return Config.ORBIT_ATTACK_RANGE.get().floatValue(); }
    private static float getConfigAttackInterval() { return Config.ORBIT_ATTACK_INTERVAL.get().floatValue(); }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of(DroneArrayType.Tags.ARRAY, DroneArrayType.Tags.ORBIT);
    }

    @Override
    public Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime) {
        // 直接从 ConstructManager 获取玩家的所有无人机
        Map<UUID, net.minecraft.world.entity.Entity> entitiesMap = 
            ConstructManager.getInstance().getActiveConstructEntities(owner.getUUID(), DroneConstructTypes.DRONE);
        
        List<DroneConstructEntity> allDrones = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity droneEntity &&
                droneEntity.isAlive() &&
                droneEntity.isOrbitArray()) {
                allDrones.add(droneEntity);
            }
        }
        
        // 按ID排序（越老越先创建，索引越小），确保索引稳定
        allDrones.sort(Comparator.comparingInt(Entity::getId));
        
        int totalDrones = allDrones.size();
        int droneIndex = allDrones.indexOf(drone);
        if (droneIndex < 0) {
            droneIndex = 0;
        }

        // 使用游戏时间计算旋转角度
        double gameTime = owner.level().getGameTime() / 20.0;
        double rotationAngle = gameTime * ANGULAR_VELOCITY * Math.PI * 2;

        // 计算每个无人机的初始角度，确保均匀分布
        // 公式：2π * (droneIndex / totalDrones)
        double initialAngle = Math.PI * 2.0D * droneIndex / Math.max(totalDrones, 1);
        
        // 最终角度 = 初始角度 + 旋转角度
        double finalAngle = initialAngle + rotationAngle;

        // 计算目标位置
        float verticalOffset = VERTICAL_OFFSET;
        if (drone instanceof DroneConstructEntity droneEntity && droneEntity.isDefenseDrone()) {
            verticalOffset = 0.0f;
        }

        Vec3 targetPos = new Vec3(
            owner.getX() + ORBIT_RADIUS * Math.cos(finalAngle),
            owner.getY() + verticalOffset,
            owner.getZ() + ORBIT_RADIUS * Math.sin(finalAngle)
        );

        // 无人机自己移动到目标位置（不是直接传送）
        Vec3 dronePos = drone.position();
        Vec3 direction = targetPos.subtract(dronePos);
        double distance = direction.length();
        
        if (distance > 0.1) {
            direction = direction.normalize();
            float moveDistance = (float)(MOVE_SPEED * deltaTime);
            if (moveDistance > distance) {
                moveDistance = (float) distance;
            }
            Vec3 moveVector = direction.scale(moveDistance);
            drone.setDeltaMovement(moveVector);
        } else {
            drone.setDeltaMovement(Vec3.ZERO);
        }

        return Vec3.ZERO;
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        Level level = drone.level();
        
        // 使用无人机的位置作为搜索中心
        Vec3 dronePos = drone.position();
        AABB searchBox = new AABB(
            dronePos.x - SEARCH_RANGE,
            dronePos.y - SEARCH_RANGE,
            dronePos.z - SEARCH_RANGE,
            dronePos.x + SEARCH_RANGE,
            dronePos.y + SEARCH_RANGE,
            dronePos.z + SEARCH_RANGE
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner || entity == drone) return false;
                    if (!entity.isAlive()) return false;
                    // 只攻击敌对生物
                    if (entity.getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) return false;
                    // 不攻击铁傀儡等友方实体
                    if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
                    // 不攻击玩家
                    if (entity instanceof net.minecraft.world.entity.player.Player) return false;
                    // 不检查视线（视线检查放在攻击时）
                    return true;
                });

        List<LivingEntity> sortedEntities = entities.stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(drone)))
                .collect(Collectors.toList());
        
        return sortedEntities;
    }

    @Override
    public void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
        if (!canAttack) {
            return;
        }

        Level level = drone.level();
        if (level.isClientSide) {
            return;
        }

        // 先检查攻击冷却
        if (drone instanceof DroneConstructEntity droneEntity && droneEntity.getAttackCooldown() > 0) {
            return;
        }

        double distance = drone.distanceTo(target);

        if (distance > getConfigAttackRange()) {
            return;
        }

        // 检测视线（朝向逻辑已迁移到 DroneConstructEntity）
        boolean hasLineOfSight = drone instanceof LivingEntity livingDrone && livingDrone.hasLineOfSight(target);

        if (!hasLineOfSight) {
            return;
        }

        // 发射子弹
        fireBullet(drone, owner, target);
    }

    private void fireBullet(Entity drone, LivingEntity owner, LivingEntity target) {
        if (drone.level().isClientSide) {
            return;
        }

        Vec3 dronePos = drone.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);
        
        Vec3 direction = targetPos.subtract(dronePos).normalize();

        float damage = DroneBullet.getBaseDamage();
        float cooldown = getConfigAttackInterval() * 20.0f;
        
        if (drone instanceof DroneConstructEntity droneEntity) {
            damage = (float) droneEntity.getAttributeValue(Attributes.ATTACK_DAMAGE);
            cooldown /= (float) droneEntity.getAttackSpeedMultiplier();
            droneEntity.setAttackCooldown((int) cooldown);

            DroneBullet bullet = new DroneBullet(drone.level(), droneEntity, damage);
            bullet.setPos(dronePos.x, dronePos.y + 0.4, dronePos.z);
            bullet.shoot(direction.x, direction.y, direction.z, 1.3f, 0.0f);
            drone.level().addFreshEntity(bullet);
        }
    }

    @Override
    public float getAttackInterval() {
        return getConfigAttackInterval();
    }

    @Override
    public float getAttackRange() {
        return getConfigAttackRange();
    }

    @Override
    public boolean isCombatMode() {
        return false;
    }
}