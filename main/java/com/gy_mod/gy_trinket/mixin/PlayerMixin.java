package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Player Mixin - 充能横扫增强
 * <p>
 * 1. 强制横扫：充能攻击时即使冲刺也触发横扫（对应1.21.1的SweepAttackEvent.setSweeping(true)）
 * 2. 搜索 AABB：沿玩家视线方向前移并扩大
 * 3. 距离检查扩展：attack()结束后，对扩展范围内未被原版距离检查命中的实体补伤
 * 4. 横扫粒子：取消原版粒子，在自定义位置生成放大粒子
 */
@Mixin(Player.class)
public class PlayerMixin {

    /**
     * 充能攻击时强制触发横扫：将isSprinting()返回false
     * 原版中冲刺时不触发横扫，充能攻击时应无视此限制
     * 对应1.21.1的 SweepAttackEvent.setSweeping(true)
     */
    @Redirect(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z")
    )
    private boolean gytrinket$forceSweepOnChargedAttack(Player instance) {
        if (ChargedAttackManager.hasChargedAttack(instance)) {
            double chargeValue = ChargedAttackDamageTracker.getChargeValue(instance.getUUID());
            if (chargeValue > 0 && ChargedAttackSweepHandler.isSwordItem(instance.getMainHandItem())) {
                return false;
            }
        }
        return instance.isSprinting();
    }

