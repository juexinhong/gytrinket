package com.gytrinket.gytrinket.core.entity.construct;

import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.TargetMemory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 构造体实体抽象基类
 * <p>
 * 提取无人机构造体、僚机构造体、蜂群构造体的公共逻辑：
 * <ul>
 *   <li>归属者 UUID 管理（字段 + 同步数据 + getOwner）</li>
 *   <li>攻击冷却计数</li>
 *   <li>基础属性字段（maxHealth / attackDamage / attackSpeedMultiplier）</li>
 *   <li>朝向插值控制（facePositionWithInterpolation 等）</li>
 *   <li>友方构造体判定（isOwnConstruct）</li>
 *   <li>飞行粒子（addFlightParticles）</li>
 *   <li>归属者攻击穿透（hurt）</li>
 *   <li>死亡清理（die + removeFromConstructManager 模板方法）</li>
 *   <li>管理器注册检查（checkManagerRegistration 模板方法）</li>
 *   <li>NBT 公共字段序列化（owner + health_ratio + 钩子）</li>
 *   <li>属性刷新（refreshConstructAttributes 模板方法）</li>
 *   <li>GeckoLib 动画缓存</li>
 * </ul>
 * <p>
 * 子类需实现：
 * <ul>
 *   <li>{@link #getConstructTypeId()} - 返回构造体类型 ID</li>
 *   <li>{@link #createConstructDataForRegistration(ServerPlayer)} - 创建注册用的构造体数据</li>
 *   <li>{@link #applyConstructAttributes(UUID, Map)} - 调用类型特定的属性应用方法</li>
 * </ul>
 */
public abstract class AbstractConstructEntity extends PathfinderMob implements GeoEntity, IConstructEntity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(AbstractConstructEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    /** 归属者玩家 UUID */
    @Nullable
    protected UUID ownerUUID;

    /** 攻击冷却（tick） */
    protected int attackCooldown = 0;

    /** 基础最大生命值（不含属性修饰器） */
    protected double baseMaxHealth;

    /** 基础攻击伤害（不含属性修饰器） */
    protected double baseAttackDamage;

    /** 攻速倍率（来自 construct_attack_speed 属性），默认 1.0 */
    protected double attackSpeedMultiplier = 1.0;

    // ===== 索敌通用参数与状态 =====
    /** 玩家最大索敌距离限制：不可选择玩家此范围外的敌人 */
    protected static final float PLAYER_MAX_TARGET_RANGE = 35.0f;
    /** 目标记忆持续时间（tick），3秒=60tick */
    protected static final long TARGET_MEMORY_DURATION = 60L;
    /** 目标记忆表：实体UUID -> 记忆条目，避免视野内无目标时立即丢失追击 */
    protected final Map<UUID, TargetMemory> targetMemoryMap = new HashMap<>();

    protected AbstractConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID, Optional.empty());
    }

    // ===== 归属者管理 =====

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        if (this.ownerUUID != null) return this.ownerUUID;
        // 客户端回退：从同步数据读取
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public Entity getOwner() {
        UUID uuid = this.getOwnerUUID();
        if (uuid == null) return null;
        return this.level().getPlayerByUUID(uuid);
    }

    // ===== 攻击冷却 =====

    public int getAttackCooldown() {
        return this.attackCooldown;
    }

    public void setAttackCooldown(int cooldown) {
        this.attackCooldown = cooldown;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
    }

    // ===== 碰撞规则 =====

    /**
     * 构造体无刚体碰撞（canBeCollidedWith=false），确保不会阻挡实体移动。
     * isPickable 由子类决定（无人机/蜂群=true 可被弹射物命中，僚机=false）。
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ===== 基础属性 =====

    @Override
    public double getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public void setBaseMaxHealth(double baseMaxHealth) {
        this.baseMaxHealth = baseMaxHealth;
    }

    @Override
    public double getBaseAttackDamage() {
        return baseAttackDamage;
    }

    public void setBaseAttackDamage(double baseAttackDamage) {
        this.baseAttackDamage = baseAttackDamage;
    }

    @Override
    public double getAttackSpeedMultiplier() {
        return attackSpeedMultiplier;
    }

    @Override
    public void setAttackSpeedMultiplier(double multiplier) {
        this.attackSpeedMultiplier = multiplier;
    }

    // ===== 朝向控制 =====

    /**
     * 使用插值朝向指定位置（偏航角每刻最多转动 rotationSpeed 度，俯仰角立即调整）
     */
    public void facePositionWithInterpolation(Vec3 targetPos, float rotationSpeed) {
        Vec3 pos = this.position();

        double dx = targetPos.x - pos.x;
        double dy = targetPos.y - pos.y;
        double dz = targetPos.z - pos.z;

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

    /**
     * 使用固定插值朝向目标实体（偏航角每刻最多转动20度）
     */
    public void faceTargetWithInterpolation(LivingEntity target) {
        facePositionWithInterpolation(target.position().add(0, target.getEyeHeight() * 0.5, 0), 20.0f);
    }

    /**
     * 朝向玩家的朝向方向（用于追击阵列无目标时）
     */
    public void faceOwnerDirection(LivingEntity owner) {
        float ownerYaw = owner.getYRot();
        this.setYRot(ownerYaw);
        this.setYHeadRot(ownerYaw);
        this.yBodyRot = ownerYaw;
        this.yHeadRot = ownerYaw;
    }

    // ===== 友方构造体判定 =====

    /**
     * 判断实体是否为归属玩家的构造体（基于 IConstructEntity 接口统一判断，避免友伤）
     */
    protected boolean isOwnConstruct(LivingEntity entity, UUID ownerUUID) {
        if (entity instanceof IConstructEntity constructEntity) {
            UUID entOwner = constructEntity.getOwnerUUID();
            return entOwner != null && entOwner.equals(ownerUUID);
        }
        return false;
    }

    // ===== 索敌模板方法 =====

    /**
     * 通用索敌流程：在自身周围 searchRange 内查找合法目标，命中则更新目标记忆；
     * 未命中时尝试沿用未过期的记忆目标（仍需满足玩家距离限制）。
     * <p>
     * 子类提供 {@param isValidTarget} 谓词实现类型特定的过滤规则
     * （如排除傀儡、玩家保护实体、玩家自己的构造体等）。
     *
     * @param owner         归属者
     * @param searchRange   搜索半径（格）
     * @param isValidTarget 目标合法性谓词
     * @return 选定的攻击目标，无则 null
     */
    protected LivingEntity findTarget(LivingEntity owner, float searchRange,
                                      Predicate<LivingEntity> isValidTarget) {
        Level level = this.level();
        Vec3 pos = this.position();
        long currentTick = level.getGameTime();
        UUID myUUID = this.getUUID();

        AABB searchBox = new AABB(
            pos.x - searchRange, pos.y - searchRange, pos.z - searchRange,
            pos.x + searchRange, pos.y + searchRange, pos.z + searchRange
        );

        List<LivingEntity> allTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                isValidTarget::test);

        if (!allTargets.isEmpty()) {
            LivingEntity newTarget = allTargets.stream()
                    .min(Comparator.comparingDouble(t -> pos.distanceTo(t.position())))
                    .orElse(null);

            if (newTarget != null) {
                TargetMemory existing = targetMemoryMap.get(myUUID);
                if (existing != null && existing.target == newTarget) {
                    existing.endTick = currentTick + TARGET_MEMORY_DURATION;
                } else {
                    targetMemoryMap.put(myUUID, new TargetMemory(newTarget, currentTick + TARGET_MEMORY_DURATION));
                }
                return newTarget;
            }
        }

        TargetMemory memory = targetMemoryMap.get(myUUID);
        if (memory != null) {
            if (memory.endTick > currentTick && memory.target.isAlive()) {
                if (memory.target.distanceTo(owner) <= PLAYER_MAX_TARGET_RANGE) {
                    return memory.target;
                }
            }
            targetMemoryMap.remove(myUUID);
        }

        return null;
    }

    // ===== 飞行粒子 =====

    protected void addFlightParticles() {
        Vec3 pos = this.position();
        Vec3 lookAngle = new Vec3(
            Mth.cos((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD),
            0.0,
            Mth.sin((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD)
        );

        Vec3 leftOffset = pos.add(-lookAngle.x * 0.3, -0.2, -lookAngle.z * 0.3);
        Vec3 rightOffset = pos.add(lookAngle.x * 0.3, -0.2, lookAngle.z * 0.3);

        this.level().addParticle(ParticleTypes.SMOKE, leftOffset.x, leftOffset.y, leftOffset.z,
             0.0, 0.0, 0.0);
        this.level().addParticle(ParticleTypes.SMOKE, rightOffset.x, rightOffset.y, rightOffset.z,
             0.0, 0.0, 0.0);
    }

    // ===== 伤害和死亡 =====

    /**
     * 归属者攻击穿透自身构造体（不阻挡），其他伤害正常结算。
     * 子类可重写以添加额外逻辑（如无人机的特殊行为触发），但应调用 super.hurt。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player playerAttacker) {
            if (this.getOwnerUUID() != null && this.getOwnerUUID().equals(playerAttacker.getUUID())) {
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    /**
     * 死亡时清理构造体管理器注册。
     * 子类可重写以添加额外逻辑（如无人机的特殊行为触发），但应调用 super.die。
     */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        removeFromConstructManager();
    }

    /**
     * 从构造体管理器中移除本实体。
     * 子类可通过重写 {@link #onRemoveFromConstructManager()} 添加类型特定的清理逻辑。
     */
    protected void removeFromConstructManager() {
        if (this.ownerUUID != null && !this.level().isClientSide) {
            ConstructManager manager = ConstructManager.getInstance();
            manager.removeConstruct(this.ownerUUID, this.getUUID());
            manager.markConstructDead(this.ownerUUID, getConstructTypeId(), this.getUUID());
            manager.unregisterConstructEntity(this.ownerUUID, getConstructTypeId(), this.getUUID());
            onRemoveFromConstructManager();
        }
    }

    /** 死亡/移除时的类型特定清理钩子 */
    protected void onRemoveFromConstructManager() {}

    // ===== 管理器注册检查 =====

    /**
     * 定期检查实体是否已在构造体管理器中注册。
     * 若既不在活跃实体表也不在玩家构造体表中，则移除实体。
     * 若仅缺失一方，则补全注册。
     */
    protected void checkManagerRegistration() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        ConstructManager cm = ConstructManager.getInstance();

        Map<UUID, Entity> activeEntities = cm.getActiveConstructEntities(ownerUUID, getConstructTypeId());
        boolean inActiveEntities = activeEntities != null && activeEntities.containsKey(this.getUUID());

        boolean inPlayerConstructs = false;
        Map<String, List<ConstructData>> constructsMap = cm.getPlayerConstructs(ownerUUID);
        List<ConstructData> list = constructsMap.get(getConstructTypeId());
        if (list != null) {
            for (ConstructData data : list) {
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

        ServerPlayer ownerPlayer = this.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (ownerPlayer == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (!inActiveEntities) {
            cm.registerConstructEntity(ownerUUID, getConstructTypeId(), this);
        }

        if (!inPlayerConstructs) {
            ConstructData newData = createConstructDataForRegistration(ownerPlayer);
            float maxH = this.getMaxHealth();
            newData.setHealthRatio(maxH > 0 ? this.getHealth() / maxH : 1.0);
            cm.addConstruct(ownerPlayer, newData);
        }
    }

    // ===== NBT 序列化 =====

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("owner", this.ownerUUID);
        }
        addTypeSpecificSaveData(tag);
        // 保存生命值比例，避免读取时因 maxHealth 未应用修饰符导致 health 被截断
        float maxH = this.getMaxHealth();
        tag.putFloat("health_ratio", maxH > 0 ? this.getHealth() / maxH : 1.0f);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) {
            this.ownerUUID = tag.getUUID("owner");
            this.entityData.set(DATA_OWNER_UUID, Optional.of(this.ownerUUID));
        }
        readTypeSpecificSaveData(tag);
        applyAttributeModifiers();
        onAttributesApplied();

        // 属性修饰符应用后，用保存的生命值比例恢复当前生命值
        // super.readAdditionalSaveData 中 setHealth 会被 maxHealth（此时仅基础值）截断，此处用正确比例覆盖
        if (tag.contains("health_ratio")) {
            float healthRatio = tag.getFloat("health_ratio");
            this.setHealth(this.getMaxHealth() * healthRatio);
        }
    }

    /** 子类保存类型特定字段到 NBT 的钩子 */
    protected void addTypeSpecificSaveData(CompoundTag tag) {}

    /** 子类从 NBT 读取类型特定字段的钩子 */
    protected void readTypeSpecificSaveData(CompoundTag tag) {}

    /** 属性修饰器应用完毕后的钩子（如无人机更新效果数据和刷新尺寸） */
    protected void onAttributesApplied() {}

    // ===== 属性应用 =====

    /**
     * 刷新构造体属性：应用 construct_* 属性系统提供的加成。
     * 子类通过实现 {@link #applyConstructAttributes} 调用类型特定的应用方法。
     */
    @Override
    public void refreshConstructAttributes() {
        if (this.ownerUUID == null) return;
        UUID playerUUID = this.ownerUUID;
        ServerPlayer player = null;
        if (this.level().getServer() != null) {
            player = this.level().getServer().getPlayerList().getPlayer(playerUUID);
        }
        if (player != null) {
            applyConstructAttributes(playerUUID, ConstructAttributeApplier.computeConstructAttributes(playerUUID));
        }
    }

    /**
     * 应用属性修饰器：设置基础生命值并刷新构造体属性。
     * 子类可重写以添加额外逻辑（如蜂群的等阶倍率）。
     */
    protected void applyAttributeModifiers() {
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMaxHealth);
        }
        refreshConstructAttributes();
    }

    /** 调用类型特定的属性应用方法（applyAttributesToDrone/Wingman/Swarm） */
    protected abstract void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes);

    // ===== 抽象方法 =====

    /** 返回构造体类型 ID（如 "drone"/"wingman"/"swarm"） */
    protected abstract String getConstructTypeId();

    /** 创建注册到构造体管理器用的数据对象（不含 healthRatio，由基类设置） */
    protected abstract ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer);

    // ===== GeckoLib =====

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
