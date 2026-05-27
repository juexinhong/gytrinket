package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneSpecialBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.PursuitBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.FormationBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 无人机构造体实体类
 * <p>
 * 作为无人机系统的核心实体，负责处理无人机的行为逻辑、朝向控制、攻击执行等。
 * 支持三种阵列类型：环绕阵列(ORBIT)、追击阵列(PURSUIT)、待机阵列(STANDBY)。
 * 通过效果标签(EffectTag)可扩展无人机能力：突击(ASSAULT)增加攻击速度，防御(DEFENSE)增加生命值。
 */
public class DroneConstructEntity extends PathfinderMob implements GeoEntity {

    /**
     * 无人机效果标签枚举
     * ASSAULT: 突击标签，提升攻击速度
     * DEFENSE: 防御标签，提升生命值
     */
    public enum DroneEffectTag {
        ASSAULT,
        DEFENSE,
        COMMANDER
    }

    private static final EntityDataAccessor<Integer> DATA_ARRAY_TYPE = SynchedEntityData.defineId(DroneConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EFFECT_TAGS = SynchedEntityData.defineId(DroneConstructEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private java.util.UUID ownerUUID;

    private int attackCooldown = 0;

    public void setAttackCooldown(int cooldown) {
        this.attackCooldown = cooldown;
    }

    public int getAttackCooldown() {
        return this.attackCooldown;
    }

    private final Set<DroneEffectTag> effectTags = new HashSet<>();
    
    private double baseMaxHealth = 5.0;
    private double baseAttackDamage = 0.3;
    private double attackSpeedMultiplier = 1.0;

    private boolean isExploding = false;
    private double explosionSpeed = 0;
    private int explosionTimer = 0;
    private boolean isTemporarilyPickable = false;
    
    // 存储对应的 DroneConstruct 引用
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
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    private void checkManagerRegistration() {
        java.util.UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        com.gy_mod.gy_trinket.core.entity.construct.ConstructManager cm =
            com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance();

        java.util.Map<java.util.UUID, net.minecraft.world.entity.Entity> activeEntities =
            cm.getActiveConstructEntities(ownerUUID, com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes.DRONE);
        boolean inActiveEntities = activeEntities != null && activeEntities.containsKey(this.getUUID());

        boolean inPlayerConstructs = false;
        java.util.Map<String, java.util.List<com.gy_mod.gy_trinket.core.entity.construct.ConstructData>> constructsMap =
            cm.getPlayerConstructs(ownerUUID);
        java.util.List<com.gy_mod.gy_trinket.core.entity.construct.ConstructData> droneList =
            constructsMap.get(com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes.DRONE);
        if (droneList != null) {
            for (com.gy_mod.gy_trinket.core.entity.construct.ConstructData data : droneList) {
                if (this.getUUID().equals(data.getEntityUUID())) {
                    inPlayerConstructs = true;
                    break;
                }
            }
        }

        if (!inActiveEntities && !inPlayerConstructs) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (inActiveEntities && inPlayerConstructs) {
            return;
        }

        if (this.level().getServer() == null) {
            return;
        }

        net.minecraft.server.level.ServerPlayer ownerPlayer =
            this.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (ownerPlayer == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (!inActiveEntities) {
            cm.registerConstructEntity(ownerUUID, com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes.DRONE, this);
        }

        if (!inPlayerConstructs) {
            com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType currentArrayType =
                com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayManager.getInstance().getPlayerArrayType(ownerPlayer);
            if (currentArrayType == null) {
                currentArrayType = com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType.Types.ORBIT;
            }
            com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructData newData =
                new com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructData(
                    com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes.DRONE,
                    this.getUUID(),
                    this.getMaxHealth(),
                    currentArrayType);
            newData.setHealth(this.getHealth());
            newData.setHasAssaultModule(this.hasEffectTag(DroneEffectTag.ASSAULT));
            newData.setHasDefenseModule(this.hasEffectTag(DroneEffectTag.DEFENSE));
            cm.addConstruct(ownerPlayer, newData);
        }
    }

    /**
     * 带所有者和构造体数据的构造函数
     * @param level 世界
     * @param ownerUUID 所有者玩家UUID
     * @param droneConstruct 对应的无人机构造体数据
     */
    public DroneConstructEntity(Level level, java.util.UUID ownerUUID, DroneConstruct droneConstruct) {
        this(ModEntities.DRONE_CONSTRUCT.get(), level);
        this.ownerUUID = ownerUUID;
        this.droneConstruct = droneConstruct;
    }

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

    /**
     * 判断是否为环绕阵列
     * @return true 如果是环绕阵列
     */
    public boolean isOrbitArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 0;
    }

    /**
     * 判断是否为追击阵列
     * @return true 如果是追击阵列
     */
    public boolean isPursuitArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 1;
    }

    /**
     * 判断是否为待机阵列
     * @return true 如果是待机阵列
     */
    public boolean isStandbyArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 2;
    }

