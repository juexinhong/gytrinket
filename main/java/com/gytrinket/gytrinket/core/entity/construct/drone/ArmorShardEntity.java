package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class ArmorShardEntity extends Entity implements GeoEntity {

    private UUID ownerUUID;
    private float damageReductionBonus;
    private int lifetime;

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    private static final int MAX_LIFETIME = 200;
    private static final double ATTRACT_RANGE = 15.0;
    private static final double COLLECT_RANGE = 0.4;
    private static final double MOVE_SPEED = 0.02;

    public ArmorShardEntity(EntityType<? extends ArmorShardEntity> entityType, Level level) {
        super(entityType, level);
        this.lifetime = 0;
    }

    public ArmorShardEntity(Level level, double x, double y, double z, UUID ownerUUID, float damageReductionBonus) {
        super(ModEntities.ARMOR_SHARD.get(), level);
        this.setPos(x, y, z);
        this.ownerUUID = ownerUUID;
        this.damageReductionBonus = damageReductionBonus;
        this.lifetime = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
        this.damageReductionBonus = tag.getFloat("DamageReductionBonus");
        this.lifetime = tag.getInt("Lifetime");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
        tag.putFloat("DamageReductionBonus", this.damageReductionBonus);
        tag.putInt("Lifetime", this.lifetime);
    }

    // In 1.21.1, getAddEntityPacket() is handled automatically by the entity system.
    // No need to override it.

    @Override
    public void tick() {
        super.tick();

        this.lifetime++;

        if (this.lifetime > MAX_LIFETIME) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (!this.level().isClientSide) {
            Player owner = this.ownerUUID != null ? this.level().getPlayerByUUID(this.ownerUUID) : null;
            if (owner != null) {
                double distance = this.distanceTo(owner);

                if (distance <= ATTRACT_RANGE) {
                    Vec3 targetPos = owner.position();
                    this.setPos(
                            this.getX() + (targetPos.x - this.getX()) * MOVE_SPEED,
                            this.getY() + (targetPos.y - this.getY()) * MOVE_SPEED,
                            this.getZ() + (targetPos.z - this.getZ()) * MOVE_SPEED
                    );
                    this.setDeltaMovement(0, 0, 0);

                    if (distance <= COLLECT_RANGE) {
                        ReshapingBehavior.collectArmorShard(this, owner);
                        this.remove(RemovalReason.DISCARDED);

                        if (this.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(
                                    ParticleTypes.HAPPY_VILLAGER,
                                    this.getX(), this.getY(), this.getZ(),
                                    15, 0.3, 0.3, 0.3, 0.2
                            );
                        }
                        this.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
                    }
                }
            }
        }

        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.5;
                this.level().addParticle(
                        ParticleTypes.SMOKE,
                        this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                        0.0, 0.0, 0.0
                );
            }
        }
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public float getDamageReductionBonus() {
        return damageReductionBonus;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }
}
