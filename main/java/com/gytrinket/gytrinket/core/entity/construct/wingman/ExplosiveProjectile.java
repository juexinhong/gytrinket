package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.core.damage.DamageSourcesCompat;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.MechanicValueDefs;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModDamageSources;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.explosion.EnergyWaveExplosion;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.damage.SecondaryDamageMerger;
import com.gytrinket.gytrinket.core.projectile.MultipartHitScan;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 爆破弹实体
 * <p>
 * 僚机发射的弹射物，命中实体造成伤害。
 * 销毁时产生模拟爆炸（半径1格，0.5爆炸伤害）。
 * 无物理模式：穿墙，自行实现碰撞检测。
 */
public class ExplosiveProjectile extends ThrowableItemProjectile {

    private float damage;

    public ExplosiveProjectile(EntityType<? extends ExplosiveProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.damage = (float) MechanicValueDefs.defaultValueOf("wingman_module_items", "base_damage", 0.5);
    }

    public ExplosiveProjectile(Level level, LivingEntity owner, float damage) {
        super(ModEntities.EXPLOSIVE_PROJECTILE.get(), owner, level);
        this.damage = damage;
        this.noPhysics = true;
        this.setNoGravity(true);
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
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    private DamageSource createDamageSource() {
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            return ModDamageSources.droneBullet(this.getLevel(), this, livingOwner);
        }
        return DamageSource.thrown(this, owner);
    }

    /**
     * 创建带有守卫阵列仇恨集中的伤害源
     */
    private DamageSource createDamageSourceWithGuardAggro() {
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            return ModDamageSources.droneBulletWithGuardAggro(this.getLevel(), this, livingOwner);
        }
        return DamageSource.thrown(this, owner);
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
        if (!this.getLevel().isClientSide) {
            Vec3 velocity = this.getDeltaMovement();
            Vec3 currentPos = this.position();
            Vec3 nextPos = currentPos.add(velocity);

            MultipartHitScan.Hit hit = findTargetInPath(currentPos, nextPos);
            if (hit != null) {
                // 将弹体移动到上一刻（本刻预测）计算得出的相交位置，再结算伤害与爆炸
                this.setPos(hit.position().x, hit.position().y, hit.position().z);
                dealDamageToTarget(hit);
                triggerExplosionAndDiscard();
                return;
            }
        }

        super.tick();

        // 3秒后销毁
        if (!this.getLevel().isClientSide && this.tickCount >= 60) {
            triggerExplosionAndDiscard();
            return;
        }
    }

    /**
     * 沿弹道路径寻找第一个可攻击的碰撞箱
     * <p>
     * 检测方式：射线从本刻位置到下一刻位置击中碰撞箱（路径预测；
     * 射线自本刻位置发出，天然覆盖本刻重叠情况）。
     * <p>
     * 多体节实体（末影龙/娜迦/九头蛇等）由 {@link MultipartHitScan} 逐个测体节：
     * 体节不注册进 level 实体列表，只测父实体 AABB 会打空伸出的头/颈/翅/尾/节段；
     * 命中体节时爆心也落在体节上，爆炸位置与视觉一致。
     */
    @Nullable
    private MultipartHitScan.Hit findTargetInPath(Vec3 currentPos, Vec3 nextPos) {
        Entity owner = this.getOwner();
        Player ownerPlayer = getOwnerPlayer();

        return MultipartHitScan.findFirstOnPath(this.getLevel(), currentPos, nextPos,
                MultipartHitScan.DEFAULT_HITBOX_INFLATE,
                target -> {
                    if (target == owner) return false;
                    if (ownerPlayer != null && target == ownerPlayer) return false;
                    return ownerPlayer == null
                            || HostileTargetManager.shouldAttackPlayer(target, ownerPlayer);
                });
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
     * 对目标造成伤害，并移除无敌时间以确保多枚爆破弹都能命中。
     * <p>
     * 命中体节时由体节自身结算（{@code hitbox.hurt}），保留各模组的体节倍率
     * （末影龙头部满伤、躯干/翼 1/4，娜迦节段 2/3 等）；父实体只用于记账。
     * 合并键带上体节 ID，保证合并批次与施加时命中的碰撞箱一致。
     */
    private void dealDamageToTarget(MultipartHitScan.Hit hit) {
        LivingEntity target = hit.target();
        Entity hitbox = hit.hitbox();

        DamageSource source = createDamageSourceWithGuardAggro();
        String mergeType = hitbox == target
                ? "explosive_shell"
                : "explosive_shell:part:" + hitbox.getId();
        SecondaryDamageMerger.accumulate(target, mergeType, damage, (t, mergedDamage) -> {
            t.invulnerableTime = 0;
            hitbox.hurt(source, mergedDamage);
            t.invulnerableTime = 0;
        });
    }

    /**
     * 销毁时产生能量波爆炸（方向为爆破弹飞行方向，溅射长度1.5格）
     * 若玩家拥有震撼弹模块，伤害和溅射长度提升
     */
    private void triggerExplosionAndDiscard() {
        if (!this.getLevel().isClientSide) {
            Vec3 explosionCenter = this.position();
            float explosionDamage = (float) Config.getWingmanExplosionDamage();
            double splashLength = 1.5;
            Vec3 splashDirection = this.getDeltaMovement();

            Player ownerPlayer = getOwnerPlayer();

            // 震撼弹模块加成
            if (ownerPlayer != null && ShockwaveModuleManager.hasShockwaveModule(ownerPlayer.getUUID())) {
                explosionDamage *= Config.getWingmanShockwaveDamageMultiplier();
                splashLength *= Config.getWingmanShockwaveSplashLengthMultiplier();
            }

            // 爆炸伤害归属僚机（非斩杀时）；斩杀时由 EnergyWaveExplosion 内部切换为玩家归属
            Entity owner = this.getOwner();
            DamageSource damageSource;
            if (owner instanceof LivingEntity livingOwner) {
                damageSource = DamageSourcesCompat.explosion(this, livingOwner);
            } else {
                damageSource = DamageSourcesCompat.explosion(this, owner);
            }

            EnergyWaveExplosion.execute(
                this.getLevel(),
                explosionCenter,
                splashDirection,
                splashLength,
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
                null,
                true,
                0.0,
                "energy_wave"
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
        this.setNoGravity(true);
    }
}

