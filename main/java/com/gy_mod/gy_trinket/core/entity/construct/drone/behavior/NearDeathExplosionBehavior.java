package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.damage.InvincibilityMarkerManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.HostileTargetManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private static final String TAG_PENDING_EXPLODE_X = "NDEPendingX";
    private static final String TAG_PENDING_EXPLODE_Y = "NDEPendingY";
    private static final String TAG_PENDING_EXPLODE_Z = "NDEPendingZ";

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

        // 上一刻已预测到本刻命中：先移动到预计算的相交位置，再自爆
        if (data.contains(TAG_PENDING_EXPLODE_X)) {
            drone.setPos(data.getDouble(TAG_PENDING_EXPLODE_X),
                    data.getDouble(TAG_PENDING_EXPLODE_Y),
                    data.getDouble(TAG_PENDING_EXPLODE_Z));
            data.remove(TAG_PENDING_EXPLODE_X);
            data.remove(TAG_PENDING_EXPLODE_Y);
            data.remove(TAG_PENDING_EXPLODE_Z);
            drone.explodeAndRemove();
            return;
        }

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

        double searchRadius = Config.NEAR_DEATH_EXPLOSION_SEARCH_RADIUS.get();
        LivingEntity target = findNearestDangerousEntity(drone, searchRadius);

        if (target != null) {
            drone.facePositionWithInterpolation(
                    target.position().add(0, target.getEyeHeight() * 0.5, 0), 20.0f);
        } else {
            Entity owner = drone.getOwner();
            if (owner != null && owner.isAlive()) {
                drone.facePositionWithInterpolation(
                        owner.position().add(0, owner.getEyeHeight() * 0.5, 0), 20.0f);
            }
        }

        // 本刻运动距离（按转向后的新方向计算）
        Vec3 movement = drone.getLookDirection().scale(speed);
        drone.setDeltaMovement(movement);

        if (target != null) {
            // 射线判定（取代原距离≤2格范围判定）：
            // 本刻运动扫掠从本刻碰撞箱沿运动距离延伸（天然覆盖本刻重叠情况），
            // 与目标碰撞箱相交 → 下一刻在相交位置（本刻预计算）自爆
            AABB sweepBox = drone.getBoundingBox().expandTowards(movement);
            if (sweepBox.intersects(target.getBoundingBox())) {
                Vec3 hitPos = drone.position().add(movement);
                data.putDouble(TAG_PENDING_EXPLODE_X, hitPos.x);
                data.putDouble(TAG_PENDING_EXPLODE_Y, hitPos.y);
                data.putDouble(TAG_PENDING_EXPLODE_Z, hitPos.z);
                return;
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = drone.position();
            // 只保留火焰粒子，数量消减30%（3 → 2）
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z, 2,
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
        return DefsManager.playerHasEquippedMechanic(server, ownerUUID, "near_death_explosion_items");
    }
}

