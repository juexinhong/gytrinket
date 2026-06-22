package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.network.packet.ChargedSweepParticlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player Mixin - 充能横扫增强
 * <p>
 * 1. 搜索 AABB：沿玩家视线方向前移并扩大
 * 2. 距离检查：扩大 entityInteractionRange 限制
 * 3. 横扫粒子：取消原版粒子，在自定义位置生成放大粒子
 */
@Mixin(Player.class)
public class PlayerMixin {

    /**
     * 在 attack() 开头记录主要攻击目标，用于区分直接攻击和横扫目标
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void gytrinket$recordPrimaryTarget(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        ChargedAttackSweepHandler.setPrimaryTarget(player.getUUID(), target);
    }

    /**
     * 在 attack() 结尾清除主要攻击目标记录
     */
    @Inject(method = "attack", at = @At("RETURN"))
    private void gytrinket$clearPrimaryTarget(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        ChargedAttackSweepHandler.removePrimaryTarget(player.getUUID());
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
     * 修改横扫距离检查的上限
     */
    @ModifyArg(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;square(D)D"
            )
    )
    private double gytrinket$expandSweepDistance(double value) {
        Player player = (Player) (Object) this;

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return value;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(player.getUUID());
        if (chargeValue <= 0) {
            return value;
        }

        float rangeMultiplier = ChargedAttackSweepHandler.getSweepRangeMultiplier(chargeValue);
        return value * rangeMultiplier;
    }

    /**
     * 充能横扫时：取消原版粒子，通过网络包发送自定义渲染数据到客户端
     * <p>
     * 原版 sweepAttack() 只在服务端被调用（内部通过 ServerLevel.sendParticles 发送粒子）。
     * 因此不能在客户端直接添加渲染数据，需要通过网络包从服务端发送到客户端。
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
        if (player.level().isClientSide()) {
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
        double baseY = player.getY() + player.getEyeHeight() -0.5 + lookY * distance;
        double baseZ = player.getZ() + lookZ * distance;

        // 通过网络包发送渲染数据到所有可见此玩家的客户端（包括自己）
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new ChargedSweepParticlePacket(
                baseX, baseY, baseZ,
                yaw, pitch, rangeMultiplier,
                player.level().getGameTime(), 4
        ));
    }
}
