package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.core.entity.construct.IConstructEntity;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * 构造体碰撞穿透事件处理器
 * <p>
 * 玩家的攻击（弹射物和近战）穿透自己的所有构造体。
 * <p>
 * 1. 弹射物穿透：玩家/友方弹射物命中自己的构造体时，取消碰撞事件，弹射物继续飞行
 *    - 在客户端和服务端都取消，避免客户端先显示命中再被服务端纠正的视觉问题
 * 2. 近战穿透：玩家近战攻击自己的构造体时，取消攻击，重新做视线追踪（排除构造体），对命中实体执行攻击
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ConstructPenetrationHandler {

    /**
     * 弹射物穿透：玩家/友方弹射物命中自己的构造体时，取消碰撞
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)) return;

        Entity hitEntity = entityHitResult.getEntity();
        if (!isConstruct(hitEntity)) return;

        Entity projectile = event.getEntity();
        if (!(projectile instanceof Projectile proj)) return;
        Entity projOwner = proj.getOwner();

        UUID constructOwnerUUID = getConstructOwnerUUID(hitEntity);
        if (constructOwnerUUID == null) return;

        // 弹射物所有者是构造体所属玩家 → 穿透
        if (projOwner instanceof Player player) {
            if (constructOwnerUUID.equals(player.getUUID())) {
                event.setCanceled(true);
                return;
            }
        }

        // 弹射物所有者是同一玩家的构造体 → 穿透
        if (isConstruct(projOwner)) {
            UUID shooterOwnerUUID = getConstructOwnerUUID(projOwner);
            if (shooterOwnerUUID != null && shooterOwnerUUID.equals(constructOwnerUUID)) {
                event.setCanceled(true);
                return;
            }
        }

        // 弹射物所有者就是被命中的构造体自身 → 穿透
        if (projOwner == hitEntity) {
            event.setCanceled(true);
        }
    }

    /**
     * 近战穿透：玩家近战攻击自己的构造体时，取消攻击，
     * 从玩家视线方向重新做射线追踪（排除构造体），对命中的新实体执行攻击
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        // 只处理玩家攻击自己的构造体
        if (!isConstruct(target)) return;
        UUID constructOwnerUUID = getConstructOwnerUUID(target);
        if (constructOwnerUUID == null || !constructOwnerUUID.equals(player.getUUID())) return;

        // 取消对构造体的攻击
        event.setCanceled(true);

        // 从玩家视线方向重新做射线追踪，排除构造体
        Entity newTarget = raytraceExcludingOwnConstructs(player);
        if (newTarget != null && newTarget.isAlive()) {
            player.attack(newTarget);
        }
    }

    /**
     * 判断实体是否是构造体
     */
    private static boolean isConstruct(Entity entity) {
        return entity instanceof IConstructEntity;
    }

    /**
     * 获取构造体的所有者UUID
     */
    private static UUID getConstructOwnerUUID(Entity construct) {
        if (construct instanceof IConstructEntity constructEntity) {
            return constructEntity.getOwnerUUID();
        }
        return null;
    }

    /**
     * 从玩家视线方向做射线追踪，排除玩家自己的所有构造体
     */
    private static Entity raytraceExcludingOwnConstructs(Player player) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double reachDistance = player.getEntityReach();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        UUID playerUUID = player.getUUID();

        // 获取视线范围内所有实体
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchBox,
                entity -> entity != player && entity.isAlive() && entity.isPickable());

        Entity closestEntity = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : entities) {
            // 跳过玩家自己的构造体
            if (isConstruct(entity)) {
                UUID ownerUUID = getConstructOwnerUUID(entity);
                if (ownerUUID != null && ownerUUID.equals(playerUUID)) {
                    continue;
                }
            }

            // 计算视线与实体碰撞箱的交点
            AABB entityBB = entity.getBoundingBox().inflate(0.3);
            Vec3 intersection = entityBB.clip(eyePos, endPos).orElse(null);
            if (intersection != null) {
                double dist = eyePos.distanceToSqr(intersection);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }
}
