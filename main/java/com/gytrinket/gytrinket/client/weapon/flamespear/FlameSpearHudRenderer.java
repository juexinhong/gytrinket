package com.gytrinket.gytrinket.client.weapon.flamespear;

import com.gytrinket.gytrinket.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * 焰矛HUD渲染器
 * <p>
 * 以服务端同步的充能值与充能速率为准（每3 tick同步一次），
 * 在准星右侧显示充能值数字与当前充能速率。
 */
public class FlameSpearHudRenderer {

    private static double chargeValue = 0;
    private static double chargeRate = 0;

    // 平滑显示值
    private double displayChargeValue = 0;
    private double displayChargeRate = 0;
    private static final float LERP_SPEED = 0.2f;
    private static final double LERP_THRESHOLD = 0.01;

    private static final FlameSpearHudRenderer INSTANCE = new FlameSpearHudRenderer();

    public static FlameSpearHudRenderer getInstance() {
        return INSTANCE;
    }

    private FlameSpearHudRenderer() {}

    /**
     * 设置服务端同步的充能值和当前充能速率
     */
    public static void setChargeData(double value, double rate) {
        chargeValue = value;
        chargeRate = rate;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        // 手持焰矛（或仍有残余充能值消退中）才显示
        boolean holdingSpear = false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (!mainHand.isEmpty() && Config.isFlameSpearItem(mainHand.getItem())) {
            holdingSpear = true;
        }
        if (!holdingSpear && chargeValue <= 0) {
            displayChargeValue = 0;
            displayChargeRate = 0;
            return;
        }

        // 平滑过渡
        displayChargeValue = lerp(displayChargeValue, chargeValue);
        displayChargeRate = lerp(displayChargeRate, chargeRate);

        if (displayChargeValue <= 0) {
            return;
        }

        Font font = mc.font;
        String chargeText = String.format("%.1f", displayChargeValue);
        String rateText = String.format("%.2f", displayChargeRate);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // 准星右侧显示（避免与准星下方充能攻击HUD重叠）
        int x = centerX + 8;
        int valueY = centerY - 4;
        int rateY = valueY + font.lineHeight + 1;

        // 充能值：黄色 → 橙色 → 红色；充能速率：青蓝色
        int valueColor = getChargeColor(displayChargeValue);
        guiGraphics.drawString(font, chargeText, x, valueY, valueColor, true);
        guiGraphics.drawString(font, rateText, x, rateY, 0xFF66CCFF, true);
    }

    private static double lerp(double current, double target) {
        double diff = target - current;
        if (Math.abs(diff) > LERP_THRESHOLD) {
            current += diff * LERP_SPEED;
            if (Math.abs(target - current) < 0.1) {
                current = target;
            }
        } else {
            current = target;
        }
        return current;
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
