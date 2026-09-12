package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.modifier.player.movement.MovementSpeedMultiplierCache;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 让玩家的速度属性影响游泳速度
 * <p>
 * 原版水中移动输入速度硬编码 0.02（不消费 MOVEMENT_SPEED 属性，
 * 深海探索者/WATER_MOVEMENT_EFFICIENCY 只是向 getSpeed() 插值）。
 * 此处对 {@code travel} 水中分支的第一个 {@code moveRelative(float, Vec3)} 调用
 * （ordinal 0，熔岩为 1，鞘翅分支无 moveRelative）的速度参数乘以玩家速度聚合乘数。
 * 客户端为移动权威，双端均通过 MovementSpeedMultiplierCache 取同一乘数保证一致。
 */
@Mixin(LivingEntity.class)
public abstract class MovementSpeedSwimMixin {

    @ModifyArg(method = "travel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
                    ordinal = 0),
            index = 0)
    private float gytrinket$applyMovementSpeedToSwim(float speed, Vec3 travelVector) {
        if ((Object) this instanceof Player player) {
            float multiplier = MovementSpeedMultiplierCache.getMultiplier(player);
            if (multiplier != 1.0F) {
                speed *= multiplier;
            }
        }
        return speed;
    }
}
