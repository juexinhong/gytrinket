package com.gy_mod.gy_trinket.core.entity.construct.drone;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/**
 * 无人机光束炮实体
 * <p>
 * 列队阵列无人机发射的固定光束炮，不会移动。
 * 光束有固定的宽度和高度，使用线段检测命中目标。
 * 每个光束炮实体只存在10刻，期间可以命中多个目标但每个目标只命中一次。
 */
public class DroneBeamProjectile extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DroneBeamProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(DroneBeamProjectile.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    private static final int BEAM_LIFETIME = 10;
    public static final double BEAM_LENGTH = 15.0D;
    private static final double BEAM_WIDTH = 0.2D;
    private static final double BEAM_HEIGHT = 0.2D;

    private Vec3 startOffset = Vec3.ZERO;
    private UUID ownerUUID;
    private Entity owner;
    private final java.util.Set<UUID> hitTargets = new java.util.HashSet<>();

    public DroneBeamProjectile(EntityType<? extends DroneBeamProjectile> entityType, Level level) {
        super(entityType, level);
        this.setDamage(0.5F);
        this.setLifetime(BEAM_LIFETIME);
    }

    public DroneBeamProjectile(EntityType<? extends DroneBeamProjectile> entityType, LivingEntity shooter, Vec3 startPos, Vec3 direction, float damage, UUID ownerUUID) {
        super(entityType, shooter.level());
        this.setDamage(damage);
        this.setLifetime(BEAM_LIFETIME);
        this.ownerUUID = ownerUUID;
        this.owner = shooter;

        direction = direction.normalize();
        this.setPos(startPos);

        double yawRadians = Math.atan2(-direction.x, direction.z);
        double yaw = Math.toDegrees(yawRadians);
        double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double pitchRadians = -Math.atan2(direction.y, horizontalLength);
        double pitch = Math.toDegrees(pitchRadians);

        this.setYRot((float) yaw);
        this.setXRot((float) pitch);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.startOffset = Vec3.ZERO;

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SHULKER_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DAMAGE, 0.5F);
        entityData.define(LIFETIME, BEAM_LIFETIME);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setDamage(tag.getFloat("Damage"));
        this.setLifetime(tag.getInt("Lifetime"));
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", this.getDamage());
        tag.putInt("Lifetime", this.getLifetime());
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
    }

    @Override
    public void tick() {
        super.tick();

        this.setLifetime(this.getLifetime() - 1);

        if (!this.level().isClientSide) {
            if (this.getLifetime() <= 0) {
                this.discard();
                return;
            }
            this.checkCollision();
        } else {
            this.spawnParticles();
        }
    }

    private void checkCollision() {
        Vec3 lookVec = this.getLookAngle();
        Vec3 start = this.position().add(this.startOffset);
        Vec3 end = start.add(lookVec.scale(BEAM_LENGTH));

        double searchRadius = Math.max(BEAM_WIDTH, BEAM_HEIGHT) + 2.0D;
        AABB largeAABB = new AABB(
                Math.min(start.x, end.x) - searchRadius,
                Math.min(start.y, end.y) - searchRadius,
                Math.min(start.z, end.z) - searchRadius,
                Math.max(start.x, end.x) + searchRadius,
                Math.max(start.y, end.y) + searchRadius,
                Math.max(start.z, end.z) + searchRadius
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, largeAABB);

        for (LivingEntity entity : entities) {
            if (!isEntityHitByBeam(entity, start, end)) {
                continue;
            }
            if (this.canHitEntity(entity)) {
                if (!this.hitTargets.contains(entity.getUUID())) {
                    entity.invulnerableTime = 0;

                    Entity realAttacker = this.getOwner();
                    Player damageOwner = null;

                    if (realAttacker instanceof DroneConstructEntity droneShooter) {
                        Entity ownerEntity = droneShooter.getOwner();
                        if (ownerEntity instanceof Player) {
                            damageOwner = (Player) ownerEntity;
                        }
                        if (damageOwner == null && this.ownerUUID != null) {
                            damageOwner = this.level().getPlayerByUUID(this.ownerUUID);
                        }
                    } else if (this.ownerUUID != null) {
                        damageOwner = this.level().getPlayerByUUID(this.ownerUUID);
                    }

                    float totalDamage = this.getDamage();
                    float projectileDamage = totalDamage * 0.5F;
                    float fireDamage = totalDamage * 0.5F;

                    float targetHealth = entity.getHealth();
                    boolean isFatalHit = targetHealth <= totalDamage;

                    net.minecraft.world.damagesource.DamageSource projectileDamageSource;
                    net.minecraft.world.damagesource.DamageSource fireDamageSource;

                    if (isFatalHit) {
                        if (damageOwner != null) {
                            projectileDamageSource = entity.damageSources().explosion(null, damageOwner);
                            fireDamageSource = entity.damageSources().explosion(null, damageOwner);
                        } else {
                            if (realAttacker instanceof LivingEntity livingAttacker) {
                                projectileDamageSource = entity.damageSources().mobAttack(livingAttacker);
                                fireDamageSource = entity.damageSources().mobAttack(livingAttacker);
                            } else {
                                projectileDamageSource = entity.damageSources().indirectMagic(realAttacker, realAttacker);
                                fireDamageSource = entity.damageSources().indirectMagic(realAttacker, realAttacker);
                            }
                        }
                    } else {
                        if (realAttacker instanceof LivingEntity livingAttacker) {
                            projectileDamageSource = entity.damageSources().mobAttack(livingAttacker);
                            fireDamageSource = entity.damageSources().mobAttack(livingAttacker);
                        } else {
                            projectileDamageSource = entity.damageSources().indirectMagic(realAttacker, realAttacker);
                            fireDamageSource = entity.damageSources().indirectMagic(realAttacker, realAttacker);
                        }
                    }

                    entity.hurt(projectileDamageSource, projectileDamage);
                    entity.invulnerableTime = 0;
                    entity.hurt(fireDamageSource, fireDamage);

                    if (realAttacker instanceof DroneConstructEntity droneShooter && droneShooter.isCommanderDrone()) {
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                            new com.gy_mod.gy_trinket.core.vulnerability.VulnerabilityApplyEvent(
                                "commander", com.gy_mod.gy_trinket.Config.COMMANDER_VULNERABILITY.get().floatValue(), entity, true
                            )
                        );
                    }

                    if (isFatalHit && damageOwner != null) {
                        entity.setLastHurtByMob(damageOwner);
                    }

                    entity.invulnerableTime = 0;
                    entity.setDeltaMovement(0, 0, 0);

                    this.hitTargets.add(entity.getUUID());
                }
            }
        }
    }

    private boolean isEntityHitByBeam(LivingEntity entity, Vec3 beamStart, Vec3 beamEnd) {
        return isHitByRectangularBeam(beamStart, beamEnd, entity.getBoundingBox());
    }

    private boolean isHitByRectangularBeam(Vec3 beamStart, Vec3 beamEnd, AABB entityAABB) {
        Vec3 lookVec = beamEnd.subtract(beamStart).normalize();
        Vec3 right = new Vec3(-lookVec.z, 0.0D, lookVec.x).normalize();
        Vec3 up = lookVec.cross(right).normalize();

        Vec3 halfWidth = right.scale(BEAM_WIDTH / 2.0D);
        Vec3 halfHeight = up.scale(BEAM_HEIGHT / 2.0D);

        Vec3 topLeftStart = beamStart.add(halfHeight).subtract(halfWidth);
        Vec3 topRightStart = beamStart.add(halfHeight).add(halfWidth);
        Vec3 bottomLeftStart = beamStart.subtract(halfHeight).subtract(halfWidth);
        Vec3 bottomRightStart = beamStart.subtract(halfHeight).add(halfWidth);

        Vec3 topLeftEnd = beamEnd.add(halfHeight).subtract(halfWidth);
        Vec3 topRightEnd = beamEnd.add(halfHeight).add(halfWidth);
        Vec3 bottomLeftEnd = beamEnd.subtract(halfHeight).subtract(halfWidth);
        Vec3 bottomRightEnd = beamEnd.subtract(halfHeight).add(halfWidth);

        if (intersectsLineAABB(topLeftStart, topRightStart, entityAABB)) return true;
        if (intersectsLineAABB(topRightStart, topRightEnd, entityAABB)) return true;
        if (intersectsLineAABB(topRightEnd, topLeftEnd, entityAABB)) return true;
        if (intersectsLineAABB(topLeftEnd, topLeftStart, entityAABB)) return true;

        if (intersectsLineAABB(bottomLeftStart, bottomRightStart, entityAABB)) return true;
        if (intersectsLineAABB(bottomRightStart, bottomRightEnd, entityAABB)) return true;
        if (intersectsLineAABB(bottomRightEnd, bottomLeftEnd, entityAABB)) return true;
        if (intersectsLineAABB(bottomLeftEnd, bottomLeftStart, entityAABB)) return true;

        if (intersectsLineAABB(topLeftStart, bottomLeftStart, entityAABB)) return true;
        if (intersectsLineAABB(topLeftEnd, bottomLeftEnd, entityAABB)) return true;

        if (intersectsLineAABB(topRightStart, bottomRightStart, entityAABB)) return true;
        if (intersectsLineAABB(topRightEnd, bottomRightEnd, entityAABB)) return true;

        return isAABBInsideBeam(entityAABB, beamStart, beamEnd, halfWidth, halfHeight, lookVec);
    }

    private boolean isAABBInsideBeam(AABB aabb, Vec3 beamStart, Vec3 beamEnd, Vec3 halfWidth, Vec3 halfHeight, Vec3 lookVec) {
        Vec3 center = aabb.getCenter();
        Vec3 centerToStart = center.subtract(beamStart);
        double widthDistance = Math.abs(centerToStart.dot(halfWidth.normalize()));
        if (widthDistance > halfWidth.length()) {
            return false;
        }

        double heightDistance = Math.abs(centerToStart.dot(halfHeight.normalize()));
        if (heightDistance > halfHeight.length()) {
            return false;
        }

        double lengthDistance = centerToStart.dot(lookVec);
        double beamLength = beamEnd.distanceTo(beamStart);
        return lengthDistance >= 0 && lengthDistance <= beamLength;
    }

    private boolean intersectsLineAABB(Vec3 lineStart, Vec3 lineEnd, AABB aabb) {
        double[] tNear = {Double.NEGATIVE_INFINITY};
        double[] tFar = {Double.POSITIVE_INFINITY};

        if (!clipLineToPlane(lineStart.x, lineEnd.x, aabb.minX, aabb.maxX, 0, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (!clipLineToPlane(lineStart.y, lineEnd.y, aabb.minY, aabb.maxY, 1, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (!clipLineToPlane(lineStart.z, lineEnd.z, aabb.minZ, aabb.maxZ, 2, lineStart, lineEnd, tNear, tFar)) {
            return false;
        }

        if (tNear[0] > tFar[0]) {
            return false;
        }

        return tFar[0] >= 0 && tNear[0] <= 1;
    }

    private boolean clipLineToPlane(double start, double end, double min, double max, int axis,
                                    Vec3 lineStart, Vec3 lineEnd, double[] tNear, double[] tFar) {
        double tMin, tMax;

        if (end - start > 1e-6) {
            tMin = (min - start) / (end - start);
            tMax = (max - start) / (end - start);
        } else if (start - end > 1e-6) {
            tMin = (max - start) / (end - start);
            tMax = (min - start) / (end - start);
        } else {
            if (start < min || start > max) {
                return false;
            }
            return true;
        }

        if (tMin > tNear[0]) tNear[0] = tMin;
        if (tMax < tFar[0]) tFar[0] = tMax;

        return tNear[0] <= tFar[0];
    }

    private boolean canHitEntity(LivingEntity entity) {
        if (entity == this.owner) {
            return false;
        }

        if (entity instanceof Player player && this.ownerUUID != null && player.getUUID().equals(this.ownerUUID)) {
            return false;
        }

        if (entity instanceof DroneConstructEntity droneEntity) {
            if (droneEntity.getOwnerUUID() != null && droneEntity.getOwnerUUID().equals(this.ownerUUID)) {
                return false;
            }
        }

        if (entity instanceof net.minecraft.world.entity.TamableAnimal tamableAnimal) {
            if (tamableAnimal.getOwner() instanceof Player petOwner && petOwner.getUUID().equals(this.ownerUUID)) {
                return false;
            }
        }

        return true;
    }

    public Entity getOwner() {
        return this.owner;
    }

    public UUID getShooterUUID() {
        return this.ownerUUID;
    }

    private void spawnParticles() {
        Vec3 lookVec = this.getLookAngle();
        Vec3 beamStart = this.position().add(this.startOffset);

        double particleStartOffset = 8.0D;
        double particleEndOffset = BEAM_LENGTH;
        double particleRange = particleEndOffset - particleStartOffset;

        int particleCount = 4;
        for (int i = 0; i < particleCount; i++) {
            double progress = (double) i / (particleCount - 1.0D);
            Vec3 particlePos = beamStart.add(lookVec.scale(particleStartOffset + particleRange * progress));

            this.level().addParticle(
                    ParticleTypes.END_ROD,
                    particlePos.x + (this.random.nextDouble() - 0.5D) * BEAM_WIDTH,
                    particlePos.y + (this.random.nextDouble() - 0.5D) * BEAM_HEIGHT,
                    particlePos.z + (this.random.nextDouble() - 0.5D) * BEAM_WIDTH,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    public float getDamage() {
        return this.entityData.get(DAMAGE);
    }

    public void setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
    }

    public int getLifetime() {
        return this.entityData.get(LIFETIME);
    }

    public void setLifetime(int lifetime) {
        this.entityData.set(LIFETIME, lifetime);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<DroneBeamProjectile> event) {
        event.setAndContinue(RawAnimation.begin().thenLoop("fire"));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }
}
