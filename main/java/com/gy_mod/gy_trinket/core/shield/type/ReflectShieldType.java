package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 反射护盾类型实现类
 * <p>
 * 功能：
 * 1. 反射弹射物（箭矢、雪球等）
 * 2. 防止弹射物直接爆炸（如烈焰弹）
 * 3. 反射速度受护盾效果属性影响
 * <p>
 * 兼容性：不兼容（isCompatible() 返回 false）
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class ReflectShieldType implements IShieldType {

    /**
     * 弹射物伤害信息类
     * 用于存储反射前的弹射物状态，以便正确计算反射方向和速度
     */
    public static class ProjectileDamageInfo {
        private final Projectile projectile;
        private final double originalSpeed;
        private final double posX, posY, posZ;

        public ProjectileDamageInfo(Projectile projectile, double originalSpeed,
                                    double posX, double posY, double posZ) {
            this.projectile = projectile;
            this.originalSpeed = originalSpeed;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
        }

        public Projectile getProjectile() { return projectile; }
        public double getOriginalSpeed() { return originalSpeed; }
        public double getPosX() { return posX; }
        public double getPosY() { return posY; }
        public double getPosZ() { return posZ; }
    }

    /** 存储玩家最后受到的弹射物信息 */
    private static final Map<UUID, ProjectileDamageInfo> LAST_PROJECTILE_INFO = new HashMap<>();
    /** 存储已反射弹射物及其信息（效果值和过期时间） */
    private static final Map<Integer, ReflectedProjectileInfo> REFLECTED_PROJECTILES = new HashMap<>();
    /** 存储需要防止爆炸的弹射物及其过期时间 */
    private static final Map<Integer, Long> EXPLOSION_PREVENTION_PROJECTILES = new HashMap<>();

    /** 爆炸防止持续时间（毫秒） */
    private static final long EXPLOSION_PREVENTION_DURATION = 3000;
    /** 反射弹射物记录过期时间（毫秒） */
    private static final long REFLECTED_PROJECTILE_EXPIRE_TIME = 10000;

    /**
     * 反射弹射物信息类
     */
    private static class ReflectedProjectileInfo {
        final long expireTime;

        ReflectedProjectileInfo(float effect, long expireTime) {
            this.expireTime = expireTime;
        }
    }

    @Override
    public String getName() {
        return "reflect";
    }

    @Override
    public boolean isCompatible() {
        return false;
    }

    /**
     * 获取玩家最后受到的弹射物信息
     * @param player 玩家
     * @return 弹射物信息，如果没有则返回 null
     */
    public static ProjectileDamageInfo getLastProjectileInfo(Player player) {
        return LAST_PROJECTILE_INFO.get(player.getUUID());
    }

    /**
     * 设置玩家最后受到的弹射物信息
     * @param player 玩家
     * @param projectile 弹射物
     */
    public static void setLastProjectileInfo(Player player, Projectile projectile) {
        LAST_PROJECTILE_INFO.put(player.getUUID(), createProjectileDamageInfo(projectile));
    }

    /**
     * 移除玩家的最后弹射物信息
     * @param player 玩家
     */
    public static void removeLastProjectileInfo(Player player) {
        LAST_PROJECTILE_INFO.remove(player.getUUID());
    }

    /**
     * 记录待反射的弹射物
     * @param player 玩家
     * @param projectile 弹射物
     */
    public static void recordProjectileForReflect(Player player, Projectile projectile) {
        if (player.level().isClientSide()) {
            return;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return;
        }

        if (projectile.getOwner() == player) {
            return;
        }

        ProjectileDamageInfo info = createProjectileDamageInfo(projectile);
        LAST_PROJECTILE_INFO.put(player.getUUID(), info);
        EXPLOSION_PREVENTION_PROJECTILES.put(projectile.getId(), System.currentTimeMillis() + EXPLOSION_PREVENTION_DURATION);
    }

    /**
     * 判断是否应该取消爆炸
     * @param sourceEntity 爆炸源实体
     * @return 如果应该取消爆炸返回 true
     */
    public static boolean shouldCancelExplosion(Entity sourceEntity) {
        if (sourceEntity == null) {
            return false;
        }

        Long expirationTime = EXPLOSION_PREVENTION_PROJECTILES.remove(sourceEntity.getId());
        return expirationTime != null && System.currentTimeMillis() < expirationTime;
    }

    /**
     * 在护盾处理后处理反射
     * @param player 玩家
     * @param damage 伤害值
     */
    public static void handleReflectAfterShield(Player player, float damage) {
        ProjectileDamageInfo info = LAST_PROJECTILE_INFO.remove(player.getUUID());
        if (info == null || !info.getProjectile().isAlive()) {
            return;
        }
        reflectProjectile(info.getProjectile(), player, player, info);
    }

    /**
     * 在护盾受伤后处理反射
     * @param player 玩家
     */
    public static void processReflectAfterShieldDamage(Player player) {
        processReflectAfterShieldDamage(player, player);
    }

    /**
     * 在护盾受伤后处理反射
     * @param player 玩家
     * @param attackedEntity 被攻击的实体（可能是玩家或被护盾保护的实体）
     */
    public static void processReflectAfterShieldDamage(Player player, LivingEntity attackedEntity) {
        ProjectileDamageInfo info = LAST_PROJECTILE_INFO.remove(player.getUUID());
        if (info == null || !info.getProjectile().isAlive()) {
            return;
        }
        reflectProjectile(info.getProjectile(), player, attackedEntity, info);
    }

    /**
     * 执行弹射物反射
     * <p>
     * 流程：
     * 1. 获取玩家的护盾效果半径属性，计算反射速度修正
     * 2. 计算弹射物的反射方向（朝向原主人或被攻击实体朝向）
     * 3. 删除原始弹射物，创建新的反射弹射物
     * 4. 设置反射弹射物的位置为被攻击实体的位置、速度、所有者等属性
     * 5. 特殊处理：箭矢设为暴击，药水保留原物品数据
     * 6. 将反射弹射物添加到世界
     *
     * @param projectile 原始弹射物
     * @param player 玩家（反射者/护盾拥有者）
     * @param attackedEntity 被攻击的实体（可能是玩家或被护盾保护的实体）
     * @param info 弹射物信息（包含原始速度、位置等）
     */
    private static void reflectProjectile(Projectile projectile, Player player, LivingEntity attackedEntity, ProjectileDamageInfo info) {
        double shieldEffect_radius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius") - 1;
        double speedModifier = Config.getReflectSpeedBaseModifier() * (1 + Config.getReflectSpeedExtraModifier() * shieldEffect_radius);

        Entity originalOwner = projectile.getOwner();
        Vec3 direction = calculateReflectDirection(projectile, attackedEntity, originalOwner);
        double finalSpeed = info.getOriginalSpeed() * speedModifier;

        projectile.remove(Entity.RemovalReason.DISCARDED);

        Projectile reflected = (Projectile) projectile.getType().create(player.level());
        if (reflected == null) {
            return;
        }

        reflected.setPos(attackedEntity.getX(), attackedEntity.getY() + attackedEntity.getEyeHeight(), attackedEntity.getZ());
        reflected.setDeltaMovement(direction.scale(finalSpeed));
        reflected.setOwner(player);
        reflected.tickCount = 0;
        reflected.setInvulnerable(false);

        if (reflected instanceof Arrow arrow) {
            arrow.setCritArrow(true);
            arrow.pickup = Arrow.Pickup.ALLOWED;
        }

        if (reflected instanceof ThrownPotion reflectedPotion && projectile instanceof ThrownPotion originalPotion) {
            reflectedPotion.setItem(originalPotion.getItem());
        }

        player.level().addFreshEntity(reflected);

        long expireTime = System.currentTimeMillis() + REFLECTED_PROJECTILE_EXPIRE_TIME;
        REFLECTED_PROJECTILES.put(reflected.getId(), new ReflectedProjectileInfo((float)shieldEffect_radius, expireTime));
    }

    /**
     * 计算反射方向
     * @param projectile 弹射物
     * @param attackedEntity 被攻击的实体
     * @param originalOwner 原始所有者
     * @return 反射方向向量
     */
    private static Vec3 calculateReflectDirection(Projectile projectile, LivingEntity attackedEntity, Entity originalOwner) {
        if (originalOwner != null && originalOwner.isAlive() && originalOwner.level() == attackedEntity.level()) {
            double distance = originalOwner.distanceTo(attackedEntity);
            if (distance <= 40.0) {
                Vec3 ownerHead = new Vec3(originalOwner.getX(), originalOwner.getY() + originalOwner.getEyeHeight(), originalOwner.getZ());
                Vec3 entityPos = new Vec3(attackedEntity.getX(), attackedEntity.getY() + attackedEntity.getEyeHeight(), attackedEntity.getZ());
                return ownerHead.subtract(entityPos).normalize();
            }
        }
        return attackedEntity.getLookAngle().reverse();
    }

    /**
     * 创建弹射物伤害信息
     * @param projectile 弹射物
     * @return 弹射物伤害信息
     */
    private static ProjectileDamageInfo createProjectileDamageInfo(Projectile projectile) {
        return new ProjectileDamageInfo(
            projectile,
            projectile.getDeltaMovement().length(),
            projectile.getX(), projectile.getY(), projectile.getZ()
        );
    }

    @Override
    public void onTick(Player player) {
    }

    /**
     * 处理弹射物撞击事件
     * - 防止未反射弹射物的爆炸
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit)) {
            return;
        }

        Entity hitEntity = entityHit.getEntity();
        if (hitEntity instanceof Player) {
            return;
        }

        Projectile projectile = event.getProjectile();
        int projectileId = projectile.getId();

        if (EXPLOSION_PREVENTION_PROJECTILES.containsKey(projectileId)) {
            event.setCanceled(true);
            return;
        }
    }

    /**
     * 处理生物攻击事件
     * - 当反射弹射物击中生物时：
     *   1. 取消原始攻击
     *   2. 计算护盾效果属性提升的伤害量
     *   3. 以归属玩家的爆炸伤害重新施加给目标
     *   4. 移除反射弹射物
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity directEntity = event.getSource().getDirectEntity();
        if (directEntity == null || !(directEntity instanceof Projectile projectile)) {
            return;
        }

        int projectileId = projectile.getId();
        if (!REFLECTED_PROJECTILES.containsKey(projectileId)) {
            return;
        }

        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player) || event.getEntity() instanceof Player) {
            return;
        }

        event.setCanceled(true);

        Player attacker = (Player) owner;
        REFLECTED_PROJECTILES.remove(projectileId);

        LivingEntity target = (LivingEntity) event.getEntity();

        float originalDamage = event.getAmount();
        double shieldEffect = AttributeManager.getGroupAttribute(attacker.getUUID(), "shield_effect") - 1;
        double damageEffectMultiplier = Config.getReflectDamageEffectMultiplier();
        float finalDamage = (float)(originalDamage * (1 + shieldEffect * damageEffectMultiplier));

        target.invulnerableTime = 0;
        target.hurt(target.damageSources().explosion(null, attacker), finalDamage);
        target.invulnerableTime = 0;

        projectile.remove(Entity.RemovalReason.DISCARDED);
    }

    /**
     * 处理爆炸开始事件
     * - 取消已记录弹射物的爆炸
     */
    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
        if (shouldCancelExplosion(sourceEntity)) {
            event.setCanceled(true);
        }
    }

    /**
     * 清理玩家数据
     * @param playerUUID 玩家UUID
     */
    public static void clearPlayerData(UUID playerUUID) {
        LAST_PROJECTILE_INFO.remove(playerUUID);
    }

    /**
     * 清理弹射物数据
     * @param projectileId 弹射物ID
     */
    public static void clearProjectileData(int projectileId) {
        REFLECTED_PROJECTILES.remove(projectileId);
        EXPLOSION_PREVENTION_PROJECTILES.remove(projectileId);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        LAST_PROJECTILE_INFO.clear();
        REFLECTED_PROJECTILES.clear();
        EXPLOSION_PREVENTION_PROJECTILES.clear();
    }

    static {
        com.gy_mod.gy_trinket.core.TickScheduler.register("reflect_cleanup", 20, () -> {
            long currentTime = System.currentTimeMillis();
            REFLECTED_PROJECTILES.entrySet().removeIf(entry -> {
                ReflectedProjectileInfo info = entry.getValue();
                return currentTime > info.expireTime;
            });
            EXPLOSION_PREVENTION_PROJECTILES.entrySet().removeIf(entry -> {
                return currentTime > entry.getValue();
            });
        });
    }
}