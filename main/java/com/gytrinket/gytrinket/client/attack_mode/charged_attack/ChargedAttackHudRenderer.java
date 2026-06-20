package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 充能攻击HUD渲染器
 * <p>
 * 以服务端同步的充能值为准（每3 tick同步一次），
 * 在准星下方5像素处显示充能值数字文本。
 */
public class ChargedAttackHudRenderer {

    private static double chargeValue = 0;
    private static double chargedDamage = 0;

    // 平滑显示值
    private double displayChargeValue = 0;
    private double displayChargedDamage = 0;
    private static final float LERP_SPEED = 0.2f;
    private static final double LERP_THRESHOLD = 0.01;

    private static final ChargedAttackHudRenderer INSTANCE = new ChargedAttackHudRenderer();

    public static ChargedAttackHudRenderer getInstance() {
        return INSTANCE;
    }

    private ChargedAttackHudRenderer() {}

    /**
     * 设置服务端同步的充能值和充能伤害
     */
    public static void setChargeValue(double value, double damage) {
        chargeValue = value;
        chargedDamage = damage;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        // 不在充能状态时不渲染
        if (!ChargedAttackInputHandler.isCharging() && chargeValue <= 0) {
            displayChargeValue = 0;
            displayChargedDamage = 0;
            return;
        }

        // 平滑过渡
        double targetValue = chargeValue;
        double diff = targetValue - displayChargeValue;
        if (Math.abs(diff) > LERP_THRESHOLD) {
            displayChargeValue += diff * LERP_SPEED;
            if (Math.abs(targetValue - displayChargeValue) < 0.1) {
                displayChargeValue = targetValue;
            }
        } else {
            displayChargeValue = targetValue;
        }

        double targetDamage = chargedDamage;
        double damageDiff = targetDamage - displayChargedDamage;
        if (Math.abs(damageDiff) > LERP_THRESHOLD) {
            displayChargedDamage += damageDiff * LERP_SPEED;
            if (Math.abs(targetDamage - displayChargedDamage) < 0.1) {
                displayChargedDamage = targetDamage;
            }
        } else {
            displayChargedDamage = targetDamage;
        }

        if (displayChargeValue <= 0) {
            return;
        }

        Font font = minecraft.font;
        String chargeText = String.format("%.1f", displayChargeValue);

        // 服务端计算的充能影响下的攻击伤害
        String damageText = String.format("%.1f", displayChargedDamage);

        // 准星位置：屏幕中心
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // 准星下方5像素显示充能值
        int chargeTextX = centerX - font.width(chargeText) / 2;
        int chargeTextY = centerY + 5;

        // 充能值下方显示伤害值
        int damageTextX = centerX - font.width(damageText) / 2;
        int damageTextY = chargeTextY + font.lineHeight + 2;

        // 颜色：黄色 → 橙色 → 红色
        int color = getChargeColor(displayChargeValue);
        guiGraphics.drawString(font, chargeText, chargeTextX, chargeTextY, color, true);

        // 伤害值颜色：白色
        int damageColor = 0xFFCCCCCC;
        guiGraphics.drawString(font, damageText, damageTextX, damageTextY, damageColor, true);
    }

    /**
     * 根据充能值获取颜色
     * 低充能：黄色，中等：橙色，高充能：红色
     */
    private int getChargeColor(double value) {
        float ratio = (float) Math.min(value / 50.0, 1.0);
        int r = 255;
        int g = (int) (255 * (1.0f - ratio * 0.7f));
        int b = (int) (50 * (1.0f - ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