    /**
     * 判断是否为列队阵列
     * @return true 如果是列队阵列
     */
    public boolean isFormationArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 3;
    }

    public boolean isGuardArray() {
        int index = this.entityData.get(DATA_ARRAY_TYPE);
        return index == 4;
    }
    
    /**
     * 获取当前无人机的行为处理器
     * @return 对应的IDroneBehavior实现
     */
    public IDroneBehavior getBehavior() {
        if (this.droneConstruct != null) {
            return this.droneConstruct.getBehavior();
        }
        // 如果 droneConstruct 为 null，则根据阵列类型获取默认行为
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

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (isDefenseDrone()) {
            return net.minecraft.world.entity.EntityDimensions.scalable(1.0f, 5.0f);
        }
        return super.getDimensions(pose);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        if (isDefenseDrone() && isGuardArray()) {
            return isTemporarilyPickable;
        }
        return false;
    }

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
    
    private void applyAttributeModifiers() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMaxHealth);

        if (this.getHealth() > baseMaxHealth) {
            this.setHealth((float) baseMaxHealth);
        }

        refreshConstructAttributes();
    }

    public void refreshConstructAttributes() {
        if (this.ownerUUID == null) {
            return;
        }
        java.util.UUID playerUUID = this.ownerUUID;
        net.minecraft.server.level.ServerPlayer player = null;
        if (this.level().getServer() != null) {
            player = this.level().getServer().getPlayerList().getPlayer(playerUUID);
        }
        if (player != null) {
            com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier.applyAttributesToDrone(
                    playerUUID, this,
                    com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier.computeConstructAttributes(playerUUID)
            );
        }
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

    public double getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public void setBaseMaxHealth(double baseMaxHealth) {
        this.baseMaxHealth = baseMaxHealth;
    }

    public double getBaseAttackDamage() {
        return baseAttackDamage;
    }

    public void setBaseAttackDamage(double baseAttackDamage) {
        this.baseAttackDamage = baseAttackDamage;
    }

    public double getAttackSpeedMultiplier() {
        return attackSpeedMultiplier;
    }

    public void setAttackSpeedMultiplier(double multiplier) {
        this.attackSpeedMultiplier = multiplier;
    }

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

    public void facePositionWithInterpolation(Vec3 targetPos, float rotationSpeed) {
        Vec3 dronePos = this.position();

        double dx = targetPos.x - dronePos.x;
        double dy = targetPos.y - dronePos.y;
        double dz = targetPos.z - dronePos.z;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = -(float) (Math.atan2(dy, horizontalDistance) * (180.0 / Math.PI));

        float currentYaw = this.getYRot();

        float deltaYaw = targetYaw - currentYaw;
        while (deltaYaw > 180.0f) deltaYaw -= 360.0f;
        while (deltaYaw < -180.0f) deltaYaw += 360.0f;

        float yawStep = Math.min(Math.abs(deltaYaw), rotationSpeed);
        if (deltaYaw < 0) yawStep = -yawStep;
        float newYaw = currentYaw + yawStep;

        this.setYRot(newYaw);
        this.setXRot(targetPitch);
        this.setYHeadRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    public void explodeAndRemove() {
        if (this.level().isClientSide) return;

        float maxHealth = this.getMaxHealth();
        float speed = (float) this.explosionSpeed;
        float coefficient = Config.NEAR_DEATH_EXPLOSION_COEFFICIENT.get().floatValue();
        float damage = maxHealth * speed * coefficient;
        double radius = Config.NEAR_DEATH_EXPLOSION_RADIUS.get();

        Vec3 pos = this.position();

        Entity owner = this.getOwner();
        DamageSource damageSource;
        if (owner instanceof net.minecraft.world.entity.player.Player player) {
            damageSource = this.damageSources().explosion(this, player);
        } else {
            damageSource = this.damageSources().explosion(this, owner);
        }

        net.minecraft.world.entity.player.Player playerOwner = owner instanceof net.minecraft.world.entity.player.Player ? (net.minecraft.world.entity.player.Player) owner : null;

        AABB area = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> {
                    if (entity == this || !entity.isAlive()) return false;
                    if (entity instanceof net.minecraft.world.entity.player.Player) return false;
                    if (!(entity instanceof net.minecraft.world.entity.Mob)) return false;
                    return com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager.shouldAttackPlayer(entity, playerOwner);
                });

        for (LivingEntity target : entities) {
            if (target.distanceTo(this) <= radius) {
                target.hurt(damageSource, damage);
            }
        }

        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        removeFromConstructManager();
        this.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("owner", this.ownerUUID);
        }
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
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) {
            this.ownerUUID = tag.getUUID("owner");
        }
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
        applyAttributeModifiers();
        updateEffectData();
        this.refreshDimensions();
    }

    @Nullable
    public java.util.UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Nullable
    public Entity getOwner() {
        if (this.ownerUUID == null) return null;
        return this.level().getPlayerByUUID(this.ownerUUID);
    }

    @Override
    public void die(DamageSource source) {
        if (tryPreventDeathBehaviors(source)) {
            return;
        }
        triggerSpecialBehaviorsOnDeath(source);
        super.die(source);
        removeFromConstructManager();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player playerAttacker) {
            if (this.getOwnerUUID() != null && this.getOwnerUUID().equals(playerAttacker.getUUID())) {
                return false;
            }
        }

        boolean result = super.hurt(source, amount);
        if (result && this.getHealth() <= this.getMaxHealth() * 0.2f && this.getHealth() > 0) {
            triggerSpecialBehaviorsOnNearDeath(source);
        }
        if (result) {
            triggerSpecialBehaviorsOnDamageTaken(source, amount);
        }
        return result;
    }

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
        behaviors.sort(java.util.Comparator.comparingInt(IDroneSpecialBehavior::getPriority));
        for (IDroneSpecialBehavior behavior : behaviors) {
            if (behavior.tryPreventDeath(this, source)) {
                return true;
            }
        }
        return false;
    }

    private void removeFromConstructManager() {
        if (this.ownerUUID != null && !this.level().isClientSide) {
            CommanderManager.getInstance().onDroneRemoved(this);

            com.gy_mod.gy_trinket.core.entity.construct.ConstructManager manager = 
                com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance();
            manager.removeConstruct(this.ownerUUID, this.getUUID());
            manager.markConstructDead(this.ownerUUID, DroneConstructTypes.DRONE, this.getUUID());
            manager.unregisterConstructEntity(this.ownerUUID, DroneConstructTypes.DRONE, this.getUUID());
        }
    }

    public void setOwnerUUID(@Nullable java.util.UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
    }

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

        updateTemporarilyPickable();

        if (this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.98, 0.98, 0.98));
        }

        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            // 客户端生成飞行粒子效果
            if (this.level().isClientSide && this.random.nextFloat() < 0.1f) {
                addFlightParticles();
            }

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
                            // 无目标时朝向玩家方向
                            faceOwnerDirection((LivingEntity) owner);
                        }
                    }
                } else if (this.isFormationArray()) {
                    // 列队阵列：翼根无人机自由攻击并触发传递，非翼根无人机等待传递
                    List<LivingEntity> targets = behavior.searchTargets(this, (LivingEntity) owner, behavior.getAttackRange());
                    
                    if (this.getWingIndex() == 0) {
                        if (!targets.isEmpty() && this.attackCooldown <= 0) {
                            LivingEntity nearestTarget = targets.get(0);
                            boolean canAttack = this.distanceTo(nearestTarget) <= behavior.getAttackRange();

                            if (canAttack) {
                                faceFormationDirection((LivingEntity) owner, nearestTarget);
                                FormationBehavior.performBeamAttack(this, nearestTarget);
                                int formationCooldown = (int) (com.gy_mod.gy_trinket.Config.FORMATION_ATTACK_INTERVAL.get() * 20.0 / this.attackSpeedMultiplier);
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

    /**
     * 立即朝向目标（无插值）
     * @param target 要朝向的目标实体
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

    private void updateTemporarilyPickable() {
        isTemporarilyPickable = false;

        if (!isDefenseDrone() || !isGuardArray()) return;
        if (this.level().isClientSide) return;

        Entity ownerEntity = this.getOwner();
        if (!(ownerEntity instanceof Player owner)) return;

        Vec3 playerPos = owner.position();
        AABB searchArea = new AABB(
                playerPos.x - 5.0, playerPos.y - 5.0, playerPos.z - 5.0,
                playerPos.x + 5.0, playerPos.y + 5.0, playerPos.z + 5.0
        );

        List<net.minecraft.world.entity.projectile.Projectile> projectiles =
                this.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, searchArea);

        for (net.minecraft.world.entity.projectile.Projectile proj : projectiles) {
            Entity projOwner = proj.getOwner();
            if (projOwner instanceof Player) continue;
            if (projOwner instanceof DroneConstructEntity droneShooter
                    && droneShooter.getOwnerUUID() != null
                    && droneShooter.getOwnerUUID().equals(this.getOwnerUUID())) continue;
            if (projOwner == this) continue;

            if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                Vec3 velocity = arrow.getDeltaMovement();
                double speedSquared = velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z;
                if (speedSquared < 0.01) continue;
            }

            isTemporarilyPickable = true;
            break;
        }
    }

    /**
     * 使用固定插值朝向目标（用于追击阵列）
     * 偏航角每刻最多转动20度，俯仰角立即调整
     * @param target 要朝向的目标实体
     */
    private void faceTargetWithInterpolation(LivingEntity target) {
        facePositionWithInterpolation(target.position().add(0, target.getEyeHeight() * 0.5, 0), 20.0f);
    }

    /**
     * 朝向玩家的朝向方向（用于追击阵列无目标时）
     * @param owner 玩家实体
     */
    private void faceOwnerDirection(LivingEntity owner) {
        float ownerYaw = owner.getYRot();

        this.setYRot(ownerYaw);
        this.setYHeadRot(ownerYaw);
        this.yBodyRot = ownerYaw;
        this.yHeadRot = ownerYaw;
    }

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

    private void addFlightParticles() {
        Vec3 pos = this.position();
        Vec3 lookAngle = new Vec3(
            Mth.cos((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD),
            0.0,
            Mth.sin((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD)
        );

        Vec3 leftOffset = pos.add(-lookAngle.x * 0.3, -0.2, -lookAngle.z * 0.3);
        Vec3 rightOffset = pos.add(lookAngle.x * 0.3, -0.2, lookAngle.z * 0.3);

        this.level().addParticle(ParticleTypes.CLOUD, leftOffset.x, leftOffset.y, leftOffset.z,
            lookAngle.x * -0.1, 0, lookAngle.z * -0.1);
        this.level().addParticle(ParticleTypes.CLOUD, rightOffset.x, rightOffset.y, rightOffset.z,
            lookAngle.x * -0.1, 0, lookAngle.z * -0.1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 5.0)
            .add(Attributes.FOLLOW_RANGE, 16.0)
            .add(Attributes.ATTACK_DAMAGE, 0.3);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 禁用动画播放，直到所有无人机都有完整的动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }

    // 列队阵列相关方法
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
