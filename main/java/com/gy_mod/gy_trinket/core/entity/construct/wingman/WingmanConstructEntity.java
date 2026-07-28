package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.BoidConfig;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.BoidHelper;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode.InterceptorAttackModeManager;
import com.gy_mod.gy_trinket.core.entity.construct.HostileTargetManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 僚机构造体实体类
 * <p>
 * 高阶其他构造体，行为接近无人机追击阵列。
 * 攻击时发射多枚爆破弹，爆破弹命中造成伤害并在销毁时产生模拟爆炸。
 * 无阵列系统，常驻无物理效果。
 */
public class WingmanConstructEntity extends AbstractConstructEntity {

    private WingmanConstruct wingmanConstruct;

    // ===== 追击行为参数 =====
    private static final float SEARCH_RANGE = 20.0f;
    private static final float MOVE_SPEED = 0.45f;
    private static final float LEAVE_SPEED = 0.2f;
    private static final float HEIGHT_ADJUST_SPEED = 0.2f;
    private static final float STANDBY_HEIGHT = 4.0f;
    private static final float STANDBY_RANGE = 7.0f;

    // Boid参数（僚机较松散）
    private static final BoidConfig BOID_CONFIG = new BoidConfig(
            2.0,  // comfortRange
            3.0,  // separationRange
            0.06, // separationStrength
            8.0,  // cohesionRange
            0.015,// cohesionStrength
            5.0,  // alignmentRange
            0.02  // alignmentStrength
    );
    private static final double VELOCITY_DAMPING = 0.8;

    /** 发射点位横向间距 */
    private static final float LAUNCH_SPACING = 0.07F;

    /** 每层额外后移距离 */
    private static final float TIER_BACK_OFFSET = 0.2F;

