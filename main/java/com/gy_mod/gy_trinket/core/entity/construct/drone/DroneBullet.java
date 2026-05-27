package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;

import java.util.UUID;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;




/**
 * 无人机子弹
 * <p>
 * 无人机发射的弹射物实体，对碰撞到的实体造成固定伤害。
 * 使用GeckoLib进行3D模型渲染。
 */
public class DroneBullet extends ThrowableItemProjectile implements GeoEntity {
    /** 子弹基础伤害 */
    public static final float BASE_DAMAGE = 0.3f;

    private static final EntityDataAccessor<Boolean> FROM_DRONE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    public DroneBullet(EntityType<? extends DroneBullet> type, Level level) {
        super(type, level);
    }

    public DroneBullet(Level level, LivingEntity owner) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, BASE_DAMAGE);
    }

    public DroneBullet(Level level, LivingEntity owner, float damage) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, damage);
    }
    
    @Override
    protected Item getDefaultItem() {
        return net.minecraft.world.item.Items.IRON_NUGGET;
    }

    public void setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
    }

    public float getDamage() {
        return this.entityData.get(DAMAGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FROM_DRONE, false);
        entityData.define(DAMAGE, BASE_DAMAGE);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, velocity, inaccuracy);
    }

    private DamageSource createDamageSource() {
        return ModDamageSources.droneBullet(this.level(), this, this.getOwner() instanceof LivingEntity living ? living : null);
    }
    
    private DamageSource createExplosionDamageSource() {
        Entity owner = this.getOwner();
        LivingEntity realOwner = null;
        if (owner instanceof DroneConstructEntity drone) {
            realOwner = drone.getOwner() instanceof LivingEntity living ? living : null;
        }
        return ModDamageSources.droneBullet(this.level(), this, realOwner);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        Entity owner = this.getOwner();

        boolean canAttack = true;

        if (entity == owner) {
            canAttack = false;
        }

        if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {
            canAttack = false;
        }

        if (owner instanceof DroneConstructEntity bulletDrone) {
            UUID bulletOwnerUUID = bulletDrone.getOwnerUUID();

            if (entity instanceof DroneConstructEntity hitDrone) {
                UUID hitDroneOwnerUUID = hitDrone.getOwnerUUID();
                if (hitDroneOwnerUUID != null && hitDroneOwnerUUID.equals(bulletOwnerUUID)) {
                    canAttack = false;
                }
            }

            if (entity instanceof Player hitPlayer) {
                if (bulletOwnerUUID != null && bulletOwnerUUID.equals(hitPlayer.getUUID())) {
                    canAttack = false;
                }
            }
        }

        if (canAttack && entity instanceof LivingEntity target) {
            float damage = getDamage();
            KnockbackManager.markNoKnockback(target.getUUID());
            if (target.getHealth() < damage) {
                target.hurt(createExplosionDamageSource(), damage * 2);
            } else {
                target.hurt(createDamageSource(), damage);
            }

            if (owner instanceof DroneConstructEntity droneShooter && droneShooter.isCommanderDrone()) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.gy_mod.gy_trinket.core.vulnerability.VulnerabilityApplyEvent(
                        "commander", com.gy_mod.gy_trinket.Config.COMMANDER_VULNERABILITY.get().floatValue(), target, true
                    )
                );
            }
        }

        if (!canAttack) {
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.5));
        }

        if (!this.level().isClientSide && canAttack) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    public void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide && (this.isInWater() || this.isInLava())) {
            this.discard();
        }

        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("fromDrone", this.entityData.get(FROM_DRONE));
        tag.putFloat("damage", this.entityData.get(DAMAGE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(FROM_DRONE, tag.getBoolean("fromDrone"));
        this.entityData.set(DAMAGE, tag.getFloat("damage"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }
}
