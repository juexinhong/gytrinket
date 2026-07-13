package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.client.attack_mode.AttackModeClientUtil;
import com.gy_mod.gy_trinket.client.attack_mode.assault.AssaultInputHandler;
import com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedAttackInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 客户端 Mixin - 攻击模式兼容层
 * <p>
 * 注入原版 Minecraft 的攻击方法，在所有其他模组（如 Better Combat）之前拦截攻击输入，
 * 确保充能攻击和强袭系统无论是否安装其他战斗模组都能正常工作。
 * <p>
 * 使用较低的 Mixin priority（999）确保在 Better Combat（默认 1000）之前执行。
 */
@Mixin(value = Minecraft.class, priority = 999)
public class MinecraftClientMixin {

    /**
     * 拦截 startAttack（单次点击攻击）
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        // 瞄准方块时不拦截，允许正常挖掘
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        // 正在充能中：取消所有攻击
        if (ChargedAttackInputHandler.isCharging()) {
            cir.setReturnValue(false);
            return;
        }

        // 初始攻击拦截：拥有充能攻击物品且攻击强度满时，启动充能并取消本次攻击
        if (AttackModeClientUtil.hasChargedAttackItem()) {
            float attackStrength = player.getAttackStrengthScale(0.0F);
            if (attackStrength >= 1.0F) {
                if (!com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler
                        .isInComboCooldown(player.getUUID())) {
                    ChargedAttackInputHandler.startCharging();
                    cir.setReturnValue(false);
                }
            }
        }
    }

    /**
     * 拦截 continueAttack（长按攻击/持续挖掘）
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onContinueAttack(boolean leftClick, CallbackInfo ci) {
        // 充能期间：阻止所有攻击
        if (ChargedAttackInputHandler.isCharging()) {
            ci.cancel();
            return;
        }

        // 强袭模式：阻止 Better Combat 接管长按攻击
        if (AssaultInputHandler.isAssaultMode()) {
            ci.cancel();
        }
    }
}
