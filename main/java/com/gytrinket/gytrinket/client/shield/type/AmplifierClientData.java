package com.gytrinket.gytrinket.client.shield.type;

import com.gytrinket.gytrinket.config.Config;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

/**
 * 增幅护盾客户端显示数据
 * <p>
 * 由服务端同步的增幅进度（0~1）驱动：
 * - 仅在检测到危险物（进度>0）时渲染
 * - 亮度基础为8，随进度线性提升至15
 * - 透明度与亮度均平滑插值（淡入淡出）
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class AmplifierClientData {

    /** 亮度范围：基础8（无危险物）~ 15（达到增幅上限） */
    private static final double BRIGHTNESS_BASE = 8.0;
    private static final double BRIGHTNESS_MAX = 15.0;

    private static double shieldEffectRadius = 1.0;
    private static double targetProgress = 0;

    private static double displayProgress = 0;
    private static double displayAlpha = 0;
    private static double displaySize = 0;

    private static int fadeOutTicks = 0;
    private static final int FADE_OUT_DURATION = 20;

    // 超时机制：如果超过此tick数未收到progress>0的同步包，自动认为无危险物
    private static int progressConfirmTicks = 0;
    private static final int PROGRESS_CONFIRM_TIMEOUT = 10;

    private static final double ALPHA_LERP_SPEED = 0.15;
    private static final double SIZE_LERP_SPEED = 0.15;
    private static final double PROGRESS_LERP_SPEED = 0.15;

    private AmplifierClientData() {}

    public static void setShieldEffectRadius(double radius) {
        shieldEffectRadius = radius;
    }

    public static void setProgress(double progress) {
        targetProgress = Math.max(0.0, Math.min(1.0, progress));
        if (targetProgress > 0) {
            fadeOutTicks = 0;
            progressConfirmTicks = 0;
        }
    }

    public static double getDisplayAlpha() {
        return displayAlpha;
    }

    public static double getDisplaySize() {
        return displaySize;
    }

    /** 当前渲染亮度（8~15） */
    public static double getDisplayBrightness() {
        return BRIGHTNESS_BASE + displayProgress * (BRIGHTNESS_MAX - BRIGHTNESS_BASE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 超时检测：progress>0但长时间未收到确认包，自动设为0（视为无危险物）
        if (targetProgress > 0) {
            progressConfirmTicks++;
            if (progressConfirmTicks >= PROGRESS_CONFIRM_TIMEOUT) {
                targetProgress = 0;
            }
        }

        // 进度插值（驱动亮度）
        double progressDiff = targetProgress - displayProgress;
        if (Math.abs(progressDiff) > 0.001) {
            displayProgress += progressDiff * PROGRESS_LERP_SPEED;
            if (Math.abs(targetProgress - displayProgress) < 0.001) {
                displayProgress = targetProgress;
            }
        } else {
            displayProgress = targetProgress;
        }

        // 透明度：有危险物（进度>0）时淡入，无危险物时淡出
        double targetAlpha;
        if (displayProgress > 0.001) {
            fadeOutTicks = 0;
            targetAlpha = 1.0;
        } else {
            fadeOutTicks++;
            if (fadeOutTicks >= FADE_OUT_DURATION) {
                targetAlpha = 0.0;
            } else {
                targetAlpha = 1.0 - (double) fadeOutTicks / FADE_OUT_DURATION;
            }
        }

        double alphaDiff = targetAlpha - displayAlpha;
        if (Math.abs(alphaDiff) > 0.001) {
            displayAlpha += alphaDiff * ALPHA_LERP_SPEED;
            if (Math.abs(targetAlpha - displayAlpha) < 0.001) {
                displayAlpha = targetAlpha;
            }
        } else {
            displayAlpha = targetAlpha;
        }

        double baseRadius = Config.getAmplificationCheckRadius();
        double effectiveRadius = baseRadius * shieldEffectRadius;
        double targetSize = effectiveRadius * 2.0 * (4.0 / 2.8); // 补偿材质内容缩小至3/4

        double sizeDiff = targetSize - displaySize;
        if (Math.abs(sizeDiff) > 0.01) {
            displaySize += sizeDiff * SIZE_LERP_SPEED;
            if (Math.abs(targetSize - displaySize) < 0.01) {
                displaySize = targetSize;
            }
        } else {
            displaySize = targetSize;
        }
    }
}