    /**
     * 在 attack() 开头记录主要攻击目标，用于区分直接攻击和横扫目标
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void gytrinket$recordPrimaryTarget(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        UUID playerUUID = player.getUUID();
        ChargedAttackSweepHandler.setPrimaryTarget(playerUUID, target);
        ChargedAttackSweepHandler.startSweepAttack(playerUUID);
    }

    /**
     * 在 attack() 结尾：
     * 1. 清除主要攻击目标记录
     * 2. 对充能横扫扩展范围内未被距离检查命中的实体补伤
     */
    @Inject(method = "attack", at = @At("RETURN"))
    private void gytrinket$clearPrimaryTargetAndApplyExpandedSweep(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        UUID playerUUID = player.getUUID();

        ChargedAttackSweepHandler.removePrimaryTarget(playerUUID);
        ChargedAttackSweepHandler.endSweepAttack(playerUUID);

        // 充能横扫扩展范围补伤
        if (player.level().isClientSide) {
            return;
        }

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(playerUUID);
        if (chargeValue <= 0) {
            return;
        }

        float rangeMultiplier = ChargedAttackSweepHandler.getSweepRangeMultiplier(chargeValue);
        if (rangeMultiplier <= 1.0F) {
            return;
        }

        // 仅在横扫攻击时补伤（主手为剑类）
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof SwordItem)) {
            return;
        }

        // 计算横扫伤害
        float baseDamage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float sweepDamage = baseDamage * 0.15F * ChargedAttackSweepHandler.getSweepDamageMultiplier(chargeValue);

        // 搜索扩展范围内的实体
        double entityReach = player.getEntityReach();
        double expandedDist = entityReach * rangeMultiplier;
        AABB searchBox = player.getBoundingBox().inflate(expandedDist + 1.0);
        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox);

        // 玩家视线方向，用于前方判定（与1.21.1一致：只命中面前的敌人）
        Vec3 lookVec = player.getLookAngle();
        double expandedDistLimit = expandedDist * expandedDist + 1.0;

        for (LivingEntity livingEntity : nearbyEntities) {
            if (livingEntity == target || livingEntity == player) {
                continue;
            }
            if (player.isAlliedTo(livingEntity)) {
                continue;
            }

            // 已被原版横扫命中，跳过（通过伤害追踪）
            if (ChargedAttackSweepHandler.isEntitySwept(playerUUID, livingEntity.getId())) {
                continue;
            }

            // 距离检查
            double distSqr = player.distanceToSqr(livingEntity);
            if (distSqr > expandedDistLimit) {
                continue;
            }

            // 前方判定：实体相对于玩家的方向与视线方向的点积必须为正（面前的实体）
            Vec3 toEntity = livingEntity.position().subtract(player.position());
            if (toEntity.dot(lookVec) <= 0) {
                continue;
            }

            livingEntity.hurt(player.damageSources().playerAttack(player), sweepDamage);
        }
    }

    /**
     * 修改横扫搜索范围的 AABB
     */
    @ModifyArg(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            ),
            index = 1
    )
    private AABB gytrinket$expandSweepRange(AABB aabb) {
        Player player = (Player) (Object) this;

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return aabb;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(player.getUUID());
        if (chargeValue <= 0) {
            return aabb;
        }

        float rangeMultiplier = ChargedAttackSweepHandler.getSweepRangeMultiplier(chargeValue);
        if (rangeMultiplier <= 1.0F) {
            return aabb;
        }

        // 沿玩家视线方向前移 AABB 中心
        Vec3 lookVec = player.getLookAngle();
        double forwardRadius = Math.abs(lookVec.x) * (aabb.maxX - aabb.minX)
                             + Math.abs(lookVec.y) * (aabb.maxY - aabb.minY)
                             + Math.abs(lookVec.z) * (aabb.maxZ - aabb.minZ);
        forwardRadius /= 2.0;
        double forwardShift = forwardRadius * (rangeMultiplier - 1.0F) * 0.5;

        double centerX = (aabb.minX + aabb.maxX) / 2.0 + lookVec.x * forwardShift;
        double centerY = (aabb.minY + aabb.maxY) / 2.0 + lookVec.y * forwardShift;
        double centerZ = (aabb.minZ + aabb.maxZ) / 2.0 + lookVec.z * forwardShift;

        double halfX = (aabb.maxX - aabb.minX) / 2.0 * rangeMultiplier;
        double halfY = (aabb.maxY - aabb.minY) / 2.0 * rangeMultiplier;
        double halfZ = (aabb.maxZ - aabb.minZ) / 2.0 * rangeMultiplier;

        return new AABB(
                centerX - halfX, centerY - halfY, centerZ - halfZ,
                centerX + halfX, centerY + halfY, centerZ + halfZ
        );
    }

    /**
     * 充能横扫时：取消原版粒子，通过网络包发送自定义渲染数据到客户端
     */
    @Inject(method = "sweepAttack", at = @At("HEAD"), cancellable = true)
    private void gytrinket$replaceSweepParticle(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(player.getUUID());
        if (chargeValue <= 0) {
            return;
        }

        // 取消原版粒子生成
        ci.cancel();

        // 只在服务端发送网络包
        if (player.level().isClientSide) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        float rangeMultiplier = ChargedAttackSweepHandler.getSweepRangeMultiplier(chargeValue);

        // 计算前移距离
        double forwardShift = rangeMultiplier > 1.0F ? (rangeMultiplier - 1.0F) * 0.5 : 0;

        // 使用玩家完整视线方向（含俯仰角）计算粒子位置
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        float pitch = player.getXRot() * ((float) Math.PI / 180F);
        float cosPitch = Mth.cos(pitch);

        // 沿视线方向延伸 1.5 格 + 充能前移
        double lookX = -Mth.sin(yaw) * cosPitch;
        double lookY = -Mth.sin(pitch);
        double lookZ = Mth.cos(yaw) * cosPitch;
        double distance = 1.5 + forwardShift;

        double baseX = player.getX() + lookX * distance;
        double baseY = player.getY() + player.getEyeHeight() - 0.5 + lookY * distance;
        double baseZ = player.getZ() + lookZ * distance;

        // 通过网络包发送渲染数据到所有可见此玩家的客户端（包括自己）
        NetworkHandler.sendChargedSweepParticleToAll(
                serverPlayer, baseX, baseY, baseZ,
                yaw, pitch, rangeMultiplier,
                player.level().getGameTime(), 4
        );
    }
}
