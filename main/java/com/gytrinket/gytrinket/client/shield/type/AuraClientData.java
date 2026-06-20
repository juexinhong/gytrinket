package com.gytrinket.gytrinket.client.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class AuraClientData {

    private static double shieldEffectRadius = 1.0;
    private static boolean damaging = false;

    private static double displayAlpha = 0;
    private static double displaySize = 0;

    private static int fadeOutTicks = 0;
    private static final int FADE_OUT_DURATION = 20;

    // 超时机制：如果超过此tick数未收到damaging=true的同步包，自动认为停止伤害
    private static int damagingConfirmTicks = 0;
    private static final int DAMAGING_CONFIRM_TIMEOUT = 10;

    private static final double ALPHA_LERP_SPEED = 0.15;
    private static final double SIZE_LERP_SPEED = 0.15;

    private AuraClientData() {}

    public static void setShieldEffectRadius(double radius) {
        shieldEffectRadius = radius;
    }

    public static void setDamaging(boolean value) {
        damaging = value;
        if (value) {
            fadeOutTicks = 0;
            damagingConfirmTicks = 0;
        }
    }

    public static double getDisplayAlpha() {
        return displayAlpha;
    }

    public static double getDisplaySize() {
        return displaySize;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 超时检测：如果damaging为true但长时间未收到确认包，自动设为false
        if (damaging) {
            damagingConfirmTicks++;
            if (damagingConfirmTicks >= DAMAGING_CONFIRM_TIMEOUT) {
                damaging = false;
            }
        }

        double targetAlpha;
        if (damaging) {
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

        double baseRadius = Config.AURA_RADIUS.get();
        double effectiveRadius = baseRadius * shieldEffectRadius;
        double targetSize = effectiveRadius * 2.0 * (4.0 / 3.0); // 补偿材质内容缩小至3/4

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

    public static void reset() {
        shieldEffectRadius = 1.0;
        damaging = false;
        displayAlpha = 0;
        displaySize = 0;
        fadeOutTicks = 0;
        damagingConfirmTicks = 0;
    }
}
