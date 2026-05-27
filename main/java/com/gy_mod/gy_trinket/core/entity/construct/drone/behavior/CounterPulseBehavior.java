package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CounterPulseBehavior implements IDroneSpecialBehavior {

    private static final Map<UUID, DroneCounterPulseInfo> droneCounterPulseMap = new ConcurrentHashMap<>();

    private static final String TAG_COOLDOWN = "CPCooldown";
    private static final String TAG_CHARGE_LEVEL = "CPChargeLevel";
    private static final String TAG_CHARGE_TICK = "CPChargeTick";

    @Override
    public String getId() {
        return "counter_pulse";
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public void onTick(DroneConstructEntity drone) {
        if (!drone.isDefenseDrone()) return;
        if (drone.level().isClientSide) return;
        if (!hasRequiredItems(drone)) return;

        UUID droneUUID = drone.getUUID();
        DroneCounterPulseInfo info = getOrCreateInfo(drone);

        info.updateCharge();
        saveInfoToPersistentData(drone, info);

        if (info.cooldown > 0) {
            info.cooldown--;
            saveInfoToPersistentData(drone, info);
            return;
        }

        if (hasEnemiesInRange(drone, info)) {
            triggerCounterPulse(drone, info, false);
            saveInfoToPersistentData(drone, info);
        }
    }

    @Override
    public void onDamageTaken(DroneConstructEntity drone, DamageSource source, float amount) {
        if (!drone.isDefenseDrone()) return;
        if (drone.level().isClientSide) return;
        if (!hasRequiredItems(drone)) return;

        Player owner = drone.getOwner() instanceof Player p ? p : null;
        if (shouldIgnoreDamageSource(source, drone, owner)) return;

        DroneCounterPulseInfo info = getOrCreateInfo(drone);
        triggerCounterPulse(drone, info, true);
        saveInfoToPersistentData(drone, info);
    }

    private boolean shouldIgnoreDamageSource(DamageSource source, DroneConstructEntity drone, Player owner) {
        if (source.getEntity() == drone || source.getDirectEntity() == drone) return true;
        if (owner != null && source.getEntity() == owner) return true;
        if (owner != null && source.getEntity() instanceof DroneConstructEntity otherDrone
                && otherDrone.getOwnerUUID() != null && otherDrone.getOwnerUUID().equals(drone.getOwnerUUID())) return true;
        if (owner != null && source.getEntity() instanceof TamableAnimal tame && tame.getOwner() == owner) return true;
        return false;
    }

    private void triggerCounterPulse(DroneConstructEntity drone, DroneCounterPulseInfo info, boolean isDamageTriggered) {
        if (!(drone.level() instanceof ServerLevel serverLevel)) return;

        double explosionRadius = calculateExplosionRadius(info.chargeLevel);
        float explosionDamage = calculateExplosionDamage(info.chargeLevel);

        List<LivingEntity> enemies = getEnemiesInRange(drone, explosionRadius);

        if (enemies.isEmpty()) return;

        Player owner = drone.getOwner() instanceof Player p ? p : null;

        for (LivingEntity enemy : enemies) {
            if (enemy == drone || enemy instanceof Player) continue;

            enemy.invulnerableTime = 0;

            DamageSource explosionSource = owner != null
                    ? drone.damageSources().explosion(null, owner)
                    : drone.damageSources().generic();
            enemy.hurt(explosionSource, explosionDamage);

            enemy.invulnerableTime = 0;
            enemy.setDeltaMovement(0, 0, 0);
        }

        serverLevel.sendParticles(
                ParticleTypes.EXPLOSION,
                drone.getX(), drone.getY(), drone.getZ(),
                1, 0.0, 0.0, 0.0, 0.0
        );

        int particleCount = Math.max(1, (int) (explosionRadius * 4));
        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                drone.getX(), drone.getY(), drone.getZ(),
                particleCount * 2,
                explosionRadius, explosionRadius, explosionRadius,
                0.2
        );

        int edgePoints = Math.max(12, (int) (explosionRadius * 16));
        for (int i = 0; i < edgePoints; i++) {
            double angle = (2.0 * Math.PI * i) / edgePoints;
            double px = drone.getX() + Math.cos(angle) * explosionRadius;
            double pz = drone.getZ() + Math.sin(angle) * explosionRadius;
            serverLevel.sendParticles(
                    ParticleTypes.END_ROD,
                    px, drone.getY(), pz,
                    1, 0.0, 0.0, 0.0, 0.0
            );
        }

        drone.level().playSound(null, drone.getX(), drone.getY(), drone.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);

        info.resetCharge();

        if (!isDamageTriggered) {
            info.cooldown = Config.COUNTER_PULSE_COOLDOWN.get();
        }
    }

    private boolean hasEnemiesInRange(DroneConstructEntity drone, DroneCounterPulseInfo info) {
        double radius = calculateExplosionRadius(info.chargeLevel);
        return !getEnemiesInRange(drone, radius).isEmpty();
    }

    private List<LivingEntity> getEnemiesInRange(DroneConstructEntity drone, double radius) {
        AABB boundingBox = drone.getBoundingBox().inflate(radius);
        Player owner = drone.getOwner() instanceof Player p ? p : null;

        List<LivingEntity> enemies = new ArrayList<>();
        for (LivingEntity entity : drone.level().getEntitiesOfClass(LivingEntity.class, boundingBox)) {
            if (entity == drone || !entity.isAlive() || entity.isInvisible()) continue;
            if (entity instanceof Player) continue;
            if (owner != null && HostileTargetManager.isEntityProtectedByPlayer(entity, owner)) continue;
            if (!HostileTargetManager.shouldAttackPlayer(entity, owner)) continue;
            enemies.add(entity);
        }
        return enemies;
    }

    private double calculateExplosionRadius(int chargeLevel) {
        double baseRadius = Config.COUNTER_PULSE_BASE_EXPLOSION_RADIUS.get();
        return baseRadius * calculateChargeMultiplier(chargeLevel);
    }

    private float calculateExplosionDamage(int chargeLevel) {
        float baseDamage = Config.COUNTER_PULSE_BASE_EXPLOSION_DAMAGE.get().floatValue();
        return baseDamage * (float) calculateChargeMultiplier(chargeLevel);
    }

    private double calculateChargeMultiplier(int chargeLevel) {
        if (chargeLevel <= 0) return 1.0;
        return 1.0 + (Math.log(chargeLevel + 1) * 0.45);
    }

    private DroneCounterPulseInfo getOrCreateInfo(DroneConstructEntity drone) {
        UUID droneUUID = drone.getUUID();
        DroneCounterPulseInfo info = droneCounterPulseMap.get(droneUUID);
        if (info != null) return info;

        CompoundTag data = drone.getPersistentData();
        if (data.contains(TAG_COOLDOWN)) {
            info = new DroneCounterPulseInfo();
            info.cooldown = data.getInt(TAG_COOLDOWN);
            info.chargeLevel = data.getInt(TAG_CHARGE_LEVEL);
            info.chargeTick = data.getInt(TAG_CHARGE_TICK);
        } else {
            info = new DroneCounterPulseInfo();
        }

        droneCounterPulseMap.put(droneUUID, info);
        return info;
    }

    private void saveInfoToPersistentData(DroneConstructEntity drone, DroneCounterPulseInfo info) {
        CompoundTag data = drone.getPersistentData();
        data.putInt(TAG_COOLDOWN, info.cooldown);
        data.putInt(TAG_CHARGE_LEVEL, info.chargeLevel);
        data.putInt(TAG_CHARGE_TICK, info.chargeTick);
    }

    private boolean hasRequiredItems(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return false;
        PlayerStore store = PlayerStoreManager.getPlayerStore(ownerUUID);
        if (store == null) return false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(ownerUUID, stack)) continue;

            if (Config.isCounterPulseItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static class DroneCounterPulseInfo {
        public int cooldown = 0;
        public int chargeLevel = 0;
        public int chargeTick = 0;

        public void updateCharge() {
            chargeTick++;
            int chargeInterval = Config.COUNTER_PULSE_CHARGE_INTERVAL.get();
            int maxCharge = Config.COUNTER_PULSE_MAX_CHARGE_LEVEL.get();
            if (chargeTick >= chargeInterval && chargeLevel < maxCharge) {
                chargeLevel++;
                chargeTick = 0;
            }
        }

        public void resetCharge() {
            chargeLevel = 0;
            chargeTick = 0;
        }
    }
}
