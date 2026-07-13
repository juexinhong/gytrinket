package com.gy_mod.gy_trinket.core.hostile_target;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.IConstructEntity;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 *   <li>玩家标记实体：被玩家攻击过的实体（持续5秒）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
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
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
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
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
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
     *   <li>被玩家攻击过的实体（持续5秒）</li>
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