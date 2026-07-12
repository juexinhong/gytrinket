package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.execute.ExecuteToggleManager;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager;
import com.gytrinket.gytrinket.core.vulnerability.VulnerabilityApplyEvent;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;

import java.util.List;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 无人机子弹
 * <p>
 * 无人机发射的弹射物实体，对碰撞到的实体造成固定伤害。
 * 使用GeckoLib进行3D模型渲染。
 * 无物理模式：子弹穿过方块，每刻自行检测实体碰撞。
 */
public class DroneBullet extends ThrowableItemProjectile implements GeoEntity {
    /** 子弹基础伤害（实际值由Config决定） */
    public static float getBaseDamage() {
        return (float) Config.getDroneBaseDamage();
    }

    private static final EntityDataAccessor<Boolean> FROM_DRONE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    public DroneBullet(EntityType<? extends DroneBullet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DroneBullet(Level level, LivingEntity owner) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, getBaseDamage());
        this.noPhysics = true;
    }

    public DroneBullet(Level level, LivingEntity owner, float damage) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, damage);
        this.noPhysics = true;
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FROM_DRONE, false);
        builder.define(DAMAGE, getBaseDamage());
    }

    private DamageSource createDamageSource() {
        return ModDamageSources.droneBullet(this.level(), this, this.getOwner() instanceof LivingEntity living ? living : null);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // 无物理模式：禁用原版实体碰撞，由自定义检测处理
        return false;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // noPhysics模式下不会触发，保留为空
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // canHitEntity返回false不会触发，保留为空
    }

    @Override
    public void tick() {
        // 服务端：自定义实体碰撞检测（在移动前）
        if (!this.level().isClientSide) {
            Vec3 velocity = this.getDeltaMovement();
            Vec3 currentPos = this.position();
            Vec3 nextPos = currentPos.add(velocity);

            LivingEntity hitTarget = findTargetInPath(currentPos, nextPos);
            if (hitTarget != null) {
                dealDamageToTarget(hitTarget);
                this.discard();
                return;
            }
        }

        // 正常tick（noPhysics=true使子弹穿过方块）
        super.tick();

        // 3秒后销毁
        if (!this.level().isClientSide && this.tickCount >= 60) {
            this.discard();
        }


    }

    /**
     * 沿子弹路径寻找第一个可攻击的实体
     * 检测方式：
     * 1) 子弹碰撞箱与实体重叠（当前帧已接触）
     * 2) 射线从当前位置到下一刻位置击中实体碰撞箱（路径预测）
     */
    @Nullable
    private LivingEntity findTargetInPath(Vec3 currentPos, Vec3 nextPos) {
        Entity owner = this.getOwner();
        Player ownerPlayer = getOwnerPlayer();

        Vec3 pathVec = nextPos.subtract(currentPos);
        double pathLength = pathVec.length();
        if (pathLength < 0.001) return null;

        // 搜索范围：路径AABB + 扩展
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

            // 检查2：射线是否击中实体碰撞箱（路径预测，inflate给射线加粗）
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

    /**
     * 获取子弹归属玩家
     */
    @Nullable
    private Player getOwnerPlayer() {
        Entity owner = this.getOwner();
        if (owner instanceof DroneConstructEntity droneOwner) {
            Entity ownerEntity = droneOwner.getOwner();
            if (ownerEntity instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * 对目标造成伤害（含斩杀逻辑和指挥官易伤）
     */
    private void dealDamageToTarget(LivingEntity target) {
        Entity owner = this.getOwner();
        Player ownerPlayer = getOwnerPlayer();

        float damage = getDamage();
        KnockbackManager.markNoKnockback(target.getUUID());
        if (target.getHealth() < damage) {
            DamageSource executeSource = ModDamageSources.getExecuteDamageSource(target, ownerPlayer, owner);
            target.hurt(executeSource, damage * 2);
            if (ExecuteToggleManager.isExecuteEnabled(ownerPlayer)) {
                target.setLastHurtByMob(ownerPlayer);
            }
        } else {
            target.hurt(createDamageSource(), damage);
        }

        if (owner instanceof DroneConstructEntity droneShooter && droneShooter.isCommanderDrone()) {
            NeoForge.EVENT_BUS.post(
                new VulnerabilityApplyEvent(
                    "commander", Config.COMMANDER_VULNERABILITY.get().floatValue(), target, true
                )
            );
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