    // ===== 客户端同步数据 =====
    static final EntityDataAccessor<Boolean> DATA_INTERCEPTOR_MODE =
            SynchedEntityData.defineId(WingmanConstructEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<ItemStack> DATA_INTERCEPTOR_WEAPON =
            SynchedEntityData.defineId(WingmanConstructEntity.class, EntityDataSerializers.ITEM_STACK);
    static final EntityDataAccessor<String> DATA_INTERCEPTOR_ATTACK_MODE =
            SynchedEntityData.defineId(WingmanConstructEntity.class, EntityDataSerializers.STRING);
    /** 近战攻击动画剩余刻数（0=无动画） */
    static final EntityDataAccessor<Integer> DATA_MELEE_ANIM_TICKS =
            SynchedEntityData.defineId(WingmanConstructEntity.class, EntityDataSerializers.INT);

    // ===== 拦截机状态 =====
    /** 上一次同步到客户端的有效武器（用于检测玩家手持物品变化） */
    private ItemStack lastSyncedEffectiveWeapon = ItemStack.EMPTY;

    /** 武器攻击独立冷却（per-entity，因为每个实体独立攻击） */
    private int weaponAttackCooldown = 0;

    /** 上一次追踪的目标（用于检测目标变更，通知攻击模式管理器） */
    private LivingEntity lastTrackedTarget = null;

    /** 获取当前追踪的目标（供攻击模式使用，因为僚机不使用原版 setTarget()） */
    public LivingEntity getLastTrackedTarget() {
        return lastTrackedTarget;
    }

    /** 连击锁定标记：点射连发期间临时提升reach+3 */
    private static final String BURST_LOCK_KEY = "InterceptorBurstLock";

    /** 近战攻击动画持续刻数 */
    public static final int MELEE_ANIM_DURATION = 10;

    // ===== 近战冲锋状态 =====
    /** 是否正在近战冲锋 */
    private boolean meleeCharging = false;
    /** 上一次武器攻击总冷却（用于判断80%阈值触发冲锋） */
    private int lastWeaponCooldownTotal = 0;
    /** 近战冲锋加速度（格/刻²） */
    private static final double MELEE_CHARGE_ACCELERATION = 0.1;
    /** 近战冲锋最大转向速度（度/刻） */
    private static final float MELEE_CHARGE_TURN_SPEED = 15.0f;

    /** 设置连击锁定状态 */
    public void setBurstLock(boolean locked) {
        this.getPersistentData().putBoolean(BURST_LOCK_KEY, locked);
    }

    /** 获取连击锁定状态 */
    public boolean isBurstLocked() {
        return this.getPersistentData().getBoolean(BURST_LOCK_KEY);
    }

    /** 供 Strategy 设置武器攻击冷却 */
    public void setWeaponAttackCooldown(int cooldown) {
        this.weaponAttackCooldown = cooldown;
    }

    /** 触发近战攻击动画（服务端调用，同步到客户端渲染） */
    public void startMeleeAttackAnimation() {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_MELEE_ANIM_TICKS, MELEE_ANIM_DURATION);
        }
    }

    /** 是否正在近战冲锋 */
    public boolean isMeleeCharging() {
        return meleeCharging;
    }

    /** 结束近战冲锋 */
    public void endMeleeCharge() {
        this.meleeCharging = false;
    }

    /** 供 Strategy 获取拦截机武器（从Manager读取） */
    public ItemStack getInterceptorWeaponForStrategy() {
        return getInterceptorWeaponFromManager();
    }

    public WingmanConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.baseMaxHealth = Config.getWingmanBaseHealth();
        this.baseAttackDamage = Config.getWingmanExplosiveDamage();
    }

    public WingmanConstructEntity(Level level, UUID ownerUUID, WingmanConstruct wingmanConstruct) {
        this(ModEntities.WINGMAN_CONSTRUCT.get(), level);
        setOwnerUUID(ownerUUID);
        this.wingmanConstruct = wingmanConstruct;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_INTERCEPTOR_MODE, false);
        this.entityData.define(DATA_INTERCEPTOR_WEAPON, ItemStack.EMPTY);
        this.entityData.define(DATA_INTERCEPTOR_ATTACK_MODE, InterceptorAttackMode.MELEE.getSerializedName());
        this.entityData.define(DATA_MELEE_ANIM_TICKS, 0);
    }

    // ===== 拦截机统一查询方法（从Manager读取，所有僚机共享同一份数据） =====

    /** 查询拦截机模式（从WingmanManager缓存读取，事件驱动更新） */
    private boolean getInterceptorMode() {
        Entity owner = this.getOwner();
        if (owner instanceof Player player) {
            return WingmanManager.getInstance().hasInterceptorModule(player);
        }
        return false;
    }

    /** 查询拦截机武器（从InterceptorWeaponManager读取） */
    private ItemStack getInterceptorWeaponFromManager() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID != null) {
            return InterceptorWeaponManager.getWeapon(ownerUUID);
        }
        return ItemStack.EMPTY;
    }

    /** 查询拦截机攻击模式（从InterceptorWeaponManager读取） */
    private InterceptorAttackMode getInterceptorAttackModeFromManager() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID != null) {
            return InterceptorWeaponManager.getAttackMode(ownerUUID);
        }
        return InterceptorAttackMode.MELEE;
    }

    /**
     * 获取拦截机使用的有效武器。
     * 优先使用拦截机专属武器（Manager），其次使用玩家主手物品。
     */
    private ItemStack getEffectiveWeapon(Player player) {
        ItemStack weapon = getInterceptorWeaponFromManager();
        if (!weapon.isEmpty()) return weapon;
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) return mainHand;
        return ItemStack.EMPTY;
    }

    /**
     * 同步拦截机数据到客户端（从Manager统一查询，所有僚机共享同一份数据）
     */
    private void syncInterceptorDataToClient() {
        if (!this.level().isClientSide) {
            boolean mode = getInterceptorMode();
            this.entityData.set(DATA_INTERCEPTOR_MODE, mode);
            // 同步有效武器（显式设置 > 玩家手持），客户端用于渲染
            ItemStack effectiveWeapon = getInterceptorWeaponFromManager();
            if (effectiveWeapon.isEmpty() && mode) {
                Entity owner = this.getOwner();
                if (owner instanceof Player player) {
                    effectiveWeapon = player.getMainHandItem();
                }
            }
            this.entityData.set(DATA_INTERCEPTOR_WEAPON, effectiveWeapon);
            this.lastSyncedEffectiveWeapon = effectiveWeapon.copy();
            this.entityData.set(DATA_INTERCEPTOR_ATTACK_MODE, getInterceptorAttackModeFromManager().getSerializedName());

            // 更新实体 ATTACK_DAMAGE 属性以匹配当前武器
            updateAttackDamageAttribute(effectiveWeapon);
        }
    }

    /**
     * 根据武器更新实体的 ATTACK_DAMAGE 属性
     * 基础值为1.0（玩家基础攻击伤害），加上武器的 ATTACK_DAMAGE 修饰符
     */
    private void updateAttackDamageAttribute(ItemStack weapon) {
        var attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) return;

        // 移除之前的武器修饰符
        attr.removeModifier(WEAPON_DAMAGE_MODIFIER_UUID);

        if (!weapon.isEmpty()) {
            var modifiers = weapon.getAttributeModifiers(EquipmentSlot.MAINHAND);
            if (modifiers.containsKey(Attributes.ATTACK_DAMAGE)) {
                for (var modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
                    attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            WEAPON_DAMAGE_MODIFIER_UUID,
                            WEAPON_DAMAGE_MODIFIER_NAME,
                            modifier.getAmount(),
                            modifier.getOperation()
                    ));
                    break; // 只取第一个
                }
            }
        }
    }

    /** 武器伤害修饰符的UUID，用于在武器切换时移除旧修饰符 */
    private static final java.util.UUID WEAPON_DAMAGE_MODIFIER_UUID = java.util.UUID.fromString("d2b6a4e8-7c3f-4a91-b5e8-9f1d3c7a2b4e");
    private static final String WEAPON_DAMAGE_MODIFIER_NAME = "gytrinket:interceptor_weapon_damage";

    /**
     * 外部调用：刷新拦截机数据到客户端（Manager数据变化后调用）
     */
    public void refreshInterceptorData() {
        syncInterceptorDataToClient();
    }

    public WingmanConstruct getWingmanConstruct() {
        return wingmanConstruct;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.tickCount > 100 && this.tickCount % 20 == 0) {
            checkManagerRegistration();
        }

        // 拦截机模式下，检测玩家手持物品变化并同步到客户端（仅当未显式设置武器时）
        if (!this.level().isClientSide && getInterceptorMode() && getInterceptorWeaponFromManager().isEmpty()) {
            Entity owner = this.getOwner();
            if (owner instanceof Player player) {
                ItemStack currentMainHand = player.getMainHandItem();
                if (!ItemStack.matches(this.lastSyncedEffectiveWeapon, currentMainHand)) {
                    this.entityData.set(DATA_INTERCEPTOR_WEAPON, currentMainHand);
                    this.lastSyncedEffectiveWeapon = currentMainHand.copy();
                }
            }
        }

        // 武器攻击独立冷却递减
        if (this.weaponAttackCooldown > 0) {
            this.weaponAttackCooldown--;
        }

        // 近战攻击动画计时器递减（服务端递减，自动同步到客户端）
        if (!this.level().isClientSide) {
            int animTicks = this.entityData.get(DATA_MELEE_ANIM_TICKS);
            if (animTicks > 0) {
                this.entityData.set(DATA_MELEE_ANIM_TICKS, animTicks - 1);
            }
        }

        if (this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.98, 0.98, 0.98));
        }

        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.isAlive()) {
            // 拦截机攻击模式管理
            if (!this.level().isClientSide && getInterceptorMode() && owner instanceof Player player) {
                InterceptorAttackModeManager.tick(this, player);
            }

            // 搜索目标
            LivingEntity target = findTarget((LivingEntity) owner);

            // 检测目标变更，通知攻击模式管理器
            if (!this.level().isClientSide && getInterceptorMode()) {
                if (target != lastTrackedTarget) {
                    if (owner instanceof Player player) {
                        InterceptorAttackModeManager.onTargetChanged(this, lastTrackedTarget, target);
                    }
                    lastTrackedTarget = target;
                }
            }

            if (target != null) {
                // 近战冲锋逻辑（覆盖正常追击移动）
                boolean isMeleeMode = getInterceptorMode() && getInterceptorAttackModeFromManager() == InterceptorAttackMode.MELEE;
                if (!this.level().isClientSide && isMeleeMode && shouldStartMeleeCharge()) {
                    meleeCharging = true;
                }

                if (this.meleeCharging && isMeleeMode) {
                    // 冲锋朝向：先更新朝向（限速15度/刻转向目标7/10身高），再基于新朝向计算移动
                    faceMeleeChargeTarget(target);
                    // 冲锋移动：沿新朝向方向加速
                    meleeChargeMovement(this, target);
                    // 目标消失则结束冲锋
                    if (!target.isAlive()) {
                        endMeleeCharge();
                    }
                } else {
                    if (this.meleeCharging) {
                        endMeleeCharge();
                    }
                    // 正常追击模式
                    pursuitMovement(this, (LivingEntity) owner, target);
                    // 朝向目标（插值旋转）
                    faceTargetWithInterpolation(target);
                }

                // 攻击
                executeAttack(this, (LivingEntity) owner, target);
            } else {
                // 无目标时结束冲锋
                if (this.meleeCharging) {
                    endMeleeCharge();
                }
                // 待机模式
                standbyMovement(this, (LivingEntity) owner);

                // 朝向玩家方向
                faceOwnerDirection((LivingEntity) owner);
            }
        }
    }

    // ===== 追击行为逻辑 =====

    /**
     * 判断是否应开始近战冲锋。
     * 条件：近战模式 + 冷却已完成80%以上 + 当前未在冲锋中
     */
    private boolean shouldStartMeleeCharge() {
        if (this.meleeCharging) return false;
        if (this.lastWeaponCooldownTotal <= 0) return false;
        // 冷却剩余 <= 20%总冷却 = 已完成80%
        return this.weaponAttackCooldown <= this.lastWeaponCooldownTotal * 0.2;
    }

    /**
     * 近战冲锋移动：速度方向始终跟随自身朝向，加速度0.05格/刻。
     * 每刻在朝向方向上增加0.05格/刻的速度，模拟逐渐加速冲刺。
     */
    private void meleeChargeMovement(Entity wingman, LivingEntity target) {
        // 获取当前朝向方向
        float yaw = wingman.getYRot() * (float) Math.PI / 180.0f;
        float pitch = wingman.getXRot() * (float) Math.PI / 180.0f;
        double dirX = -Math.sin(yaw) * Math.cos(pitch);
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * Math.cos(pitch);

        Vec3 currentMovement = wingman.getDeltaMovement();
        // 在朝向方向上加速
        Vec3 newMovement = currentMovement.add(
                dirX * MELEE_CHARGE_ACCELERATION,
                dirY * MELEE_CHARGE_ACCELERATION,
                dirZ * MELEE_CHARGE_ACCELERATION);

        // 速度上限
        double maxSpeed = MOVE_SPEED * 4.0;
        if (newMovement.length() > maxSpeed) {
            newMovement = newMovement.normalize().scale(maxSpeed);
        }

        wingman.setDeltaMovement(newMovement.scale(VELOCITY_DAMPING));
    }

    /**
     * 近战冲锋朝向：朝目标7/10身高处转向，限速15度/刻。
     */
    private void faceMeleeChargeTarget(LivingEntity target) {
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.7, 0);
        facePositionWithInterpolation(targetPos, MELEE_CHARGE_TURN_SPEED);
    }

    private LivingEntity findTarget(LivingEntity owner) {
        Player player = owner instanceof Player p ? p : null;
        return findTarget(owner, SEARCH_RANGE, entity -> {
            if (entity == owner || entity == this) return false;
            if (!entity.isAlive()) return false;
            if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
            if (isOwnConstruct(entity, owner.getUUID())) return false;
            if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
            return entity.distanceTo(owner) <= PLAYER_MAX_TARGET_RANGE;
        });
    }

    private void pursuitMovement(Entity wingman, LivingEntity owner, LivingEntity target) {
        Vec3 pos = wingman.position();
        Vec3 targetPos = target.position();

        double dx = pos.x - targetPos.x;
        double dz = pos.z - targetPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // 根据攻击模式确定追击距离
        double idealDistMin, idealDistMax, farDist;
        if (getInterceptorMode()) {
            com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode.InterceptorWeaponMode weaponMode =
                    com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode.InterceptorAttackModeManager.getCurrentWeaponMode(this);
            double[] range = weaponMode != null
                    ? weaponMode.getIdealDistanceRange(this)
                    : new double[]{6.0, 7.0, 8.0};
            idealDistMin = range[0];
            idealDistMax = range[1];
            farDist = range[2];
        } else {
            // 正常僚机：保持6-8格
            idealDistMin = 6.0;
            idealDistMax = 7.0;
            farDist = 8.0;
        }

        double speed = 0;
        Vec3 direction = Vec3.ZERO;
        float yaw = wingman.getYRot() * (float) Math.PI / 180.0f;

        if (horizontalDist > farDist) {
            float excessDistance = (float) (horizontalDist - farDist);
            float speedMultiplier = 1.0f + excessDistance * 0.10f;
            speed = MOVE_SPEED * speedMultiplier;
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        } else if (horizontalDist > idealDistMax) {
            speed = LEAVE_SPEED;
            Vec3 toTarget = targetPos.subtract(pos).normalize();
            direction = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        } else if (horizontalDist > idealDistMin) {
            speed = 0;
            direction = Vec3.ZERO;
        } else {
            speed = LEAVE_SPEED;
            Vec3 awayDirection = pos.subtract(targetPos).normalize();
            direction = new Vec3(awayDirection.x, 0, awayDirection.z).normalize();
        }

        double targetHeightMin = targetPos.y + target.getBbHeight() * 0.8;
        double targetHeightMax = targetPos.y + target.getBbHeight() * 1.0;

        Vec3 verticalDirection = Vec3.ZERO;

        if (pos.y >= targetHeightMin && pos.y <= targetHeightMax) {
            // 在目标高度范围内
        } else if (pos.y > targetHeightMax) {
            double heightDiff = pos.y - targetHeightMax;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, -HEIGHT_ADJUST_SPEED * speedFactor, 0);
        } else if (pos.y < targetHeightMin) {
            double heightDiff = targetHeightMin - pos.y;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, HEIGHT_ADJUST_SPEED * speedFactor, 0);
        }

        Vec3 finalMovement = direction.scale(speed);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }

        applyBoidAndSetMovement(wingman, owner, finalMovement);
    }

    private void standbyMovement(Entity wingman, LivingEntity owner) {
        Vec3 pos = wingman.position();
        Vec3 ownerPos = owner.position();

        Vec3 standbyTarget = ownerPos.add(0, STANDBY_HEIGHT, 0);

        double dx = pos.x - ownerPos.x;
        double dz = pos.z - ownerPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist > 20.0) {
            wingman.teleportTo(ownerPos.x, ownerPos.y + STANDBY_HEIGHT, ownerPos.z);
            return;
        }

        Vec3 toOwner = new Vec3(ownerPos.x - pos.x, 0, ownerPos.z - pos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 0.001 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 verticalDir = Vec3.ZERO;
        double heightDiff = standbyTarget.y - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = HEIGHT_ADJUST_SPEED * (1.0 + Math.abs(heightDiff) * 0.5);
            verticalDir = new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0);
        }

        Vec3 finalMovement = Vec3.ZERO;

        if (horizontalDist > STANDBY_RANGE) {
            double speedBoost = 1.0 + (horizontalDist - STANDBY_RANGE) * 0.2;
            finalMovement = horizontalDir.scale(MOVE_SPEED * speedBoost);
        } else if (horizontalDist > 3.0) {
            finalMovement = horizontalDir.scale(LEAVE_SPEED);
        }

        if (verticalDir != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDir);
        }

        applyBoidAndSetMovement(wingman, owner, finalMovement);
    }

    /**
     * 应用boid力、速度限制和阻尼，然后设置移动向量
     */
    private void applyBoidAndSetMovement(Entity wingman, LivingEntity owner, Vec3 movement) {
        Vec3 boidForce = BoidHelper.calculateBoidForce(wingman, owner, WingmanConstructEntity.class, BOID_CONFIG);
        Vec3 finalMovement = movement.add(boidForce);

        double maxSpeed = MOVE_SPEED * 2.0;
        if (finalMovement.length() > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        wingman.setDeltaMovement(finalMovement.scale(VELOCITY_DAMPING));
    }

    // ===== 攻击逻辑 =====

    private void executeAttack(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (this.level().isClientSide) return;

        double distance = wingman.distanceTo(target);
        float attackRange = Config.getWingmanAttackRange().floatValue();
        if (distance > attackRange) return;

        // 爆破弹攻击（始终可用，独立冷却）
        executeExplosiveAttack(wingman, owner, target);

        // 拦截机模式：额外执行武器攻击（独立冷却）
        if (getInterceptorMode() && owner instanceof Player player) {
            executeInterceptorWeaponAttack(player, target);
        }
    }

    /**
     * 爆破弹攻击（僚机常规攻击，正常攻击速度）
     */
    private void executeExplosiveAttack(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (this.attackCooldown > 0) return;

        fireExplosiveProjectiles(this, owner, target);

        float attackInterval = (float) Config.getWingmanAttackInterval();
        int cooldown = (int) (attackInterval * 20.0f / this.attackSpeedMultiplier);
        this.attackCooldown = Math.max(1, cooldown);
    }

    /**
     * 拦截机武器攻击逻辑（独立于爆破弹冷却）
     * 拦截机自己使用武器攻击，不操控玩家
     */
    private void executeInterceptorWeaponAttack(Player player, LivingEntity target) {
        if (this.weaponAttackCooldown > 0) {
            return;
        }

        // 获取有效武器
        ItemStack weapon = getEffectiveWeapon(player);
        if (weapon.isEmpty()) {
            return;
        }

        // 模块模式：攻击前检查（充能/点射可能阻止攻击）
        if (!InterceptorAttackModeManager.onPreAttack(this, target, weapon, player)) {
            // 攻击被模块模式阻止，设置1tick冷却防止每tick重试
            this.weaponAttackCooldown = 1;
            return;
        }

        // 近战冲锋中：检查目标是否在近战范围内
        boolean isMeleeMode = getInterceptorAttackModeFromManager() == InterceptorAttackMode.MELEE;
        if (isMeleeMode && this.meleeCharging) {
            double reachDistance = com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode.MeleeWeaponMode.getReachDistanceStatic(this);
            double distance = this.distanceTo(target);
            if (distance > reachDistance) {
                // 冲锋期间目标不在范围内：保留冷却为0，等待进入范围
                return;
            }
        }

        // 近战模式：在攻击执行前触发挥砍动画（无论是否命中，只要有攻击意图就播放）
        if (isMeleeMode) {
            startMeleeAttackAnimation();
        }

        // 通过武器模式执行攻击（不设置冷却）
        InterceptorAttackModeManager.executeWeaponAttack(this, target, weapon, player);

        // 计算基础冷却（通过武器模式）
        int baseCooldown = InterceptorAttackModeManager.calculateWeaponCooldown(this, weapon, player);

        // 模块模式修改冷却
        int modifiedCooldown = InterceptorAttackModeManager.modifyCooldown(baseCooldown, this, player);
        this.weaponAttackCooldown = modifiedCooldown;
        this.lastWeaponCooldownTotal = modifiedCooldown;

        // 近战攻击命中后结束冲锋
        if (isMeleeMode && this.meleeCharging) {
            endMeleeCharge();
        }

        // 模块模式：攻击后通知
        InterceptorAttackModeManager.onPostAttack(this, target, weapon, player);
    }

    /**
     * 发射爆破弹，采用三角形阵列。
     * 所有爆破弹立即生成，中心在前，横向越远越靠后，形成三角形布局。
     * 所有爆破弹共用中心方向，保证平行飞行。
     * <p>
     * 例：5发布局（俯视）：
     *       中心(前)
     *     左1  右1
     *   左2      右2
     */
    private void fireExplosiveProjectiles(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (wingman.level().isClientSide) return;

        Vec3 wingmanPos = wingman.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);
        // 使用属性系统计算有效爆破弹数量（底数来自Config + construct_wingman_explosive_count_base）
        com.gy_mod.gy_trinket.core.entity.construct.ConstructType type =
                com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance()
                        .getConstructType(WingmanConstructTypes.WINGMAN);
        int projectileCount = type != null
                ? com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier.getEffectiveExplosiveCount(owner.getUUID(), type)
                : Config.getWingmanExplosiveCount();
        float damage = (float) this.baseAttackDamage;

        // 缓存中心发射方向（所有爆破弹共用，保证平行飞行）
        Vec3 centerDirection = targetPos.subtract(wingmanPos).normalize();

        // 计算横向右方向（垂直于中心方向的水平分量）
        Vec3 horizontalDir = new Vec3(centerDirection.x, 0, centerDirection.z);
        if (horizontalDir.lengthSqr() < 1.0E-6) {
            horizontalDir = new Vec3(1, 0, 0);
        }
        horizontalDir = horizontalDir.normalize();
        Vec3 right = new Vec3(-horizontalDir.z, 0, horizontalDir.x).normalize();
        // 后方向：中心方向的反方向
        Vec3 back = centerDirection.reverse();

        for (int i = 0; i < projectileCount; i++) {
            int tier;    // 距中心层级（0=中心）
            int side;    // 方向（0=中心, 1=右, -1=左）

            if (i == 0) {
                tier = 0;
                side = 0;
            } else {
                side = (i % 2 == 1) ? 1 : -1;
                tier = (i + 1) / 2;
            }

            // 横向偏移 + 三角形后移（层级越深越靠后）
            Vec3 offset = right.scale(side * tier * LAUNCH_SPACING)
                .add(back.scale(tier * TIER_BACK_OFFSET));
            Vec3 launchPos = wingmanPos.add(offset).add(0, 0.4, 0);

            ExplosiveProjectile projectile = new ExplosiveProjectile(
                wingman.level(), (LivingEntity) wingman, damage
            );
            projectile.setPos(launchPos);
            projectile.shoot(centerDirection.x, centerDirection.y, centerDirection.z, 1.3f, 0.0f);
            wingman.level().addFreshEntity(projectile);
        }
    }

    // ===== 属性和动画 =====

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.FOLLOW_RANGE, 16.0)
            .add(Attributes.ATTACK_DAMAGE, 0.5);
    }

    // ===== 抽象方法实现 =====

    @Override
    protected void addTypeSpecificSaveData(CompoundTag tag) {
        // 拦截机武器/攻击模式数据不再保存到实体NBT，统一从Manager查询
    }

    @Override
    protected void readTypeSpecificSaveData(CompoundTag tag) {
        // 拦截机武器/攻击模式数据不再从实体NBT读取，统一从Manager查询
        // 实体恢复后从Manager同步到客户端
        syncInterceptorDataToClient();
    }

    @Override
    public String getConstructTypeId() {
        return WingmanConstructTypes.WINGMAN;
    }

    @Override
    protected void onRemoveFromConstructManager() {
        super.onRemoveFromConstructManager();
        // 清除拦截机攻击模式状态
        InterceptorAttackModeManager.clearAllModules(this);
    }

    @Override
    public Set<String> getInstanceTags() {
        java.util.Set<String> tags = super.getInstanceTags();
        if (wingmanConstruct != null) {
            tags.addAll(wingmanConstruct.getCurrentTags());
        }
        return tags;
    }

    @Override
    protected ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer) {
        return new WingmanConstructData(
            WingmanConstructTypes.WINGMAN,
            this.getUUID(),
            this.getBaseMaxHealth()
        );
    }

    @Override
    protected void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes) {
        ConstructAttributeApplier.applyAttributesToConstruct(playerUUID, this, this, attributes);
    }

    // ===== 拦截机模式 Getter（供外部查询，从Manager读取） =====

    public boolean isInterceptorMode() {
        return getInterceptorMode();
    }

    public InterceptorAttackMode getInterceptorAttackMode() {
        return getInterceptorAttackModeFromManager();
    }

    public ItemStack getInterceptorWeapon() {
        return getInterceptorWeaponFromManager();
    }
}
