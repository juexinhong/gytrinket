package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModDamageSources;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.explosion.SimulatedExplosion;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * 爆破弹实体
 * <p>
 * 僚机发射的弹射物，命中实体造成伤害。
 * 销毁时产生模拟爆炸（半径1格，0.5爆炸伤害）。
 * 无物理模式：穿墙，自行实现碰撞检测。
 */
public class ExplosiveProjectile extends ThrowableItemProjectile implements GeoEntity {

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    private float damage;
    /** 命中实体时在碰撞箱表面的交点位置，用于优化爆炸点位 */
    private Vec3 explosionPos;

    public ExplosiveProjectile(EntityType<? extends ExplosiveProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.damage = (float) Config.getWingmanExplosiveDamage();
    }

    public ExplosiveProjectile(Level level, LivingEntity owner, float damage) {
        super(ModEntities.EXPLOSIVE_PROJECTILE.get(), owner, level);
        this.damage = damage;
        this.noPhysics = true;
    }

    @Override
    protected Item getDefaultItem() {
        return net.minecraft.world.item.Items.FIRE_CHARGE;
    }

    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    private DamageSource createDamageSource() {
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            return ModDamageSources.droneBullet(this.level(), this, livingOwner);
        }
        return this.damageSources().thrown(this, owner);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // noPhysics模式下不会触发
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // canHitEntity返回false不会触发
    }

    @Override
    public void tick() {
        // 服务端：自定义实体碰撞检测
        if (!this.level().isClientSide) {
            Vec3 velocity = this.getDeltaMovement();
            Vec3 currentPos = this.position();
            Vec3 nextPos = currentPos.add(velocity);

            LivingEntity hitTarget = findTargetInPath(currentPos, nextPos);
            if (hitTarget != null) {
                // 爆炸位置使用目标实体position（与SimulatedExplosion的距离计算一致）
                this.explosionPos = hitTarget.position();

                dealDamageToTarget(hitTarget);
                triggerExplosionAndDiscard();
                return;
            }
        }

        super.tick();

        // 3秒后销毁
        if (!this.level().isClientSide && this.tickCount >= 60) {
            triggerExplosionAndDiscard();
            return;
        }
    }

    /**
     * 沿弹道路径寻找第一个可攻击的实体
     */
    @Nullable
    private LivingEntity findTargetInPath(Vec3 currentPos, Vec3 nextPos) {
        Entity owner = this.getOwner();
        Player ownerPlayer = getOwnerPlayer();

        Vec3 pathVec = nextPos.subtract(currentPos);
        double pathLength = pathVec.length();
        if (pathLength < 0.001) return null;

        AABB pathBox = new AABB(
            Math.min(currentPos.x, nextPos.x), Math.min(currentPos.y, nextPos.y), Math.min(currentPos.z, nextPos.z),
            Math.max(currentPos.x, nextPos.x), Math.max(currentPos.y, nextPos.y), Math.max(currentPos.z, nextPos.z)
        ).inflate(1.0);

        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class, pathBox);

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        AABB bulletBox = this.getBoundingBox();

        for (LivingEntity target : candidates) {
            if (target == owner) continue;
            if (ownerPlayer != null && target == ownerPlayer) continue;
            if (ownerPlayer != null && !HostileTargetManager.shouldAttackPlayer(target, ownerPlayer)) continue;

            // 检查1：子弹碰撞箱是否与实体重叠
            if (bulletBox.intersects(target.getBoundingBox())) {
                double dist = currentPos.distanceToSqr(target.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = target;
                }
                continue;
            }

            // 检查2：射线是否击中实体碰撞箱
            AABB targetBox = target.getBoundingBox().inflate(0.3);
            Vec3 intersection = targetBox.clip(currentPos, nextPos).orElse(null);
            if (intersection != null) {
                double dist = currentPos.distanceToSqr(intersection);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = target;
                }
            }
        }

        return closest;
    }

    @Nullable
    private Player getOwnerPlayer() {
        Entity owner = this.getOwner();
        if (owner instanceof WingmanConstructEntity wingmanOwner) {
            Entity ownerEntity = wingmanOwner.getOwner();
            if (ownerEntity instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * 对目标造成伤害，并移除无敌时间以确保多枚爆破弹都能命中
     */
    private void dealDamageToTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        KnockbackManager.markNoKnockback(target.getUUID());
        target.hurt(createDamageSource(), damage);
        target.invulnerableTime = 0;
    }

    /**
     * 销毁时产生模拟爆炸（在子弹自身位置）
     */
    private void triggerExplosionAndDiscard() {
        if (!this.level().isClientSide) {
            Vec3 pos = this.position();
            Vec3 explosionCenter = this.explosionPos != null ? this.explosionPos : pos;
            float explosionDamage = (float) Config.getWingmanExplosionDamage();
            double explosionRadius = Config.getWingmanExplosionRadius();

            Player ownerPlayer = getOwnerPlayer();
            DamageSource damageSource;
            if (ownerPlayer != null) {
                damageSource = this.damageSources().explosion(this, ownerPlayer);
            } else {
                Entity owner = this.getOwner();
                damageSource = this.damageSources().explosion(this, owner);
            }

            SimulatedExplosion.execute(
                this.level(),
                explosionCenter,
                explosionRadius,
                explosionDamage,
                damageSource,
                entity -> entity.isAlive()
                        && !(entity instanceof Player)
                        && entity instanceof LivingEntity
                        && !(entity instanceof DroneConstructEntity)
                        && !(entity instanceof WingmanConstructEntity)
                        && (ownerPlayer == null || HostileTargetManager.shouldAttackPlayer(entity, ownerPlayer)),
                true,
                ownerPlayer,
                0.0  // 爆破弹爆炸斥力修正为0
            );
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", this.damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("damage");
        this.noPhysics = true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }
}
