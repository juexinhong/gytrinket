package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NearDeathExplosionBehavior implements IDroneSpecialBehavior {

    private static final String TAG_EXPLODING = "NDEExploding";
    private static final String TAG_EXPLOSION_TIMER = "NDETimer";
    private static final String TAG_EXPLOSION_SPEED = "NDESpeed";

    @Override
    public String getId() {
        return "near_death_explosion";
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean tryPreventDeath(DroneConstructEntity drone, DamageSource source) {
        if (!hasRequiredItems(drone)) {
            return false;
        }

        if (drone.isExploding()) {
            return false;
        }

        drone.setHealth(1.0f);

        int invincibleDuration = Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get();
        double initialSpeed = Config.NEAR_DEATH_EXPLOSION_INITIAL_SPEED.get();

        InvincibilityMarkerManager.addMarker(drone, invincibleDuration);
        drone.setExploding(true);

        CompoundTag data = drone.getPersistentData();
        data.putBoolean(TAG_EXPLODING, true);
        data.putInt(TAG_EXPLOSION_TIMER, invincibleDuration);
        data.putDouble(TAG_EXPLOSION_SPEED, initialSpeed);

        drone.setExplosionTimer(invincibleDuration);
        drone.setExplosionSpeed(initialSpeed);

        return true;
    }

    @Override
    public void onTick(DroneConstructEntity drone) {
        if (!drone.isExploding()) {
            return;
        }

        Level level = drone.level();
        if (level.isClientSide) {
            return;
        }

        CompoundTag data = drone.getPersistentData();
        int timer = data.getInt(TAG_EXPLOSION_TIMER);
        if (timer <= 0) {
            drone.explodeAndRemove();
            return;
        }

        timer--;
        data.putInt(TAG_EXPLOSION_TIMER, timer);
        drone.setExplosionTimer(timer);

        double speed = data.getDouble(TAG_EXPLOSION_SPEED);
        double acceleration = Config.NEAR_DEATH_EXPLOSION_SPEED_ACCELERATION.get();
        speed += acceleration;
        data.putDouble(TAG_EXPLOSION_SPEED, speed);
        drone.setExplosionSpeed(speed);

        // 转向速度随时间提升：基础转向速度 × (1 + 每秒提升比例 × 已过去秒数)
        int totalDuration = Config.NEAR_DEATH_EXPLOSION_INVINCIBLE_DURATION.get();
        float elapsedSeconds = (totalDuration - timer) / 20.0f;
        float turnSpeed = (float) (Config.NEAR_DEATH_EXPLOSION_TURN_SPEED_BASE.get()
                * (1.0 + Config.NEAR_DEATH_EXPLOSION_TURN_SPEED_GROWTH_PER_SECOND.get() * elapsedSeconds));

        double searchRadius = Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.get();
        LivingEntity target = findNearestDangerousEntity(drone, searchRadius);

        if (target != null) {
            drone.facePositionWithInterpolation(
                    target.position().add(0, target.getEyeHeight() * 0.5, 0), turnSpeed);

            if (drone.distanceTo(target) <= 2.0) {
                drone.explodeAndRemove();
                return;
            }
        } else {
            Entity owner = drone.getOwner();
            if (owner != null && owner.isAlive()) {
                drone.facePositionWithInterpolation(
                        owner.position().add(0, owner.getEyeHeight() * 0.5, 0), turnSpeed);
            }
        }

        Vec3 lookDir = drone.getLookDirection();
        drone.setDeltaMovement(lookDir.scale(speed));

        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = drone.position();
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z, 3,
                    0.1, 0.1, 0.1, 0.02);
        }
    }

    private LivingEntity findNearestDangerousEntity(DroneConstructEntity drone, double searchRadius) {
        Level level = drone.level();
        Vec3 dronePos = drone.position();

        AABB searchBox = new AABB(
                dronePos.x - searchRadius, dronePos.y - searchRadius, dronePos.z - searchRadius,
                dronePos.x + searchRadius, dronePos.y + searchRadius, dronePos.z + searchRadius
        );

        Player player = drone.getOwner() instanceof Player ? (Player) drone.getOwner() : null;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == drone || !entity.isAlive()) return false;
                    if (entity instanceof Player) return false;
                    return HostileTargetManager.shouldAttackPlayer(entity, player);
                });

        if (targets.isEmpty()) {
            return null;
        }

        return targets.stream()
                .min(Comparator.comparingDouble(t -> drone.distanceTo(t)))
                .orElse(null);
    }

    private boolean hasRequiredItems(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) {
            return false;
        }
        MinecraftServer server = drone.level().getServer();
        if (server == null) {
            return false;
        }
        // 检查玩家装备的物品是否声明了「最终指令」特殊机制（数据驱动/覆盖层优先）
        return DefsManager.playerHasEquippedMechanic(server, ownerUUID, "near_death_explosion_items");
    }
}
