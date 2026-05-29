package com.gy_mod.gy_trinket.client.shield.type;

import com.gy_mod.gy_trinket.Config;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class AuraClientData {

    private static double shieldEffectRadius = 1.0;
    private static boolean damaging = false;

    private static double displayAlpha = 0;
    private static double displaySize = 0;

    private static int fadeOutTicks = 0;
    private static final int FADE_OUT_DURATION = 20;

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
        }
    }

    public static double getDisplayAlpha() {
        return displayAlpha;
    }

    public static double getDisplaySize() {
        return displaySize;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

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
        double targetSize = effectiveRadius * 2.0;

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
    }
}
