package com.gy_mod.gy_trinket.mixin;

import com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedAttackInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandler Mixin - 通用长按右键充能检测
 * <p>
 * "使用物品"指的是长按右键这个动作本身，不依赖任何具体物品。
 * 在鼠标按键事件层面检测右键按下/松开：
 * - 右键按下：通知充能攻击系统开始充能
 * - 右键松开：通知充能攻击系统释放充能
 * <p>
 * HEAD 注入且不取消，原版物品使用（进食、拉弓、盾牌等）行为照常进行。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress(JIII)V", at = @At("HEAD"))
    private void gytrinket$onButton(long windowPointer, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // 未进入世界或界面打开时不处理
        if (mc.player == null || mc.screen != null) {
            return;
        }
        // 仅处理鼠标右键
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            ChargedAttackInputHandler.startChargingFromRightButton();
        } else if (action == GLFW.GLFW_RELEASE) {
            ChargedAttackInputHandler.releaseChargingFromRightButton();
        }
    }
}
