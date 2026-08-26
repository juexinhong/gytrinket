package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * TargetingConditions Mixin：幽灵机身隐身玩家在目标选取阶段被完全排除
 * <p>
 * 当怪物通过 TargetingConditions 寻找目标时，
 * 如果目标是拥有幽灵机身完全隐身的玩家，直接返回false，
 * 阻止怪物在任何距离（包括2格内）选取该玩家为目标。
 */
@Mixin(TargetingConditions.class)
public class TargetingConditionsMixin {

    @Inject(method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onTest(@Nullable LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player) {
            if (GhostFuselageManager.isFullyStealthed(player)) {
                cir.setReturnValue(false);
            }
        }
    }
}

