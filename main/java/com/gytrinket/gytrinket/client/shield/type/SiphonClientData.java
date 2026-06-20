package com.gytrinket.gytrinket.client.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class SiphonClientData {

    private static int targetStacks = 0;
    private static double shieldEffectRadius = 1.0;
    private static int[] protectedEntityIds = new int[0];

    private static double displayStacks = 0;
    private static double displayAlpha = 0;
    private static double displaySize = 0;

    private static final double LERP_SPEED = 0.15;

    private SiphonClientData() {}

    public static void setSiphonStacks(int stacks) {
        targetStacks = stacks;
    }

    public static void setShieldEffectRadius(double radius) {
        shieldEffectRadius = radius;
    }

    public static void setProtectedEntityIds(int[] ids) {
        protectedEntityIds = ids != null ? ids : new int[0];
    }

    public static int[] getProtectedEntityIds() {
        return protectedEntityIds;
    }

    public static int getTargetStacks() {
        return targetStacks;
    }

    public static double getDisplayStacks() {
        return displayStacks;
    }

    public static double getDisplayAlpha() {
        return displayAlpha;
    }

    public static double getDisplaySize() {
        return displaySize;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        double targetDisplayStacks = targetStacks;

        double diff = targetDisplayStacks - displayStacks;
        if (Math.abs(diff) > 0.01) {
            displayStacks += diff * LERP_SPEED;
            if (Math.abs(targetDisplayStacks - displayStacks) < 0.01) {
                displayStacks = targetDisplayStacks;
            }
        } else {
            displayStacks = targetDisplayStacks;
        }

        if (displayStacks <= 0) {
            displayAlpha = 0;
            displaySize = 0;
            return;
        }

        double baseAlpha = 0.05;
        double alphaPerStack = 0.10;
        double targetAlpha = baseAlpha + displayStacks * alphaPerStack;
        targetAlpha = Math.min(targetAlpha, 1.0);

        double alphaDiff = targetAlpha - displayAlpha;
        if (Math.abs(alphaDiff) > 0.001) {
            displayAlpha += alphaDiff * LERP_SPEED;
            if (Math.abs(targetAlpha - displayAlpha) < 0.001) {
                displayAlpha = targetAlpha;
            }
        } else {
            displayAlpha = targetAlpha;
        }

        double baseRadius = Config.SIPHON_RADIUS.get();
        double effectiveRadius = baseRadius * shieldEffectRadius;
        double targetSize = effectiveRadius * 2.0 * (4.0 / 3.0); // 补偿材质内容缩小至3/4

        double sizeDiff = targetSize - displaySize;
        if (Math.abs(sizeDiff) > 0.01) {
            displaySize += sizeDiff * LERP_SPEED;
            if (Math.abs(targetSize - displaySize) < 0.01) {
                displaySize = targetSize;
            }
        } else {
            displaySize = targetSize;
        }
    }

    public static void reset() {
        targetStacks = 0;
        shieldEffectRadius = 1.0;
        protectedEntityIds = new int[0];
        displayStacks = 0;
        displayAlpha = 0;
        displaySize = 0;
    }
}
