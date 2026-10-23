package com.gy_mod.gy_trinket.core.entity.meteor;

import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 漂浮陨石实体。
 * <p>
 * 不是 {@link LivingEntity}：没有 AI、没有属性/药水/击退抗性，生命值和死亡分裂完全自己管理。
 * 近战、箭和爆炸通过 {@link #hurt(DamageSource, float)} 都能伤害它。
 * 陨石只和玩家/生物结算碰撞，陨石之间会直接互相穿过。
 * <p>
 * 方块碰撞使用原版 {@code move()} 和实体的实际碰撞箱；视觉外壳不参与方块碰撞。
 */
public class MeteorEntity extends Entity {

    private static final double GRAVITY = 0.08;
    private static final int COLLISION_COOLDOWN_TICKS = 10;
    private static final int HURT_COOLDOWN_TICKS = 5;
    /** 每 tick 朝速度方向转多少；0.03 约等于 1.7°/tick，转满 180° 需要数秒。 */
    private static final float ROTATION_LERP = 0.03F;
    /** 所有阶段的粒子数量倍率（在 2 倍基础上减少 20%）。 */
    private static final double PARTICLE_MULTIPLIER = 1.6;

    /** 服务端各维度当前活跃陨石数，避免自然生成器每 40 tick 扫描全部实体。 */
    private static final Map<ResourceKey<Level>, Integer> ACTIVE_COUNTS = new ConcurrentHashMap<>();

    /** 半砖作为小碎石，和整块 FallingBlock、方块粒子形成大小差异。 */
    private static final BlockState[] SMALL_DEBRIS = {
            Blocks.COBBLESTONE_SLAB.defaultBlockState(),
            Blocks.STONE_SLAB.defaultBlockState(),
            Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState()
    };

    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_SEED =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_FRAGMENT =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.BOOLEAN);

    private final MeteorSize size;

    /** 服务端：防止死亡/撞地结算重入。 */
    private boolean dead;
    private int collisionCooldown;
    private int hurtCooldown;
    /** 是否已经计入 ACTIVE_COUNTS；防止重复 add/remove 导致计数漂移。 */
    private boolean countedActive;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level, MeteorSize size) {
        super(type, level);
        this.size = size;
        this.entityData.set(DATA_HEALTH, size.maxHealth());
        this.entityData.set(DATA_SEED, this.random.nextLong());
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide && !this.countedActive) {
            this.countedActive = true;
            ACTIVE_COUNTS.merge(this.level().dimension(), 1, Integer::sum);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        if (this.countedActive) {
            this.countedActive = false;
            if (!this.level().isClientSide) {
                ACTIVE_COUNTS.computeIfPresent(this.level().dimension(),
                        (dimension, count) -> count <= 1 ? null : count - 1);
            }
        }
        super.onRemovedFromWorld();
    }

    /** 当前维度已加载的陨石数量（含分裂体）。 */
    public static int getActiveCount(ServerLevel level) {
        return ACTIVE_COUNTS.getOrDefault(level.dimension(), 0);
    }

    /** 停服/切世界时清空计数，避免旧维度计数残留。 */
    public static void clearActiveCounts() {
        ACTIVE_COUNTS.clear();
    }

    // ===== 数据同步 =====

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_HEALTH, 0.0F);
        this.entityData.define(DATA_SEED, 0L);
        this.entityData.define(DATA_FRAGMENT, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        float savedHealth = tag.contains("Health") ? tag.getFloat("Health") : this.size.maxHealth();
        this.setHealth(savedHealth <= 0.0F ? this.size.maxHealth() : savedHealth);
        if (tag.contains("Seed")) {
            this.entityData.set(DATA_SEED, tag.getLong("Seed"));
        }
        this.entityData.set(DATA_FRAGMENT, tag.getBoolean("Fragment"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", this.getHealth());
        tag.putLong("Seed", this.getShapeSeed());
        tag.putBoolean("Fragment", this.isFragment());
    }

    // ===== 每刻逻辑 =====

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientTick();
            return;
        }
        this.serverTick();
    }

    private void clientTick() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4) {
            motion = new Vec3(0.0, -1.0, 0.0);
        }
        Vec3 forward = motion.normalize();
        Vec3 back = forward.scale(-1.0);
        Vec3 center = this.visualCenter();
        double radius = this.size.radius();

        // 尾迹：烟、火焰、发光余烬、灰烬分开控制，数量都随半径增加。
        int smokeCount = particleCount(1 + (int) (radius / 5.0));
        for (int i = 0; i < smokeCount; i++) {
            Vec3 pos = smokePosition(center, back, radius);
            var smoke = i == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE;
            this.level().addParticle(smoke,
                    pos.x, pos.y, pos.z,
                    -motion.x * 0.025 + this.random.nextDouble() * 0.025,
                    0.02 + this.random.nextDouble() * 0.025,
                    -motion.z * 0.025 + this.random.nextDouble() * 0.025);
        }
        if (this.tickCount % 4 == 0) {
            Vec3 pos = smokePosition(center, back, radius, 1.9);
            this.level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    pos.x, pos.y, pos.z, 0.0, 0.03, 0.0);
        }

        int flameCount = particleCount(1 + (int) (radius / 4.0));
        for (int i = 0; i < flameCount; i++) {
            Vec3 pos = trailPosition(center, back, radius, 0.55);
            this.level().addParticle(ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z,
                    -motion.x * 0.07 + this.random.nextDouble() * 0.04,
                    0.008 + this.random.nextDouble() * 0.03,
                    -motion.z * 0.07 + this.random.nextDouble() * 0.04);
        }

        int emberCount = particleCount(3 + (int) (radius / 2.0));
        float emberSize = 0.7F + (float) radius * 0.05F;
        for (int i = 0; i < emberCount; i++) {
            Vec3 pos = trailPosition(center, back, radius, 0.45);
            float green = 0.35F + this.random.nextFloat() * 0.35F;
            this.level().addParticle(
                    new DustParticleOptions(new Vector3f(1.0F, green, 0.05F), emberSize),
                    pos.x, pos.y, pos.z,
                    -motion.x * 0.05 + this.random.nextDouble() * 0.035,
                    0.015 + this.random.nextDouble() * 0.03,
                    -motion.z * 0.05 + this.random.nextDouble() * 0.035);
        }

        int ashCount = particleCount(1 + (int) (radius / 8.0));
        for (int i = 0; i < ashCount; i++) {
            Vec3 pos = trailPosition(center, back, radius, 0.9);
            this.level().addParticle(this.random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    pos.x, pos.y, pos.z,
                    (this.random.nextDouble() - 0.5) * 0.04,
                    -0.005,
                    (this.random.nextDouble() - 0.5) * 0.04);
        }

        // 整个球壳的燃烧：让陨石本体看起来在灼烧，而不只是拖一条尾。
        int shellCount = particleCount(4 + (int) (radius / 2.0));
        float haloSize = 1.0F + (float) radius * 0.05F;
        for (int i = 0; i < shellCount; i++) {
            Vec3 pos = randomShellPoint(center, radius * 0.9);
            Vec3 outward = pos.subtract(center).normalize();
            this.level().addParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                    pos.x, pos.y, pos.z,
                    outward.x * 0.035 + back.x * 0.03,
                    outward.y * 0.035 + back.y * 0.03 + 0.01,
                    outward.z * 0.035 + back.z * 0.03);
        }
        int haloCount = particleCount(2 + (int) (radius / 3.0));
        for (int i = 0; i < haloCount; i++) {
            Vec3 pos = randomShellPoint(center, radius * 0.98);
            Vec3 outward = pos.subtract(center).normalize();
            this.level().addParticle(
                    new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.08F), haloSize),
                    pos.x, pos.y, pos.z,
                    outward.x * 0.02 + back.x * 0.02,
                    outward.y * 0.02 + back.y * 0.02,
                    outward.z * 0.02 + back.z * 0.02);
        }
        int lavaCount = particleCount(3 + (int) (radius / 2.0));
        for (int i = 0; i < lavaCount; i++) {
            Vec3 pos = randomShellPoint(center, radius * 0.96);
            Vec3 outward = pos.subtract(center).normalize();
            this.level().addParticle(ParticleTypes.LAVA,
                    pos.x, pos.y, pos.z,
                    outward.x * 0.03 + back.x * 0.02,
                    outward.y * 0.03 + back.y * 0.02,
                    outward.z * 0.03 + back.z * 0.02);
        }
        int sparkCount = particleCount(2 + (int) (radius / 3.0));
        for (int i = 0; i < sparkCount; i++) {
            Vec3 pos = randomShellPoint(center, radius * 0.9);
            Vec3 outward = pos.subtract(center).normalize();
            this.level().addParticle(this.random.nextBoolean()
                            ? ParticleTypes.FIREWORK : ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z,
                    outward.x * 0.10 + back.x * 0.05,
                    outward.y * 0.10 + back.y * 0.05,
                    outward.z * 0.10 + back.z * 0.05);
        }

        // 迎风面的火焰环。
        Vec3 lead = center.add(forward.scale(radius * 0.72));
        int ring = particleCount(4 + (int) (radius / 2.0));
        Vec3 axis = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (axis.lengthSqr() < 1.0E-4) {
            axis = new Vec3(1.0, 0.0, 0.0);
        }
        axis = axis.normalize();
        Vec3 axis2 = forward.cross(axis).normalize();
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2.0 * i / ring + this.random.nextDouble() * 0.25;
            Vec3 offset = axis.scale(Math.cos(angle) * radius * 0.55)
                    .add(axis2.scale(Math.sin(angle) * radius * 0.55));
            Vec3 pos = lead.add(offset);
            this.level().addParticle(
                    i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                    pos.x, pos.y, pos.z,
                    offset.x * 0.12 + forward.x * 0.02,
                    offset.y * 0.12 + forward.y * 0.02,
                    offset.z * 0.12 + forward.z * 0.02);
            if (i % 3 == 0) {
                this.level().addParticle(ParticleTypes.LAVA,
                        pos.x, pos.y, pos.z,
                        offset.x * 0.08, offset.y * 0.08, offset.z * 0.08);
            }
            if (i % 4 == 0) {
                this.level().addParticle(ParticleTypes.FALLING_LAVA,
                        pos.x, pos.y + radius * 0.15, pos.z,
                        0.0, -0.10, 0.0);
            }
        }

        // 不再按速度分阶段：始终使用最终阶段的灼烧粒子。
        this.level().addParticle(ParticleTypes.FIREWORK,
                lead.x + (this.random.nextDouble() - 0.5) * radius * 0.5,
                lead.y + (this.random.nextDouble() - 0.5) * radius * 0.3,
                lead.z + (this.random.nextDouble() - 0.5) * radius * 0.5,
                -motion.x * 0.08, -motion.y * 0.04, -motion.z * 0.08);
        if (this.tickCount % 3 == 0) {
            this.level().addParticle(ParticleTypes.LAVA,
                    lead.x, lead.y, lead.z,
                    (this.random.nextDouble() - 0.5) * 0.06,
                    -0.08,
                    (this.random.nextDouble() - 0.5) * 0.06);
            this.level().addParticle(ParticleTypes.FALLING_LAVA,
                    lead.x, lead.y + radius * 0.25, lead.z,
                    0.0, -0.12, 0.0);
        }
    }
    private static int particleCount(int base) {
        return Math.max(1, (int) (base * PARTICLE_MULTIPLIER));
    }

    private Vec3 randomShellPoint(Vec3 center, double radius) {
        double theta = this.random.nextDouble() * Math.PI * 2.0;
        double cosPhi = this.random.nextDouble() * 2.0 - 1.0;
        double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
        return new Vec3(
                center.x + Math.cos(theta) * sinPhi * radius,
                center.y + cosPhi * radius,
                center.z + Math.sin(theta) * sinPhi * radius);
    }

    private Vec3 smokePosition(Vec3 center, Vec3 back, double radius) {
        return smokePosition(center, back, radius, 1.0);
    }

    private Vec3 smokePosition(Vec3 center, Vec3 back, double radius, double backFactor) {
        double behind = radius * (backFactor + this.random.nextDouble() * 0.8);
        double spread = radius * 0.30;
        return new Vec3(
                center.x + (this.random.nextDouble() - 0.5) * spread + back.x * behind,
                center.y + (this.random.nextDouble() - 0.5) * spread + back.y * behind,
                center.z + (this.random.nextDouble() - 0.5) * spread + back.z * behind);
    }

    private Vec3 trailPosition(Vec3 center, Vec3 back, double radius, double backFactor) {
        double spread = radius * 0.55;
        double behind = radius * backFactor + this.random.nextDouble() * radius * 0.7;
        return new Vec3(
                center.x + (this.random.nextDouble() - 0.5) * spread + back.x * behind,
                center.y + (this.random.nextDouble() - 0.5) * spread + back.y * behind,
                center.z + (this.random.nextDouble() - 0.5) * spread + back.z * behind);
    }

    private void serverTick() {
        if (this.collisionCooldown > 0) {
            this.collisionCooldown--;
        }
        if (this.hurtCooldown > 0) {
            this.hurtCooldown--;
        }

        // 调试/展示用：NoGravity 陨石原地悬浮，仍由客户端播放拖尾粒子。
        if (this.isNoGravity()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        double vx = Mth.clamp(velocity.x, -1.0, 1.0) * 0.999;
        // 竖直方向不再限速；100% 重力下终端速度约为 4 格/tick。
        double vy = (velocity.y - GRAVITY) * 0.98;
        double vz = Mth.clamp(velocity.z, -1.0, 1.0) * 0.999;
        Vec3 intendedVelocity = new Vec3(vx, vy, vz);
        this.setDeltaMovement(intendedVelocity);
        this.updateRotationFromVelocity();

        // 原版方块碰撞检测：碰到方块立即崩解，不再做射线/穿透。
        this.move(net.minecraft.world.entity.MoverType.SELF, intendedVelocity);

        if (this.getY() < this.level().getMinBuildHeight() - 16) {
            this.discard();
            return;
        }

        // 远处的陨石长期飘在加载区块里会占满自然生成上限，每 10 秒清理一次。
        if (this.tickCount % 200 == 0 && this.level() instanceof ServerLevel server) {
            boolean nearPlayer = false;
            for (ServerPlayer player : server.players()) {
                if (player.distanceToSqr(this) < 512.0 * 512.0) {
                    nearPlayer = true;
                    break;
                }
            }
            if (!nearPlayer) {
                this.discard();
                return;
            }
        }

        if (this.horizontalCollision || this.verticalCollision || this.onGround()
                || !this.level().noCollision(this)) {
            this.impactOnBlocks(intendedVelocity);
            return;
        }
        this.checkEntityCollisions();
    }

    /**
     * 陨石的外形会缓慢转向自己的速度方向：水平速度决定 yaw，竖直速度决定 pitch。
     * 只影响渲染朝向，碰撞箱仍然是轴对齐立方体。
     */
    private void updateRotationFromVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double total = Math.sqrt(horizontal * horizontal + velocity.y * velocity.y);
        if (total < 1.0E-4) {
            return;
        }

        float targetYaw = (float) (Math.atan2(-velocity.x, velocity.z) * (180.0 / Math.PI));
        float targetPitch = (float) (-Math.asin(Mth.clamp(velocity.y / total, -1.0, 1.0))
                * (180.0 / Math.PI));

        this.setYRot(Mth.rotLerp(ROTATION_LERP, this.getYRot(), targetYaw));
        this.setXRot(Mth.lerp(ROTATION_LERP, this.getXRot(), targetPitch));
    }

    // ===== 撞方块 =====

    /** 碰到方块立即崩解，不做射线或穿透。 */
    private void impactOnBlocks(Vec3 velocity) {
        if (this.dead) {
            return;
        }

        // 爆心放在碰撞位置（实体脚底/碰撞面），而不是模型中心。
        Vec3 point = this.position();
        this.dead = true;
        if (!(this.level() instanceof ServerLevel server)) {
            this.discard();
            return;
        }

        double horizontal = this.size.breakRadius();
        double vertical = horizontal * 0.5;
        server.playSound(null, point.x, point.y, point.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                4.0F, 0.55F + this.random.nextFloat() * 0.15F);

        // 崩解不再生成粒子，完全交给被炸飞的方块实体表现。
        MeteorCrater.breakEllipsoid(server, point, horizontal, vertical, horizontal);
        this.damageEntitiesInImpact(server, point, horizontal, velocity);
        this.spawnSplit(server, point);
        this.discard();
    }

    /** 销毁范围内的生物撞击，使用碰撞前的最终速度。 */
    private void damageEntitiesInImpact(ServerLevel server, Vec3 center, double radius, Vec3 impactVelocity) {
        AABB area = new AABB(center, center).inflate(radius);
        List<LivingEntity> candidates = server.getEntitiesOfClass(LivingEntity.class, area);
        DamageSource source = ModDamageTypes.getMeteorImpactDamageSource(server, this);
        double radiusSqr = radius * radius;

        for (LivingEntity other : candidates) {
            if (other instanceof Player player && player.isSpectator()) {
                continue;
            }
            Vec3 otherCenter = other.getBoundingBox().getCenter();
            if (otherCenter.distanceToSqr(center) > radiusSqr) {
                continue;
            }

            double relativeSpeed = impactVelocity.subtract(other.getDeltaMovement()).length();
            float damage = (0.15F * this.getMaxHealth() + 3.0F)
                    * (float) (1.0 + 0.10 * relativeSpeed);
            other.hurt(source, damage);

            Vec3 delta = otherCenter.subtract(center);
            if (delta.lengthSqr() < 1.0E-6) {
                delta = new Vec3(this.random.nextDouble() - 0.5, 0.0, this.random.nextDouble() - 0.5);
            }
            Vec3 direction = delta.normalize();
            double impulse = Math.min(2.0, 0.35 + 0.15 * relativeSpeed);
            other.push(direction.x * impulse, 0.10, direction.z * impulse);
            other.hurtMarked = true;
        }
    }

    // ===== 实体碰撞 =====

    private void checkEntityCollisions() {
        if (this.dead || this.collisionCooldown > 0) {
            return;
        }

        // 实体碰撞使用正常碰撞箱检测。
        AABB hitbox = this.getBoundingBox();
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class, hitbox);

        for (LivingEntity other : candidates) {
            if (!hitbox.intersects(other.getBoundingBox())) {
                continue;
            }
            if (other instanceof Player player && player.isSpectator()) {
                continue;
            }
            this.handleEntityCollision(other);
            return;
        }
    }

    /**
     * 陨石只和玩家/生物结算碰撞；陨石之间不再互相碰撞，也不互相伤害。
     */
    private void handleEntityCollision(LivingEntity other) {
        double relativeSpeed = this.getDeltaMovement().subtract(other.getDeltaMovement()).length();
        float damage = (0.15F * this.getMaxHealth() + 3.0F) * (float) (1.0 + 0.10 * relativeSpeed);

        DamageSource source = ModDamageTypes.getMeteorImpactDamageSource(this.level(), this);
        other.hurt(source, damage);
        this.applySeparation(other, relativeSpeed);

        if (!this.dead) {
            this.hurt(source, damage);
        }
        this.collisionCooldown = COLLISION_COOLDOWN_TICKS;
    }

    private void applySeparation(LivingEntity other, double relativeSpeed) {
        // 用碰撞箱中心而不是脚底，避免特大陨石把玩家往斜下方推。
        Vec3 delta = this.getBoundingBox().getCenter().subtract(other.getBoundingBox().getCenter());
        if (delta.lengthSqr() < 1.0E-6) {
            delta = new Vec3(this.random.nextDouble() - 0.5, 0.0, this.random.nextDouble() - 0.5);
        }
        Vec3 direction = delta.normalize();
        double impulse = Math.min(2.0, 0.35 + 0.15 * relativeSpeed);

        // 大型/特大型陨石有击退抗性：只按剩余比例接受相离速度。
        double selfScale = 1.0 - this.size.knockbackResistance();
        if (selfScale > 0.0) {
            Vec3 selfPush = direction.scale(impulse).add(0.0, 0.10, 0.0).scale(selfScale);
            this.setDeltaMovement(this.getDeltaMovement().add(selfPush));
        }
        other.push(-direction.x * impulse, 0.10, -direction.z * impulse);
        other.hurtMarked = true;
    }

    /**
     * 外部推力（爆炸、其他实体推挤等）也按击退抗性衰减；特大陨石完全不被推开。
     */
    @Override
    public void push(double x, double y, double z) {
        double factor = 1.0 - this.size.knockbackResistance();
        if (factor <= 0.0) {
            return;
        }
        super.push(x * factor, y * factor, z * factor);
    }

    // ===== 受伤与死亡 =====

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.dead || this.isRemoved() || amount <= 0.0F) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        if (this.hurtCooldown > 0 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            return false;
        }

        this.hurtCooldown = HURT_COOLDOWN_TICKS;
        this.setHealth(this.getHealth() - amount);

        if (this.level() instanceof ServerLevel server) {
            Vec3 center = this.getBoundingBox().getCenter();
            server.playSound(null, center.x, center.y, center.z,
                    SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS,
                    1.0F, 0.6F + this.random.nextFloat() * 0.2F);
            int count = particleCount(6 + (int) this.size.radius());
            server.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z,
                    count, this.size.radius() * 0.4, this.size.radius() * 0.4, this.size.radius() * 0.4, 0.08);
        }

        if (this.getHealth() <= 0.0F) {
            this.die(source);
        }
        return true;
    }

    /**
     * /kill 走的是 Entity#kill，而不是 hurt；这里让命令击杀也触发分裂。
     */
    @Override
    public void kill() {
        if (!this.level().isClientSide && !this.dead) {
            this.die(this.damageSources().genericKill());
        } else {
            super.kill();
        }
    }

    private void die(DamageSource source) {
        if (this.dead) {
            return;
        }
        this.dead = true;

        if (this.level() instanceof ServerLevel server) {
            Vec3 center = this.visualCenter();
            server.playSound(null, center.x, center.y, center.z,
                    SoundEvents.STONE_BREAK, SoundSource.BLOCKS,
                    2.0F, 0.55F + this.random.nextFloat() * 0.2F);
            this.spawnSplit(server, center);
        }
        this.discard();
    }

    // ===== 分裂 =====

    private void spawnSplit(ServerLevel level, Vec3 center) {
        this.spawnRockDebris(level, center);
        this.spawnExtraImpactBlocks(level, center);

        MeteorSize[] children = this.size.splits();
        if (children.length == 0) {
            this.spawnDebris(level, center);
            return;
        }

        long parentShapeSeed = this.getShapeSeed();
        int childIndex = 0;
        for (MeteorSize childSize : children) {
            Vec3 childCenter = this.findFreeSpot(level, center, childSize);
            MeteorEntity child = MeteorEntities.create(childSize, level);
            // 碰撞箱是居中的，脚底位置要减去碰撞箱半高，而不是视觉半径。
            child.setPos(childCenter.x, childCenter.y - childSize.hitboxRadius(), childCenter.z);
            // 使用母体的形状种子派生碎块，保证切割面/材质来自同一块石头。
            child.entityData.set(DATA_SEED, parentShapeSeed + (long) (++childIndex) * 0x9E3779B97F4A7C15L);
            child.setFragment(true);

            // 水平随机方向的速度随母体大小提升；竖直向上速度模拟崩解。
            Vec3 horizontal = new Vec3(
                    this.random.nextDouble() * 2.0 - 1.0,
                    0.0,
                    this.random.nextDouble() * 2.0 - 1.0
            );
            if (horizontal.lengthSqr() < 1.0E-6) {
                horizontal = new Vec3(1.0, 0.0, 0.0);
            }
            double sizeFactor = 1.0 + this.size.radius() * 0.12;
            double horizontalSpeed = (0.10 + this.random.nextDouble() * 0.12) * sizeFactor;
            Vec3 horizontalVelocity = horizontal.normalize().scale(horizontalSpeed);

            // 分裂体初始速度整体提高 3 倍。
            child.setDeltaMovement(horizontalVelocity
                    .add(0.0, 0.09 + this.random.nextDouble() * 0.07, 0.0)
                    .add(this.getDeltaMovement().scale(0.35))
                    .scale(3.0));
            level.addFreshEntity(child);
        }
    }

    /** 中型及以上陨石额外抛出的方块实体，数量 = 半径 × 3。 */
    private void spawnExtraImpactBlocks(ServerLevel level, Vec3 center) {
        if (this.size == MeteorSize.SMALL) {
            return;
        }
        double r = this.size.radius();
        int count = (int) (r * 3.0);
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = r * (0.35 + this.random.nextDouble() * 0.75);
            double x = center.x + Math.cos(angle) * distance;
            double y = center.y + (this.random.nextDouble() - 0.4) * r * 0.9;
            double z = center.z + Math.sin(angle) * distance;
            BlockState state = MeteorPalette.random(this.random);
            FallingBlockEntity debris = FallingBlockEntity.fall(level,
                    BlockPos.containing(x, y, z), state);
            double speed = 0.12 + this.random.nextDouble() * 0.18 + r * 0.025;
            double up = 0.18 + this.random.nextDouble() * 0.25 + r * 0.02;
            debris.setDeltaMovement(Math.cos(angle) * speed, up, Math.sin(angle) * speed);
            debris.hurtMarked = true;
        }
    }

    /** 大小不一的碎石：整块 FallingBlock、半砖 FallingBlock。 */
    private void spawnRockDebris(ServerLevel level, Vec3 center) {
        double r = this.size.radius();

        // 大块碎石：整块方块。
        int fullBlocks = 5 + (int) (r * 0.9);
        for (int i = 0; i < fullBlocks; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = r * (0.25 + this.random.nextDouble() * 0.65);
            double x = center.x + Math.cos(angle) * distance;
            double y = center.y + (this.random.nextDouble() - 0.45) * r * 0.9;
            double z = center.z + Math.sin(angle) * distance;
            FallingBlockEntity debris = FallingBlockEntity.fall(level,
                    BlockPos.containing(x, y, z), MeteorPalette.random(this.random));
            debris.setDeltaMovement(Math.cos(angle) * (0.10 + this.random.nextDouble() * 0.15),
                    0.08 + this.random.nextDouble() * 0.18,
                    Math.sin(angle) * (0.10 + this.random.nextDouble() * 0.15));
            debris.hurtMarked = true;
        }

        // 小块碎石：半砖，视觉上明显比整块小一号。
        int smallBlocks = 3 + (int) (r * 0.7);
        for (int i = 0; i < smallBlocks; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = r * (0.2 + this.random.nextDouble() * 0.7);
            double x = center.x + Math.cos(angle) * distance;
            double y = center.y + (this.random.nextDouble() - 0.45) * r * 0.9;
            double z = center.z + Math.sin(angle) * distance;
            BlockState state = SMALL_DEBRIS[this.random.nextInt(SMALL_DEBRIS.length)];
            FallingBlockEntity debris = FallingBlockEntity.fall(level,
                    BlockPos.containing(x, y, z), state);
            debris.setDeltaMovement(Math.cos(angle) * (0.12 + this.random.nextDouble() * 0.18),
                    0.10 + this.random.nextDouble() * 0.20,
                    Math.sin(angle) * (0.12 + this.random.nextDouble() * 0.18));
            debris.hurtMarked = true;
        }
    }

    private void spawnDebris(ServerLevel level, Vec3 center) {
        // 小型陨石碎裂成 3~6 个原版 FallingBlock，落地后分别变成正常方块。
        int count = 3 + this.random.nextInt(4);
        double spread = this.size.radius() * 0.7;

        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = this.random.nextDouble() * spread;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + (this.random.nextDouble() - 0.5) * this.size.radius();

            BlockState state = MeteorPalette.random(this.random);
            FallingBlockEntity debris = FallingBlockEntity.fall(level, BlockPos.containing(x, y, z), state);
            debris.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.08, 0.02,
                    (this.random.nextDouble() - 0.5) * 0.08);
            debris.hurtMarked = true;
        }
    }

    private Vec3 findFreeSpot(ServerLevel level, Vec3 center, MeteorSize childSize) {
        double radius = childSize.radius();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            // 碎块从母体中心附近出现，而不是在远处找一个空位。
            double distance = radius * 0.4 + this.random.nextDouble() * this.size.radius() * 0.15;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + (this.random.nextDouble() - 0.5) * this.size.radius() * 0.25;
            double feetY = y - radius;
            if (feetY < level.getMinBuildHeight() + 2.0) {
                continue;
            }
            AABB box = new AABB(x - radius, feetY, z - radius,
                    x + radius, feetY + radius * 2.0, z + radius);
            if (level.noCollision(box)) {
                return new Vec3(x, y, z);
            }
        }
        return center.add(0.0, radius * 0.5, 0.0);
    }

    // ===== 属性 =====

    /** 视觉球心 = 碰撞箱中心，渲染模型也以这里为中心。 */
    private Vec3 visualCenter() {
        return new Vec3(this.getX(), this.getY() + this.size.hitboxRadius(), this.getZ());
    }

    /**
     * 原版实体距离剔除使用的是“碰撞箱尺寸 × 64”。碰撞箱缩小后这个距离也会骤减，
     * 陨石在 150 格高处可能被提前剔除；这里改用视觉直径计算。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double renderDistance = 64.0 * this.size.diameter();
        return distanceSqr < renderDistance * renderDistance;
    }

    /** 视锥剔除使用完整视觉外壳，而不是中心的小碰撞箱。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 center = this.visualCenter();
        double radius = this.size.radius();
        return new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
    }

    public MeteorSize getSize() {
        return this.size;
    }

    public float getMaxHealth() {
        return this.size.maxHealth();
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    private void setHealth(float health) {
        this.entityData.set(DATA_HEALTH, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public long getShapeSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public boolean isFragment() {
        return this.entityData.get(DATA_FRAGMENT);
    }

    public void setFragment(boolean fragment) {
        this.entityData.set(DATA_FRAGMENT, fragment);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }
}
