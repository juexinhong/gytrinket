package com.gytrinket.gytrinket.client.attack_mode;

import com.gytrinket.gytrinket.client.datacenter.ClientDataCenter;
import com.gytrinket.gytrinket.compat.CuriosCompat;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.function.Predicate;

/**
 * 攻击模式客户端工具类
 * <p>
 * 提取各攻击模式客户端处理器中的公共方法，避免重复代码。
 * <p>
 * 客户端物品快照（服务端 NBT 同步）仅含光点核心、不含 Curios 饰品栏，
 * 本类在客户端本地周期刷新 Curios 饰品到快照缓存，
 * 使 {@link #hasActiveItem(Predicate)} 的判定范围与服务端一致（光点核心 + Curios）。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class AttackModeClientUtil {

    /** Curios 饰品栏本地缓存刷新周期（tick） */
    private static final int CURIOS_REFRESH_INTERVAL = 10;

    private static int tickCounter = 0;

    private AttackModeClientUtil() {}

    /**
     * 客户端 tick 周期刷新 Curios 饰品栏到本地快照缓存
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter < CURIOS_REFRESH_INTERVAL) {
            return;
        }
        tickCounter = 0;
        refreshCuriosSnapshot();
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        tickCounter = 0;
        refreshCuriosSnapshot();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDataCenter.getSnapshot().updateCuriosItems(List.of());
    }

    /** 从客户端本地 Curios 饰品栏刷新快照缓存 */
    private static void refreshCuriosSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isRemoved()) {
            return;
        }
        List<ItemStack> curios = CuriosCompat.getEquippedCurios(player);
        ClientDataCenter.getSnapshot().updateCuriosItems(curios);
    }

    /**
     * 寻找准星对准的目标
     */
    public static Entity findTargetInCrosshair(Player player) {
        return findTargetInCrosshair(player, false);
    }

    /**
     * 寻找准星对准的目标
     * @param livingOnly true=仅查找生物目标（忽略原版准星指向的非生物无效目标，直接光束查找最近生物）；
     *                   false=非生物目标（展示框/画等）仍依赖原版准星精确指向
     */
    public static Entity findTargetInCrosshair(Player player, boolean livingOnly) {
        Minecraft mc = Minecraft.getInstance();
        // 非生物目标（展示框/画等）仍依赖原版准星精确指向；生物目标由下方光束判定覆盖
        if (!livingOnly && mc.hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            if (!(entity instanceof LivingEntity) && !shouldSkipEntity(entity)) {
                return entity;
            }
        }

        // 光束炮式矩形光束判定（与服务端 ChargedAttackSweepHandler 同一算法）：
        // 宽/高容差判定柱，长度 = 实体交互距离 + 附加 0.5 格
        double reachDistance = player.entityInteractionRange() + ChargedAttackSweepHandler.CHARGE_EXTRA_REACH;
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

            if (!ChargedAttackSweepHandler.isEntityHitByBeam(entity, eyePos, endPos)) {
                continue;
            }

            // 距离排序：实体盒中心沿视线轴的投影长度（取最近）
            double alongAxis = entity.getBoundingBox().getCenter().subtract(eyePos).dot(lookVec);
            if (alongAxis < closestDistance) {
                closestDistance = alongAxis;
                closestEntity = entity;
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
     * 检查玩家是否拥有指定类型的物品（范围：光点核心快照 + Curios 饰品栏本地缓存）
     */
    public static boolean hasActiveItem(Predicate<Item> itemPredicate) {
        var snapshot = ClientDataCenter.getSnapshot();
        for (int i = 0; i < snapshot.getSlotCount(); i++) {
            ItemStack stack = snapshot.getItemInSlot(i);
            if (!stack.isEmpty() && itemPredicate.test(stack.getItem())) {
                return true;
            }
        }
        for (int i = 0; i < snapshot.getCuriosSlotCount(); i++) {
            ItemStack stack = snapshot.getCuriosItemInSlot(i);
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
