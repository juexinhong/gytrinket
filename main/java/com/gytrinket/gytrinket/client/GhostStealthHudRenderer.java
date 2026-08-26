package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackHudRenderer;
import com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 幽灵隐身进度HUD渲染器
 * <p>
 * 在准星附近以文本显示当前玩家的隐身进度（百分比）。
 * 位置规则：
 * - 无充能HUD时：显示在充能HUD的位置（准星下方5像素）
 * - 充能HUD可见时：显示在充能HUD（充能值+伤害值两行）下方
 * - 隐身进度为0时不显示
 */
public class GhostStealthHudRenderer {

    /** 文本颜色：亮蓝（主题色） */
    private static final int TEXT_COLOR = 0xFF4AA8FF;

    private static final GhostStealthHudRenderer INSTANCE = new GhostStealthHudRenderer();

    public static GhostStealthHudRenderer getInstance() {
        return INSTANCE;
    }

    private GhostStealthHudRenderer() {}

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        float progress = GhostFuselageClientData.getStealthProgress(minecraft.player.getId());
        if (progress <= 0.001f) {
            return;
        }

        Font font = minecraft.font;
        String text = String.format("隐身 %d%%", (int) (progress * 100.0f));

        // 准星位置：屏幕中心
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        int textX = centerX - font.width(text) / 2;
        int textY;
        if (ChargedAttackHudRenderer.isHudVisible()) {
            // 充能HUD可见：显示在其下方（充能值行 centerY+5 + 伤害值行，各占 lineHeight+2 间距）
            textY = centerY + 5 + 2 * (font.lineHeight + 2);
        } else {
            // 无充能HUD：显示在充能HUD的位置（准星下方5像素）
            textY = centerY + 5;
        }

        guiGraphics.drawString(font, text, textX, textY, TEXT_COLOR, true);
    }
}
