package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneSpecialBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.PursuitBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.FormationBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.SelfDestructBehavior;
import com.gy_mod.gy_trinket.core.explosion.SimulatedExplosion;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 无人机构造体实体类
 * <p>
 * 作为无人机系统的核心实体，负责处理无人机的行为逻辑、朝向控制、攻击执行等。
 * 支持三种阵列类型：环绕阵列(ORBIT)、追击阵列(PURSUIT)、待机阵列(STANDBY)。
 * 通过效果标签(EffectTag)可扩展无人机能力：突击(ASSAULT)增加攻击速度，防御(DEFENSE)增加生命值。
 */
public class DroneConstructEntity extends AbstractConstructEntity {

    /**
     * 无人机效果标签枚举
     * ASSAULT: 突击标签，提升攻击速度
     * DEFENSE: 防御标签，提升生命值
     * COMMANDER: 指挥官标签，用于列队阵列的指挥官无人机
     */
    public enum DroneEffectTag {
        ASSAULT,
        DEFENSE,
        COMMANDER
    }

    private static final EntityDataAccessor<Integer> DATA_ARRAY_TYPE = SynchedEntityData.defineId(DroneConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EFFECT_TAGS = SynchedEntityData.defineId(DroneConstructEntity.class, EntityDataSerializers.INT);

    private final Set<DroneEffectTag> effectTags = new HashSet<>();

    private boolean isExploding = false;
    private double explosionSpeed = 0;
    private int explosionTimer = 0;

    /** 存储对应的 DroneConstruct 引用 */
    private DroneConstruct droneConstruct;

    // 列队阵列相关字段
    private int droneIndex = 0;
    private int totalDrones = 1;
    private boolean isLeftSide = false;
    private int sideIndex = 0;
    private int wingIndex = 0;
    private boolean isWingEndDrone = false;
    private boolean isCommander = false;

    /**
     * 基础构造函数，由Minecraft实体系统调用
     */
    public DroneConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.baseMaxHealth = Config.getDroneBaseHealth();
        this.baseAttackDamage = Config.getDroneBaseDamage();
    }

    /**
     * 带所有者和构造体数据的构造函数
     */
    public DroneConstructEntity(Level level, UUID ownerUUID, DroneConstruct droneConstruct) {
        this(ModEntities.DRONE_CONSTRUCT.get(), level);
        setOwnerUUID(ownerUUID);
        this.droneConstruct = droneConstruct;
    }

