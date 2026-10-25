package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.MechanicValueDefs;
import com.gy_mod.gy_trinket.core.damage.SecondaryDamageMerger;
import com.gy_mod.gy_trinket.core.entity.construct.HostileTargetManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.core.projectile.MultipartHitScan;
import com.gy_mod.gy_trinket.core.vulnerability.VulnerabilityApplyEvent;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

/**
 * 无人机子弹
 * <p>
 * 无人机发射的弹射物实体，对碰撞到的实体造成固定伤害。
 * 使用GeckoLib进行3D模型渲染。
 * 无物理模式：子弹穿过方块，每刻自行检测实体碰撞。
 */
public class DroneBullet extends ThrowableItemProjectile {
    /** 子弹基础伤害（实际值由Config决定） */
    public static float getBaseDamage() {
        return (float) MechanicValueDefs.defaultValueOf("drone_module_items", "base_damage", 0.3);
    }

    private static final EntityDataAccessor<Boolean> FROM_DRONE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DroneBullet.class, EntityDataSerializers.FLOAT);

    public DroneBullet(EntityType<? extends DroneBullet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DroneBullet(Level level, LivingEntity owner) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, getBaseDamage());
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DroneBullet(Level level, LivingEntity owner, float damage) {
        super(ModEntities.DRONE_BULLET.get(), owner, level);
        this.entityData.set(FROM_DRONE, true);
        this.entityData.set(DAMAGE, damage);
        this.noPhysics = true;
        this.setNoGravity(true);
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
        this.entityData.define(FROM_DRONE, false);
        this.entityData.define(DAMAGE, getBaseDamage());
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

            MultipartHitScan.Hit hit = findTargetInPath(currentPos, nextPos);
            if (hit != null) {
                // 将子弹移动到上一刻（本刻预测）计算得出的相交位置，再结算伤害
                this.setPos(hit.position().x, hit.position().y, hit.position().z);
                dealDamageToTarget(hit);
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
     * 沿子弹路径寻找第一个可攻击的碰撞箱
     * <p>
     * 检测方式：射线从本刻位置到下一刻位置击中碰撞箱（路径预测；
     * 射线自本刻位置发出，天然覆盖本刻重叠情况）。
     * <p>
     * 多体节实体（末影龙/娜迦/九头蛇等）由 {@link MultipartHitScan} 逐个测体节：
     * 体节不注册进 level 实体列表，只测父实体 AABB 会打空伸出的头/颈/翅/尾/节段。
     */
    @Nullable
    private MultipartHitScan.Hit findTargetInPath(Vec3 currentPos, Vec3 nextPos) {
        Entity owner = this.getOwner();
        Player ownerPlayer = getOwnerPlayer();

        return MultipartHitScan.findFirstOnPath(this.level(), currentPos, nextPos,
                MultipartHitScan.DEFAULT_HITBOX_INFLATE,
                target -> {
                    if (target == owner) return false;
                    if (ownerPlayer != null && target == ownerPlayer) return false;
                    return ownerPlayer == null
                            || HostileTargetManager.shouldAttackPlayer(target, ownerPlayer);
                });
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
     * 对目标造成伤害（含指挥官易伤）
     * <p>
     * 命中体节时由体节自身结算（{@code hitbox.hurt}），保留各模组的体节倍率
     * （末影龙头部满伤、躯干/翼 1/4，娜迦节段 2/3 等）；父实体只用于记账
     * （伤害合并、击退标记、易伤）。
     * <p>
     * 伤害经次级伤害合并系统延迟施加（同类型伤害在时间窗口内累积合并，降低受击频率）。
     * 体节命中时合并键带上体节 ID，保证合并批次与施加时命中的碰撞箱一致。
     */
    private void dealDamageToTarget(MultipartHitScan.Hit hit) {
        Entity owner = this.getOwner();
        LivingEntity target = hit.target();
        Entity hitbox = hit.hitbox();

        float damage = getDamage();
        KnockbackManager.markNoKnockback(target.getUUID());
        String mergeType = hitbox == target
                ? "drone_bullet"
                : "drone_bullet:part:" + hitbox.getId();
        SecondaryDamageMerger.accumulate(target, mergeType, damage, (t, mergedDamage) -> {
            // 攻击前：清除无敌时间（清掉残留的原版无敌帧，保证本次伤害足额施加）
            t.invulnerableTime = 0;
            // 施加时攻击者恒为无人机本体（避免非致死时对玩家触发仇恨）：
            // 预估致死时由 ExecuteDamageHandler 取消原伤害并改用归属玩家的
            // 斩杀伤害源（execute_damage）完成最后一击
            LivingEntity droneCause = owner instanceof LivingEntity living ? living : null;
            hitbox.hurt(ModDamageSources.droneBullet(t.level(), this, droneCause), mergedDamage);
            // 攻击后：清除无敌时间（清掉本次 hurt 写入的原版无敌帧，
            // 避免阻挡紧随其后的下一发子弹或玩家攻击）
            t.invulnerableTime = 0;
            if (owner instanceof DroneConstructEntity droneShooter && droneShooter.isCommanderDrone()) {
                MinecraftForge.EVENT_BUS.post(
                    new VulnerabilityApplyEvent(
                        "commander", Config.COMMANDER_VULNERABILITY.get().floatValue(), t, true
                    )
                );
            }
        });
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
        this.setNoGravity(true);
    }

}
