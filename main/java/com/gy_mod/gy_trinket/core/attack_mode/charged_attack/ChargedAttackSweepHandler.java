package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.core.entity.construct.IConstructEntity;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 充能攻击横扫增强处理
 * <p>
 * 充能释放时使用剑类物品：
 * 1. 必定触发横扫攻击（无视冲刺、移动等原版限制）
 * 2. 横扫伤害根据充能值提升（每点充能值+10%，最高100%加成）
 * 3. 横扫范围根据充能值扩大（每点+10%，无上限）
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackSweepHandler {

    private ChargedAttackSweepHandler() {}

    /**
     * 判断物品是否支持横扫动作（剑类）
     */
    public static boolean isSwordItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    /**
     * 计算充能横扫伤害倍率
     * 每点充能值提升10%横扫伤害，最高100%加成
     *
     * @param chargeValue 充能值
     * @return 横扫伤害倍率（1.0 = 无加成，2.0 = 100%加成）
     */
    public static float getSweepDamageMultiplier(double chargeValue) {
        float bonus = (float) Math.min(chargeValue * 0.1, 1.0);
        return 1.0F + bonus;
    }

    /**
     * 计算充能横扫范围倍率
     * 每点充能值提升10%横扫范围，无上限
     *
     * @param chargeValue 充能值
     * @return 横扫范围倍率（1.0 = 无扩大）
     */
    public static float getSweepRangeMultiplier(double chargeValue) {
        return 1.0F + (float) (chargeValue * 0.1);
    }

    /**
     * 执行充能横扫攻击（替代原版attack+补伤机制）
     * <p>
     * 不使用Mixin注入，而是在服务端直接执行自定义扇形范围伤害：
     * 1. 对主要命中目标施加全额充能伤害
     * 2. 对扇形范围内的其他实体施加横扫伤害
     * 3. 发送自定义横扫粒子
     * 4. 处理击退效果
     */
    public static void executeChargedSweepAttack(ServerPlayer player, double chargeValue) {
        if (chargeValue <= 0) {
            return;
        }

        // 查找准星对准的主要目标
        Entity primaryTarget = findTargetInCrosshair(player);
        if (primaryTarget == null) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 对主要目标施加全额充能伤害
        float baseDamage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float chargedDamage = baseDamage * (1.0F + (float) chargeValue);

        // 移除无敌时间确保伤害生效
        if (primaryTarget instanceof LivingEntity livingTarget) {
            livingTarget.invulnerableTime = 0;
        }

        primaryTarget.hurt(player.damageSources().playerAttack(player), chargedDamage);

        // 计算横扫参数
        float rangeMultiplier = getSweepRangeMultiplier(chargeValue);
        float sweepDamageMultiplier = getSweepDamageMultiplier(chargeValue);
        float sweepDamage = baseDamage * 0.15F * sweepDamageMultiplier;

        // 扇形范围搜索
        double entityReach = player.getEntityReach();
        double expandedDist = entityReach * rangeMultiplier;
        AABB searchBox = player.getBoundingBox().inflate(expandedDist + 1.0);
        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox);

        // 玩家视线方向
        Vec3 lookVec = player.getLookAngle();
        double expandedDistLimit = expandedDist * expandedDist + 1.0;

        // 先收集横扫目标到集合，避免遍历时伤害导致并发修改
        List<LivingEntity> sweepTargets = new ArrayList<>();
        for (LivingEntity livingEntity : nearbyEntities) {
            if (livingEntity == primaryTarget || livingEntity == player) {
                continue;
            }
            if (player.isAlliedTo(livingEntity)) {
                continue;
            }
            // 过滤自己的构造体
            if (isOwnConstruct(livingEntity, player)) {
                continue;
            }

            // 距离检查
            double distSqr = player.distanceToSqr(livingEntity);
            if (distSqr > expandedDistLimit) {
                continue;
            }

            // 前方判定：实体必须在玩家面前
            Vec3 toEntity = livingEntity.position().subtract(player.position());
            if (toEntity.dot(lookVec) <= 0) {
                continue;
            }

            sweepTargets.add(livingEntity);
        }

        // 统一对横扫目标施加伤害和击退
        double kbX = primaryTarget.getX() - player.getX();
        double kbZ = primaryTarget.getZ() - player.getZ();
        for (LivingEntity livingEntity : sweepTargets) {
            // 移除无敌时间确保伤害生效
            livingEntity.invulnerableTime = 0;

            livingEntity.hurt(player.damageSources().playerAttack(player), sweepDamage);

            // 击退效果（横扫击退）
            livingEntity.knockback(0.4F, -kbX, -kbZ);
        }

        // 发送横扫粒子
        sendChargedSweepParticle(player, chargeValue, rangeMultiplier);

        // 消耗攻击强度
        player.resetAttackStrengthTicker();
    }

    /**
     * 查找玩家准星对准的目标
     */
    private static Entity findTargetInCrosshair(ServerPlayer player) {
        double reachDistance = player.getEntityReach();
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, searchBox, e ->
                e instanceof LivingEntity && e.isAlive() && e != player && !isOwnConstruct(e, player));

        Entity closestEntity = null;
        double closestDistance = reachDistance;

        for (Entity entity : entities) {
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
     * 判断实体是否为玩家自己的构造体
     */
    private static boolean isOwnConstruct(Entity entity, Player player) {
        if (entity instanceof IConstructEntity constructEntity) {
            UUID ownerUUID = constructEntity.getOwnerUUID();
            return ownerUUID != null && ownerUUID.equals(player.getUUID());
        }
        return false;
    }

    /**
     * 发送充能横扫粒子到所有可见此玩家的客户端
     */
    private static void sendChargedSweepParticle(ServerPlayer player, double chargeValue, float rangeMultiplier) {
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        float pitch = player.getXRot() * ((float) Math.PI / 180F);
        float cosPitch = Mth.cos(pitch);

        double forwardShift = rangeMultiplier > 1.0F ? (rangeMultiplier - 1.0F) * 0.5 : 0;

        double lookX = -Mth.sin(yaw) * cosPitch;
        double lookY = -Mth.sin(pitch);
        double lookZ = Mth.cos(yaw) * cosPitch;
        double distance = 1.5 + forwardShift;

        double baseX = player.getX() + lookX * distance;
        double baseY = player.getY() + player.getEyeHeight() - 0.5 + lookY * distance;
        double baseZ = player.getZ() + lookZ * distance;

        NetworkHandler.sendChargedSweepParticleToAll(
                player, baseX, baseY, baseZ,
                yaw, pitch, rangeMultiplier,
                player.level().getGameTime(), 4
        );
    }
}
