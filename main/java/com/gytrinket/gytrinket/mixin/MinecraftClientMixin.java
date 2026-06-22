package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil;
import com.gytrinket.gytrinket.client.attack_mode.assault.AssaultInputHandler;
import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackInputHandler;
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
 * <p>
 * 拦截逻辑：
 * - startAttack：充能攻击启动时取消攻击
 * - continueAttack：充能期间/强袭模式下阻止 Better Combat 接管长按攻击
 */
@Mixin(value = Minecraft.class, priority = 999)
public class MinecraftClientMixin {

    /**
     * 拦截 startAttack（单次点击攻击）
     * <p>
     * 在充能状态下取消攻击；如果应该启动充能，则启动充能并取消攻击。
     * 此注入在 Better Combat 的 startAttack 拦截之前执行（priority=999 < 1000），
     * 因此充能攻击的优先级高于 Better Combat 的连击系统。
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
                if (!com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler
                        .isInComboCooldown(player.getUUID())) {
                    ChargedAttackInputHandler.startCharging();
                    cir.setReturnValue(false);
                }
            }
        }
    }

    /**
     * 拦截 continueAttack（长按攻击/持续挖掘）
     * <p>
     * 1. 充能期间：阻止所有攻击行为
     * 2. 强袭模式下：阻止 Better Combat 接管长按攻击，让强袭自己的自动攻击逻辑执行
     * <p>
     * 此注入在 Better Combat 的 continueAttack 拦截之前执行。
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gytrinket$onContinueAttack(boolean leftClick, CallbackInfo ci) {
        // 充能期间：阻止所有攻击
        if (ChargedAttackInputHandler.isCharging()) {
            ci.cancel();
            return;
        }

        // 强袭模式：阻止 Better Combat 接管长按攻击
        // 强袭系统通过 AssaultInputHandler 在客户端 tick 中自行控制攻击频率和触发
        // 如果不拦截，Better Combat 的长按自动攻击会绕过强袭的攻击速度控制
        if (AssaultInputHandler.isAssaultMode()) {
            ci.cancel();
        }
    }
}
