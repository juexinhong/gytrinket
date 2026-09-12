package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class ReflectShieldType implements IShieldType {

    /**
     * 弹射物伤害信息类
     * 用于存储反射前的弹射物状态，以便正确计算反射方向和速度
     */
    public static class ProjectileDamageInfo {
        private final Projectile projectile;
        private final double originalSpeed;
        private final double posX, posY, posZ;
        /** 提供该 reflect 实例的物品 ID（数值按该物品实例取） */
        private final String itemId;

        public ProjectileDamageInfo(Projectile projectile, double originalSpeed,
                                    double posX, double posY, double posZ, String itemId) {
            this.projectile = projectile;
            this.originalSpeed = originalSpeed;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.itemId = itemId;
        }

        public Projectile getProjectile() { return projectile; }
        public double getOriginalSpeed() { return originalSpeed; }
        public double getPosX() { return posX; }
        public double getPosY() { return posY; }
        public double getPosZ() { return posZ; }
        public String getItemId() { return itemId; }
    }

    /** 存储玩家待反射的弹射物信息（每个 active reflect 实例一条，各用各自物品定义的参数） */
    private static final Map<UUID, List<ProjectileDamageInfo>> PENDING_REFLECT_INFOS = new HashMap<>();
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
        /** 提供该 reflect 实例的物品 ID */
        final String itemId;

        ReflectedProjectileInfo(String itemId, long expireTime) {
            this.itemId = itemId;
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
     * 记录待反射的弹射物（每个 active reflect 实例各记录一条，反射时各用各自物品定义的参数）
     * @param player 玩家
     * @param projectile 弹射物
     * @param itemId 提供该 reflect 实例的物品 ID（数值按该物品实例取）
     */
    public static void recordProjectileForReflect(Player player, Projectile projectile, String itemId) {
        if (player.level().isClientSide()) {
            return;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return;
        }

        if (projectile.getOwner() == player) {
            return;
        }

        // 跳过已反射的弹射物，防止二次反射
        if (REFLECTED_PROJECTILES.containsKey(projectile.getId())) {
            return;
        }

        List<ProjectileDamageInfo> infos = PENDING_REFLECT_INFOS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        // 同一（弹射物, 物品实例）对只记录一条：不同 reflect 实例各记录一条（各用各自物品参数），重复伤害事件不重复记录
        for (ProjectileDamageInfo existing : infos) {
            if (existing.getProjectile() == projectile && java.util.Objects.equals(existing.getItemId(), itemId)) {
                return;
            }
        }
        infos.add(createProjectileDamageInfo(projectile, itemId));
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
     * 在护盾受伤后处理反射
     * @param player 玩家
     */
    public static void processReflectAfterShieldDamage(Player player) {
        processReflectAfterShieldDamage(player, player);
    }

    /**
     * 在护盾受伤后处理反射：每个待反射实例各自反射一个弹射物（各用各自物品定义的参数）
     * @param player 玩家
     * @param attackedEntity 被攻击的实体（可能是玩家或被护盾保护的实体）
     */
    public static void processReflectAfterShieldDamage(Player player, LivingEntity attackedEntity) {
        List<ProjectileDamageInfo> infos = PENDING_REFLECT_INFOS.remove(player.getUUID());
        if (infos == null || infos.isEmpty()) {
            return;
        }
        // 弹射物本体已失效（如已撞地消失）则不反射
        if (!infos.get(0).getProjectile().isAlive()) {
            return;
        }
        for (ProjectileDamageInfo info : infos) {
            reflectProjectile(info.getProjectile(), player, attackedEntity, info);
        }
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
        // 数值按提供该 reflect 实例的物品取（每实例独立）
        double speedBaseModifier = DefsManager.resolveShieldTypeValueForItem(player.getServer(), info.getItemId(),
                "reflect", "speed_base_modifier", Config.getReflectSpeedBaseModifier());
        double speedExtraModifier = DefsManager.resolveShieldTypeValueForItem(player.getServer(), info.getItemId(),
                "reflect", "speed_extra_modifier", Config.getReflectSpeedExtraModifier());
        double speedModifier = speedBaseModifier * (1 + speedExtraModifier * shieldEffect_radius);

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

        // 先登记反射记录再加入世界：EntityJoinLevelEvent（点射注册等）在 addFreshEntity 内同步触发，
        // 届时需能通过 ShieldTypeManager.isReflectedProjectile 查到本弹射物
        long expireTime = System.currentTimeMillis() + REFLECTED_PROJECTILE_EXPIRE_TIME;
        REFLECTED_PROJECTILES.put(reflected.getId(), new ReflectedProjectileInfo(info.getItemId(), expireTime));

        player.level().addFreshEntity(reflected);
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
     * @param itemId 提供该 reflect 实例的物品 ID
     * @return 弹射物伤害信息
     */
    private static ProjectileDamageInfo createProjectileDamageInfo(Projectile projectile, String itemId) {
        return new ProjectileDamageInfo(
            projectile,
            projectile.getDeltaMovement().length(),
            projectile.getX(), projectile.getY(), projectile.getZ(),
            itemId
        );
    }

    @Override
    public void onTick(Player player) {
    }

    /**
     * 处理免疫判定事件
     * - 当反射弹射物击中免疫该伤害类型的实体时（如烈焰人免疫火焰），
     *   强制绕过免疫判定，使伤害流程继续到 LivingIncomingDamageEvent，
     *   以便 onLivingAttack 能将伤害类型转换为爆炸伤害
     */
    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (!event.isInvulnerable()) {
            return;
        }

        DamageSource source = event.getSource();
        Entity directEntity = source.getDirectEntity();
        if (!(directEntity instanceof Projectile projectile)) {
            return;
        }

        if (!REFLECTED_PROJECTILES.containsKey(projectile.getId())) {
            return;
        }

        // 反射弹射物：绕过目标实体的伤害类型免疫
        event.setInvulnerable(false);
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
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
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
        ReflectedProjectileInfo reflectedInfo = REFLECTED_PROJECTILES.remove(projectileId);

        LivingEntity target = (LivingEntity) event.getEntity();

        float originalDamage = event.getAmount();
        double shieldEffect = AttributeManager.getGroupAttribute(attacker.getUUID(), "shield_effect") - 1;
        // 数值按产生该反射弹射物的实例物品取
        double damageEffectMultiplier = DefsManager.resolveShieldTypeValueForItem(attacker.getServer(),
                reflectedInfo != null ? reflectedInfo.itemId : null,
                "reflect", "damage_effect_multiplier", Config.getReflectDamageEffectMultiplier());
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
        PENDING_REFLECT_INFOS.remove(playerUUID);
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
        PENDING_REFLECT_INFOS.clear();
        REFLECTED_PROJECTILES.clear();
        EXPLOSION_PREVENTION_PROJECTILES.clear();
    }

    static {
        com.gytrinket.gytrinket.core.TickScheduler.register("reflect_cleanup", 20, () -> {
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