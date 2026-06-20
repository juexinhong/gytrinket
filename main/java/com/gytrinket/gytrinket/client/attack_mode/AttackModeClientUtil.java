package com.gytrinket.gytrinket.client.attack_mode;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.client.datacenter.ClientDataCenter;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * 攻击模式客户端工具类
 * <p>
 * 提取各攻击模式客户端处理器中的公共方法，避免重复代码。
 */
public class AttackModeClientUtil {

    private AttackModeClientUtil() {}

    /**
     * 寻找准星对准的目标
     */
    public static Entity findTargetInCrosshair(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            if (!shouldSkipEntity(entity)) {
                return entity;
            }
        }

        double reachDistance = player.entityInteractionRange();
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity && e.isAlive() && e != player);

        Entity closestEntity = null;
        double closestDistance = reachDistance;

        for (Entity entity : entities) {
            if (shouldSkipEntity(entity)) {
                continue;
            }

            AABB entityBox = entity.getBoundingBox().inflate(0.5);
            var clipResult = entityBox.clip(eyePos, endPos);
            if (clipResult.isPresent()) {
                double distance = eyePos.distanceTo(clipResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }

    /**
     * 重置玩家攻击强度计时器为0
     */
    public static void resetAttackStrengthTicker(Player player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            gytrinket.LOGGER.warn("Failed to reset attack strength ticker", e);
        }
    }

    /**
     * 使用反射设置玩家攻击强度为满
     */
    public static void reflectAttackStrengthToFull(Player player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            gytrinket.LOGGER.warn("Failed to reflect attack strength to full via reflection", e);
        }
    }

    /**
     * 判断实体是否应被跳过（不作为攻击目标）
     */
    public static boolean shouldSkipEntity(Entity entity) {
        return entity instanceof com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
    }

    /**
     * 检查玩家是否拥有指定类型的物品
     */
    public static boolean hasActiveItem(Predicate<net.minecraft.world.item.Item> itemPredicate) {
        var snapshot = ClientDataCenter.getSnapshot();
        for (int i = 0; i < snapshot.getSlotCount(); i++) {
            net.minecraft.world.item.ItemStack stack = snapshot.getItemInSlot(i);
            if (!stack.isEmpty() && itemPredicate.test(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查玩家是否拥有充能攻击物品
     */
    public static boolean hasChargedAttackItem() {
        return hasActiveItem(Config::isChargedAttackItem);
    }

    /**
     * 检查玩家是否拥有强袭物品
     */
    public static boolean hasAssaultItem() {
        return hasActiveItem(Config::isAssaultItem);
    }
}