    // ===== 同步数据 =====

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ARRAY_TYPE, 0);
        entityData.define(DATA_EFFECT_TAGS, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_EFFECT_TAGS.equals(key)) {
            int data = entityData.get(DATA_EFFECT_TAGS);
            effectTags.clear();
            for (DroneEffectTag effectTag : DroneEffectTag.values()) {
                if ((data & (1 << effectTag.ordinal())) != 0) {
                    effectTags.add(effectTag);
                }
            }
            this.refreshDimensions();
        }
    }

    // ===== 阵列类型判定 =====

    public boolean isOrbitArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 0;
    }

    public boolean isPursuitArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 1;
    }

    public boolean isStandbyArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 2;
    }

    public boolean isFormationArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 3;
    }

    public boolean isGuardArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 4;
    }

    public IDroneBehavior getBehavior() {
        if (this.droneConstruct != null) {
            return this.droneConstruct.getBehavior();
        }
        if (isOrbitArray()) {
            return DroneArrayType.Types.ORBIT.getBehavior();
        } else if (isPursuitArray()) {
            return DroneArrayType.Types.PURSUIT.getBehavior();
        } else if (isFormationArray()) {
            return DroneArrayType.Types.FORMATION.getBehavior();
        } else if (isGuardArray()) {
            return DroneArrayType.Types.GUARD.getBehavior();
        } else {
            return DroneArrayType.Types.STANDBY.getBehavior();
        }
    }

    public void setArrayType(DroneArrayType arrayType) {
        int index;
        if (arrayType.hasTag(DroneArrayType.Tags.PURSUIT)) {
            index = 1;
        } else if (arrayType.hasTag(DroneArrayType.Tags.STANDBY)) {
            index = 2;
        } else if (arrayType.hasTag(DroneArrayType.Tags.FORMATION)) {
            index = 3;
        } else if (arrayType.hasTag(DroneArrayType.Tags.GUARD)) {
            index = 4;
        } else {
            index = 0;
        }
        this.entityData.set(DATA_ARRAY_TYPE, index);
        if (this.droneConstruct != null) {
            this.droneConstruct.setArrayType(arrayType);
        }
        this.refreshDimensions();
    }

    // ===== 碰撞规则 =====

    /**
     * 防御无人机使用扩大的碰撞箱以拦截弹射物。
     * 通过重写 makeBoundingBox() 返回扩大的碰撞箱来实现防御无人机的弹射物拦截。
     */
    @Override
    protected AABB makeBoundingBox() {
        if (effectTags != null && isDefenseDrone()) {
            float width = 0.8f;
            float height = 4.0f;
            return AABB.ofSize(this.getEyePosition(), (double) width, (double) height, (double) width);
        }
        return super.makeBoundingBox();
    }

    // ===== 效果标签管理 =====

    public void addEffectTag(DroneEffectTag tag) {
        effectTags.add(tag);
        if (tag == DroneEffectTag.COMMANDER) {
            this.isCommander = true;
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
        updateEffectData();
        applyAttributeModifiers();
        this.refreshDimensions();
    }

    public void removeEffectTag(DroneEffectTag tag) {
        effectTags.remove(tag);
        if (tag == DroneEffectTag.COMMANDER) {
            this.isCommander = false;
            this.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
        updateEffectData();
        applyAttributeModifiers();
        this.refreshDimensions();
    }

    public boolean hasEffectTag(DroneEffectTag tag) {
        return effectTags.contains(tag);
    }

    public Set<DroneEffectTag> getEffectTags() {
        return new HashSet<>(effectTags);
    }

    private void updateEffectData() {
        int data = 0;
        if (effectTags.contains(DroneEffectTag.ASSAULT)) {
            data |= 1 << DroneEffectTag.ASSAULT.ordinal();
        }
        if (effectTags.contains(DroneEffectTag.DEFENSE)) {
            data |= 1 << DroneEffectTag.DEFENSE.ordinal();
        }
        if (effectTags.contains(DroneEffectTag.COMMANDER)) {
            data |= 1 << DroneEffectTag.COMMANDER.ordinal();
        }
        this.entityData.set(DATA_EFFECT_TAGS, data);
    }

    public boolean isAssaultDrone() {
        return hasEffectTag(DroneEffectTag.ASSAULT);
    }

    public boolean isDefenseDrone() {
        return hasEffectTag(DroneEffectTag.DEFENSE);
    }

    public boolean isCommanderDrone() {
        return hasEffectTag(DroneEffectTag.COMMANDER);
    }

    // ===== 引用与状态 =====

    public DroneConstruct getDroneConstruct() {
        return droneConstruct;
    }

    public boolean isExploding() {
        return isExploding;
    }

    public void setExploding(boolean exploding) {
        isExploding = exploding;
    }

    public double getExplosionSpeed() {
        return explosionSpeed;
    }

    public void setExplosionSpeed(double speed) {
        this.explosionSpeed = speed;
    }

    public int getExplosionTimer() {
        return explosionTimer;
    }

    public void setExplosionTimer(int timer) {
        this.explosionTimer = timer;
    }

    public Vec3 getLookDirection() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    // ===== 自爆 =====

    public void explodeAndRemove() {
        if (this.level().isClientSide) return;

        // 最终指令的自爆视为死亡判定，可以触发自毁装置
        if (SelfDestructBehavior.hasRequiredItems(this)) {
            SelfDestructBehavior.triggerSelfDestructExplosion(this);
        }

        float maxHealth = this.getMaxHealth();
        float speed = (float) this.explosionSpeed;
        float coefficient = Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get().floatValue();
        float damage = maxHealth * speed * coefficient;
        double radius = Config.NEAR_DEATH_EXPLOSION_RADIUS.get();

        Vec3 pos = this.position();

        Entity owner = this.getOwner();
        DamageSource damageSource;
        if (owner instanceof Player player) {
            damageSource = this.damageSources().explosion(this, player);
        } else {
            damageSource = this.damageSources().explosion(this, owner);
        }

        Player playerOwner = owner instanceof Player p ? p : null;

        SimulatedExplosion.execute(
                this.level(),
                pos,
                radius,
                damage,
                damageSource,
                entity -> entity != this && entity.isAlive()
                        && !(entity instanceof Player)
                        && entity instanceof net.minecraft.world.entity.Mob
                        && HostileTargetManager.shouldAttackPlayer(entity, playerOwner),
                false,
                playerOwner
        );

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        removeFromConstructManager();
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    // ===== 抽象方法实现 =====

    @Override
    protected String getConstructTypeId() {
        return DroneConstructTypes.DRONE;
    }

    @Override
    protected ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer) {
        DroneArrayType currentArrayType = DroneArrayManager.getInstance().getPlayerArrayType(ownerPlayer);
        if (currentArrayType == null) {
            currentArrayType = DroneArrayType.Types.ORBIT;
        }
        DroneConstructData newData = new DroneConstructData(
                DroneConstructTypes.DRONE,
                this.getUUID(),
                this.getBaseMaxHealth(),
                currentArrayType);
        newData.setHasAssaultModule(this.hasEffectTag(DroneEffectTag.ASSAULT));
        newData.setHasDefenseModule(this.hasEffectTag(DroneEffectTag.DEFENSE));
        return newData;
    }

    @Override
    protected void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes) {
        ConstructAttributeApplier.applyAttributesToDrone(playerUUID, this, attributes);
    }

    // ===== 类型特定 NBT 钩子 =====

    @Override
    protected void addTypeSpecificSaveData(CompoundTag tag) {
        tag.putInt("array_type", this.entityData.get(DATA_ARRAY_TYPE));
        tag.putInt("effects", this.entityData.get(DATA_EFFECT_TAGS));
        tag.putInt("drone_index", this.droneIndex);
        tag.putInt("total_drones", this.totalDrones);
        tag.putBoolean("is_left_side", this.isLeftSide);
        tag.putInt("side_index", this.sideIndex);
        tag.putInt("wing_index", this.wingIndex);
        tag.putBoolean("is_wing_end", this.isWingEndDrone);
    }

    @Override
    protected void readTypeSpecificSaveData(CompoundTag tag) {
        if (tag.contains("array_type")) {
            this.entityData.set(DATA_ARRAY_TYPE, tag.getInt("array_type"));
        }
        if (tag.contains("effects")) {
            int effectData = tag.getInt("effects");
            effectTags.clear();
            for (DroneEffectTag effectTag : DroneEffectTag.values()) {
                if ((effectData & (1 << effectTag.ordinal())) != 0) {
                    effectTags.add(effectTag);
                }
            }
        }
        if (tag.contains("drone_index")) {
            this.droneIndex = tag.getInt("drone_index");
        }
        if (tag.contains("total_drones")) {
            this.totalDrones = tag.getInt("total_drones");
        }
        if (tag.contains("is_left_side")) {
            this.isLeftSide = tag.getBoolean("is_left_side");
        }
        if (tag.contains("side_index")) {
            this.sideIndex = tag.getInt("side_index");
        }
        if (tag.contains("wing_index")) {
            this.wingIndex = tag.getInt("wing_index");
        }
        if (tag.contains("is_wing_end")) {
            this.isWingEndDrone = tag.getBoolean("is_wing_end");
        }
        if (effectTags.contains(DroneEffectTag.COMMANDER)) {
            this.isCommander = true;
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
    }

    @Override
    protected void onAttributesApplied() {
        updateEffectData();
        this.refreshDimensions();
    }

    @Override
    protected void onRemoveFromConstructManager() {
        CommanderManager.getInstance().onDroneRemoved(this);
    }

    // ===== 伤害与死亡（添加无人机特殊行为触发） =====

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && this.getHealth() <= this.getMaxHealth() * 0.2f && this.getHealth() > 0) {
            triggerSpecialBehaviorsOnNearDeath(source);
        }
        if (result) {
            triggerSpecialBehaviorsOnDamageTaken(source, amount);
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (tryPreventDeathBehaviors(source)) {
            return;
        }
        triggerSpecialBehaviorsOnDeath(source);
        super.die(source);
    }

    // ===== 特殊行为触发 =====

    private void tickSpecialBehaviors() {
        List<IDroneSpecialBehavior> behaviors = DroneSpecialBehaviorManager.getInstance().getApplicableBehaviors(this);
        for (IDroneSpecialBehavior behavior : behaviors) {
            behavior.onTick(this);
        }
    }

    private void triggerSpecialBehaviorsOnDamageTaken(DamageSource source, float amount) {
        List<IDroneSpecialBehavior> behaviors = DroneSpecialBehaviorManager.getInstance().getApplicableBehaviors(this);
        for (IDroneSpecialBehavior behavior : behaviors) {
            behavior.onDamageTaken(this, source, amount);
        }
    }

    private void triggerSpecialBehaviorsOnNearDeath(DamageSource source) {
        List<IDroneSpecialBehavior> behaviors = DroneSpecialBehaviorManager.getInstance().getApplicableBehaviors(this);
        for (IDroneSpecialBehavior behavior : behaviors) {
            behavior.onNearDeath(this, source);
        }
    }

    private void triggerSpecialBehaviorsOnDeath(DamageSource source) {
        List<IDroneSpecialBehavior> behaviors = DroneSpecialBehaviorManager.getInstance().getApplicableBehaviors(this);
        for (IDroneSpecialBehavior behavior : behaviors) {
            behavior.onDeath(this, source);
        }
    }

    private boolean tryPreventDeathBehaviors(DamageSource source) {
        List<IDroneSpecialBehavior> behaviors = DroneSpecialBehaviorManager.getInstance().getApplicableBehaviors(this);
        behaviors.sort(Comparator.comparingInt(IDroneSpecialBehavior::getPriority));
        for (IDroneSpecialBehavior behavior : behaviors) {
            if (behavior.tryPreventDeath(this, source)) {
                return true;
            }
        }
        return false;
    }

    // ===== 主循环 =====

    /**
     * 每刻更新逻辑
     * 处理无人机的位置更新、朝向控制、攻击执行等核心逻辑
     */
    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.tickCount > 100 && this.tickCount % 20 == 0) {
            checkManagerRegistration();
        }

        tickSpecialBehaviors();

        if (isExploding) {
            return;
        }

        if (this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.98, 0.98, 0.98));
        }

        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.isAlive()) {
            // 更新列队阵列的攻击传递状态
            if (this.isFormationArray() && this.getOwnerUUID() != null) {
                FormationBehavior.tickAttackPass(this.getOwnerUUID());
            }

            IDroneBehavior behavior = getBehavior();
            if (behavior != null) {
                // 更新位置
                behavior.updatePosition(this, (LivingEntity) owner, 0, 1.0f / 20.0f);

                // 根据阵列类型执行不同的朝向和攻击逻辑
                if (this.isPursuitArray()) {
                    // 追击阵列：优先攻击玩家附近的敌人，使用插值旋转
                    if (behavior instanceof PursuitBehavior pursuitBehavior) {
                        LivingEntity targetToAttack = null;

                        // 优先检查是否有优先攻击目标（玩家6格范围内的敌人）
                        if (pursuitBehavior.hasPriorityTarget(this, (LivingEntity) owner)) {
                            targetToAttack = pursuitBehavior.findPriorityTarget(this, (LivingEntity) owner);
                        } else {
                            List<LivingEntity> targets = behavior.searchTargets(this, (LivingEntity) owner, behavior.getAttackRange());
                            if (!targets.isEmpty()) {
                                targetToAttack = targets.get(0);
                            }
                        }

                        if (targetToAttack != null) {
                            boolean canAttack = this.distanceTo(targetToAttack) <= behavior.getAttackRange();

                            if (canAttack) {
                                faceTargetWithInterpolation(targetToAttack);
                            }

                            behavior.executeAttack(this, (LivingEntity) owner, targetToAttack, canAttack);
                        } else {
                            // 无攻击目标时，检查是否有记忆目标（3秒记忆期内继续转向）
                            LivingEntity memoryTarget = pursuitBehavior.getMemoryTarget(this);
                            if (memoryTarget != null) {
                                faceTargetWithInterpolation(memoryTarget);
                            } else {
                                // 无记忆目标时朝向玩家方向
                                faceOwnerDirection((LivingEntity) owner);
                            }
                        }
                    }
                } else if (this.isFormationArray()) {
                    // 列队阵列：先更新朝向，确保视线判定使用正确的俯仰角
                    faceFormationDirection((LivingEntity) owner, null);

                    // 翼根无人机自由攻击并触发传递，非翼根无人机等待传递
                    List<LivingEntity> targets = behavior.searchTargets(this, (LivingEntity) owner, behavior.getAttackRange());

                    if (this.getWingIndex() == 0) {
                        if (!targets.isEmpty() && this.attackCooldown <= 0) {
                            LivingEntity nearestTarget = targets.get(0);
                            boolean canAttack = this.distanceTo(nearestTarget) <= behavior.getAttackRange();

                            if (canAttack) {
                                faceFormationDirection((LivingEntity) owner, nearestTarget);
                                FormationBehavior.performBeamAttack(this, nearestTarget);
                                int formationCooldown = (int) (Config.FORMATION_ATTACK_INTERVAL.get() * 20.0 / this.attackSpeedMultiplier);
                                this.setAttackCooldown(Math.max(1, formationCooldown));
                                FormationBehavior.onWingRootAttack(this, nearestTarget);
                            } else {
                                faceFormationDirection((LivingEntity) owner, null);
                            }
                        } else {
                            faceFormationDirection((LivingEntity) owner, null);
                        }
                    } else {
                        // 非翼根无人机：收到传递才能攻击，无目标则终止传递
                        if (FormationBehavior.canNonWingRootAttack(this)) {
                            if (!targets.isEmpty()) {
                                LivingEntity nearestTarget = targets.get(0);
                                boolean canAttack = this.distanceTo(nearestTarget) <= behavior.getAttackRange();

                                if (canAttack) {
                                    faceFormationDirection((LivingEntity) owner, nearestTarget);
                                    FormationBehavior.performBeamAttack(this, nearestTarget);
                                    FormationBehavior.onNonWingRootAttack(this);
                                } else {
                                    faceFormationDirection((LivingEntity) owner, null);
                                }
                            } else {
                                faceFormationDirection((LivingEntity) owner, null);
                                FormationBehavior.terminateAttackPass(this);
                            }
                        } else {
                            faceFormationDirection((LivingEntity) owner, null);
                        }
                    }
                } else if (this.isGuardArray()) {
                    List<LivingEntity> targets = behavior.searchTargets(this, (LivingEntity) owner, behavior.getAttackRange());
                    if (!targets.isEmpty()) {
                        LivingEntity nearestTarget = targets.get(0);
                        boolean canAttack = this.distanceTo(nearestTarget) <= behavior.getAttackRange();

                        faceOutwardFromOwner((LivingEntity) owner);

                        behavior.executeAttack(this, (LivingEntity) owner, nearestTarget, canAttack);
                    } else {
                        faceOutwardFromOwner((LivingEntity) owner);
                    }
                } else {
                    // 环绕阵列
                    List<LivingEntity> targets = behavior.searchTargets(this, (LivingEntity) owner, behavior.getAttackRange());
                    if (!targets.isEmpty()) {
                        LivingEntity nearestTarget = targets.get(0);
                        boolean canAttack = this.distanceTo(nearestTarget) <= behavior.getAttackRange();

                        if (this.isDefenseDrone()) {
                            faceOutwardFromOwner((LivingEntity) owner);
                        } else if (canAttack) {
                            faceTarget(nearestTarget);
                        }

                        behavior.executeAttack(this, (LivingEntity) owner, nearestTarget, canAttack);
                    } else {
                        if (this.isDefenseDrone()) {
                            faceOutwardFromOwner((LivingEntity) owner);
                        } else {
                            faceOwnerPerpendicular();
                        }
                    }
                }
            }
        }
    }

    // ===== 无人机专用朝向方法 =====

    /**
     * 立即朝向目标（无插值）
     */
    private void faceTarget(LivingEntity target) {
        Vec3 dronePos = this.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);

        double dx = targetPos.x - dronePos.x;
        double dy = targetPos.y - dronePos.y;
        double dz = targetPos.z - dronePos.z;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = -(float) (Math.atan2(dy, horizontalDistance) * (180.0 / Math.PI));

        this.setYRot(targetYaw);
        this.setXRot(targetPitch);
        this.setYHeadRot(targetYaw);
        this.yBodyRot = targetYaw;
        this.yHeadRot = targetYaw;
    }

    /**
     * 朝向与玩家连线的垂直方向（用于环绕阵列无目标时）
     */
    private void faceOwnerPerpendicular() {
        Entity owner = this.getOwner();
        if (owner == null) {
            return;
        }

        Vec3 dronePos = this.position();
        Vec3 ownerPos = owner.position();

        double dx = dronePos.x - ownerPos.x;
        double dz = dronePos.z - ownerPos.z;

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.1) {
            return;
        }

        float perpendicularYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI)) + 90.0f;

        this.setYRot(perpendicularYaw);
        this.setXRot(0);
        this.setYHeadRot(perpendicularYaw);
        this.yBodyRot = perpendicularYaw;
        this.yHeadRot = perpendicularYaw;
    }

    /**
     * 朝向远离玩家的方向（用于防御无人机）
     */
    private void faceOutwardFromOwner(LivingEntity owner) {
        Vec3 dronePos = this.position();
        Vec3 ownerPos = owner.position();

        double dx = dronePos.x - ownerPos.x;
        double dz = dronePos.z - ownerPos.z;

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.1) {
            return;
        }

        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        this.setYRot(yaw);
        this.setXRot(0);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /**
     * 列队阵列朝向：基于玩家朝向延伸点，并可在30度范围内偏向目标
     */
    private void faceFormationDirection(LivingEntity owner, @Nullable LivingEntity target) {
        Vec3 ownerPos = owner.position();
        Vec3 dronePos = this.position();

        float ownerYaw = owner.getYRot();
        double yawRad = Math.toRadians(ownerYaw);
        Vec3 lookDir = new Vec3(
            Math.sin(yawRad),
            0,
            -Math.cos(yawRad)
        ).normalize();

        Vec3 extendedPos = ownerPos.add(lookDir.scale(-6));  //虽然是玩家身后6格.其实为了聚焦也是没有办法的事

        Vec3 toExtended = extendedPos.subtract(dronePos).normalize();
        float baseYaw = (float) Math.toDegrees(Math.atan2(toExtended.z, toExtended.x)) - 90f;

        if (target != null) {
            Vec3 toTarget = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(dronePos).normalize();
            float targetYaw = (float) Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90f;

            float yawDiff = targetYaw - baseYaw;
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            yawDiff = Math.max(-30f, Math.min(30f, yawDiff));
            baseYaw += yawDiff;
        }

        float pitch = owner.getXRot();

        this.setYRot(baseYaw);
        this.setXRot(pitch);
        this.setYHeadRot(baseYaw);
        this.yBodyRot = baseYaw;
        this.yHeadRot = baseYaw;
    }

    // ===== 属性注册 =====

    public static AttributeSupplier.Builder createAttributes() {
        // 注册时使用默认值，实际值由 applyAttributeModifiers() 从 Config 覆盖
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 5.0)
            .add(Attributes.FOLLOW_RANGE, 16.0)
            .add(Attributes.ATTACK_DAMAGE, 0.3);
    }

    // ===== 列队阵列相关 getter/setter =====

    public int getDroneIndex() {
        return this.droneIndex;
    }

    public void setDroneIndex(int index) {
        this.droneIndex = index;
    }

    public int getTotalDrones() {
        return this.totalDrones;
    }

    public void setTotalDrones(int total) {
        this.totalDrones = total;
    }

    public boolean isLeftSide() {
        return this.isLeftSide;
    }

    public void setLeftSide(boolean leftSide) {
        this.isLeftSide = leftSide;
    }

    public int getSideIndex() {
        return this.sideIndex;
    }

    public void setSideIndex(int index) {
        this.sideIndex = index;
    }

    public int getWingIndex() {
        return this.wingIndex;
    }

    public void setWingIndex(int index) {
        this.wingIndex = index;
    }

    public boolean isWingEndDrone() {
        return this.isWingEndDrone;
    }

    public void setWingEndDrone(boolean wingEnd) {
        this.isWingEndDrone = wingEnd;
    }

    public boolean isCommander() {
        return this.isCommander;
    }

    public void setCommander(boolean commander) {
        this.isCommander = commander;
    }
}
