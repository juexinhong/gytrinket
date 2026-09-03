package com.gytrinket.gytrinket.core.entity.construct;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.entity.construct.IConstructEntity;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敌对目标管理器
 * <p>
 * 提供统一的敌对实体判断方法，用于检测对玩家有威胁的实体。
 * 主要用于增幅护盾、光环护盾等需要检测周围威胁的系统。
 * 
 * <p>检测类型：
 * <ul>
 *   <li>敌对实体：MONSTER类别的生物（如僵尸、骷髅）</li>
 *   <li>仇恨实体：以玩家为攻击目标的生物</li>
 *   <li>危险实体：配置文件中定义的危险实体（如箭矢、烈焰弹）</li>
 *   <li>玩家标记实体：被玩家攻击过的实体（持续时间由配置决定）</li>
 * </ul>
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class HostileTargetManager {

    private static final Map<UUID, Map<UUID, Long>> PLAYER_MARKED_ENTITIES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        if (player == null || target == null) {
            return;
        }

        markEntity(player.getUUID(), target.getUUID());
    }

    private static void markEntity(UUID playerUUID, UUID targetUUID) {
        long expireTime = getCurrentTick() + Config.getHostileTargetMarkDuration();
        
        PLAYER_MARKED_ENTITIES.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
            .put(targetUUID, expireTime);
    }

    private static boolean isEntityMarkedByPlayer(UUID playerUUID, UUID targetUUID) {
        Map<UUID, Long> markedEntities = PLAYER_MARKED_ENTITIES.get(playerUUID);
        if (markedEntities == null) {
            return false;
        }

        Long expireTime = markedEntities.get(targetUUID);
        if (expireTime == null) {
            return false;
        }

        if (getCurrentTick() > expireTime) {
            markedEntities.remove(targetUUID);
            return false;
        }

        return true;
    }

    private static long getCurrentTick() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getTickCount() : 0;
    }

    /**
     * 判断实体是否为敌对实体（MONSTER类别）
     * <p>
     * MONSTER类别包括：僵尸、骷髅、苦力怕、末影人等传统敌对生物。
     * 
     * @param entity 待判断的实体
     * @return 是否为敌对实体
     */
    public static boolean isHostileEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        
        LivingEntity living = (LivingEntity) entity;
        return living.getType().getCategory() == MobCategory.MONSTER;
    }

    /**
     * 判断实体是否对玩家有仇恨
     * <p>
     * 检测生物的攻击目标是否为玩家，适用于中立生物（如狼、铁傀儡）
     * 被激怒后攻击玩家的情况。
     * 
     * @param entity 待判断的实体
     * @param player 玩家
     * @return 是否对玩家有仇恨
     */
    public static boolean isHostileToPlayer(Entity entity, Player player) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        
        Mob mob = (Mob) entity;
        LivingEntity target = mob.getTarget();
        
        // 检查是否以当前玩家为目标
        if (target == player) {
            return true;
        }
        
        // 检查是否以任意玩家为目标
        if (target != null && target instanceof Player) {
            return true;
        }
        
        return false;
    }

    /**
     * 判断实体是否对玩家保护的实体有仇恨
     * <p>
     * 当玩家将护盾移植给其他实体时，攻击该实体的生物被视为对玩家有威胁。
     * 
     * @param entity 待判断的实体
     * @param player 玩家
     * @return 是否对玩家保护的实体有仇恨
     */
    public static boolean isHostileToProtectedEntity(Entity entity, Player player) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        
        Mob mob = (Mob) entity;
        LivingEntity target = mob.getTarget();
        
        // 如果目标为空，返回false
        if (target == null) {
            return false;
        }
        
        // 检查目标是否是玩家保护的实体
        return isEntityProtectedByPlayer(target, player);
    }

    /**
     * 判断实体是否为配置中的危险实体
     * <p>
     * 危险实体列表在Config中配置，包括箭矢、烈焰弹、药水瓶等。
     * 
     * @param entity 待判断的实体
     * @return 是否为危险实体
     */
    public static boolean isDangerousEntity(Entity entity) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && Config.isDangerousEntity(key.toString());
    }

    /**
     * 判断实体是否应该攻击玩家（综合判断）
     * <p>
     * 综合以下条件，满足任一即为威胁：
     * <ol>
     *   <li>对玩家或玩家保护的实体有仇恨（最高优先级）</li>
     *   <li>是敌对实体（MONSTER类别）</li>
     *   <li>是配置中的危险实体</li>
     *   <li>被玩家攻击过的实体（持续时间由配置决定）</li>
     * </ol>
     * 
     * @param entity 待判断的实体
     * @param player 玩家
     * @return 是否应该攻击玩家
     */
    public static boolean shouldAttackPlayer(Entity entity, Player player) {
        // 空实体或已死亡
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        
        if (player == null) {
            return isDangerousEntity(entity);
        }
        
        // 玩家自己
        if (entity == player) {
            return false;
        }
        
        // 检查是否是玩家自身移植护盾的实体（不应该攻击自己移植的实体）
        if (isEntityProtectedByPlayer(entity, player)) {
            return false;
        }

        // 检查是否是归属玩家的实体（如无人机等构造体）
        if (isEntityOwnedByPlayer(entity, player)) {
            return false;
        }

        // 玩家（或其构造体）发射的弹射物：即使是配置危险实体也不视为威胁（归属优先于危险物配置）
        if (isFriendlyProjectile(entity, player)) {
            return false;
        }

        // 落地的箭矢：已失去飞行威胁，不再计入危险物
        if (isArrowGrounded(entity)) {
            return false;
        }
        
        // 对玩家有仇恨的实体（最高优先级）
        if (isHostileToPlayer(entity, player)) {
            return true;
        }
        
        // 对玩家保护的实体有仇恨（攻击保护对象的敌人也视为威胁）
        if (isHostileToProtectedEntity(entity, player)) {
            return true;
        }
        
        // 敌对生物
        if (isHostileEntity(entity)) {
            return true;
        }
        
        // 配置中的危险实体
        if (isDangerousEntity(entity)) {
            return true;
        }
        
        // 被玩家攻击过的实体（持续5秒）
        if (isEntityMarkedByPlayer(player.getUUID(), entity.getUUID())) {
            return true;
        }
        
        return false;
    }

    /**
     * 判断实体是否被玩家的护盾移植保护
     * <p>
     * 如果实体是玩家移植护盾的目标，则不应该被视为威胁。
     * 
     * @param entity 待判断的实体
     * @param player 玩家
     * @return 是否被玩家的护盾移植保护
     */
    public static boolean isEntityProtectedByPlayer(Entity entity, Player player) {
        if (player == null) {
            return false;
        }
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) {
            return false;
        }
        
        UUID ownerUUID = ShieldTransferManager.getShieldOwnerUUID((net.minecraft.world.entity.LivingEntity) entity);
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    /**
     * 判断实体是否归属该玩家
     * <p>
     * 归属玩家的实体（如无人机等构造体）不应被视为威胁。
     *
     * @param entity 待判断的实体
     * @param player 玩家
     * @return 是否归属该玩家
     */
    public static boolean isEntityOwnedByPlayer(Entity entity, Player player) {
        if (player == null) {
            return false;
        }
        return isEntityOwnedByPlayer(entity, player.getUUID());
    }

    /**
     * 判断实体是否归属指定UUID的玩家
     */
    public static boolean isEntityOwnedByPlayer(Entity entity, UUID playerUUID) {
        if (playerUUID == null) {
            return false;
        }
        if (entity instanceof IConstructEntity construct) {
            UUID ownerUUID = construct.getOwnerUUID();
            return ownerUUID != null && ownerUUID.equals(playerUUID);
        }
        return false;
    }

    /** AbstractArrow.inGround 为 protected 字段，通过反射读取（兼容混淆名）；读取失败时退化为速度判定 */
    private static Field ARROW_IN_GROUND_FIELD;

    static {
        // inGround：MojMap 名（开发/1.21.1 运行时）+ SRG 名（1.20.1 生产环境，f_36703_=inGround）
        // 注意 f_36704_ 是 inGroundTime(int)，绝不能作为候选（getBoolean 会抛 IllegalArgumentException）
        for (String fieldName : new String[]{"inGround", "f_36703_"}) {
            try {
                Field f = AbstractArrow.class.getDeclaredField(fieldName);
                if (f.getType() != boolean.class) {
                    continue;
                }
                f.setAccessible(true);
                ARROW_IN_GROUND_FIELD = f;
                break;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 判断弹射物是否由玩家（或其所属构造体）发射
     * <p>
     * 归属玩家的弹射物即使是配置中的危险实体（箭矢、火球等）也不应视为威胁。
     */
    private static boolean isFriendlyProjectile(Entity entity, Player player) {
        if (player == null || !(entity instanceof Projectile projectile)) {
            return false;
        }
        Entity owner = projectile.getOwner();
        if (owner == null) {
            return false;
        }
        return owner == player || isEntityOwnedByPlayer(owner, player);
    }

    /**
     * 判断箭矢是否处于落地状态（inGround）
     * <p>
     * 落地箭矢已失去飞行威胁，不应再计入危险物。
     * AbstractArrow.inGround 为 protected，通过反射读取；反射不可用时退化为速度近似判定。
     */
    private static boolean isArrowGrounded(Entity entity) {
        if (!(entity instanceof AbstractArrow arrow)) {
            return false;
        }
        if (ARROW_IN_GROUND_FIELD != null) {
            try {
                return ARROW_IN_GROUND_FIELD.getBoolean(arrow);
            } catch (Exception ignored) {
                // 类型不符/访问失败等一律退化为速度判定，绝不能让异常冒泡崩溃
            }
        }
        Vec3 velocity = arrow.getDeltaMovement();
        double speedSquared = velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z;
        return speedSquared < 0.01;
    }

    /**
     * 判断实体是否为中立生物
     * <p>
     * 中立生物包括：动物、水生生物、环境生物等通常不会主动攻击玩家的生物。
     * 
     * @param entity 待判断的实体
     * @return 是否为中立生物
     */
    public static boolean isNeutralEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        
        LivingEntity living = (LivingEntity) entity;
        MobCategory category = living.getType().getCategory();
        
        return category == MobCategory.CREATURE || 
               category == MobCategory.WATER_CREATURE || 
               category == MobCategory.WATER_AMBIENT ||
               category == MobCategory.AMBIENT;
    }

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_MARKED_ENTITIES.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_MARKED_ENTITIES.clear();
    }
}