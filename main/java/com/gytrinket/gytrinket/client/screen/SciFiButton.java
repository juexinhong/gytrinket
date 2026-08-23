package com.gytrinket.gytrinket.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 简洁科幻按钮（蓝色系）
 * <p>
 * 替代原版灰色按钮，与项目整体 UI 统一。
 */
public class SciFiButton extends Button {

    private final UIRenderer renderer;

    private SciFiButton(int x, int y, int width, int height, Component message, OnPress onPress, UIRenderer renderer) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.renderer = renderer;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int accent = renderer.getAccentColor();

        boolean hovered = this.isHoveredOrFocused();
        // 禁用态：底色、边框、文字整体变灰（如刷新点耗尽）
        if (!this.active) {
            g.fill(x, y, x + w, y + h, ThemeColors.BUTTON_DISABLED_COLOR);
            ScreenUtils.drawChamferRect(g, x, y, w, h, 4, ThemeColors.BUTTON_DISABLED_BORDER);
        } else {
            int base = hovered ? ThemeColors.BUTTON_HOVER_COLOR : ThemeColors.BUTTON_COLOR;
            // 扁平填充
            g.fill(x, y, x + w, y + h, base);
            // 切角描边（右上/左下削角，蓝青空间流动渐变）
            ScreenUtils.drawChamferRect(g, x, y, w, h, 4, accent);
        }

        Component message = this.getMessage();
        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(message);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - 8) / 2;
        int textColor = this.active ? ThemeColors.BUTTON_TEXT_COLOR : ScreenUtils.withAlpha(ThemeColors.BUTTON_TEXT_COLOR, 120);
        g.drawString(mc.font, message, textX, textY, textColor, false);
    }

    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width;
        private int height;
        private UIRenderer renderer = SolidUIRenderer.CONFIG;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder renderer(UIRenderer renderer) {
            this.renderer = renderer;
            return this;
        }

        public SciFiButton build() {
            return new SciFiButton(x, y, width, height, message, onPress, renderer);
        }
    }
}
