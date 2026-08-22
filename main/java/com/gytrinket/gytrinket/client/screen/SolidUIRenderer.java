package com.gytrinket.gytrinket.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class SolidUIRenderer implements UIRenderer {

    private final int bgColor;
    private final int borderColor;
    private final int slotColor;
    private final int slotHoverColor;
    private final int selectedRowColor;
    private final int attrCellColor;
    private final int attrCellHoverColor;
    private final int attrDeleteHoverColor;
    private final int scrollbarTrackColor;
    private final int scrollbarThumbColor;
    private final int dividerColor;
    private final int accentColor;
    private final int textColor;
    private final int valueColor;
    private final int deleteColor;
    private final int hintColor;

    private SolidUIRenderer(Builder b) {
        this.bgColor = b.bgColor;
        this.borderColor = b.borderColor;
        this.slotColor = b.slotColor;
        this.slotHoverColor = b.slotHoverColor;
        this.selectedRowColor = b.selectedRowColor;
        this.attrCellColor = b.attrCellColor;
        this.attrCellHoverColor = b.attrCellHoverColor;
        this.attrDeleteHoverColor = b.attrDeleteHoverColor;
        this.scrollbarTrackColor = b.scrollbarTrackColor;
        this.scrollbarThumbColor = b.scrollbarThumbColor;
        this.dividerColor = b.dividerColor;
        this.accentColor = b.accentColor;
        this.textColor = b.textColor;
        this.valueColor = b.valueColor;
        this.deleteColor = b.deleteColor;
        this.hintColor = b.hintColor;
    }

    @Override
    public void drawPanelBackground(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, bgColor, ScreenUtils.darken(bgColor, 0.10f));
    }

    @Override
    public void drawPanelHeader(GuiGraphics g, int x, int y, int w, int h) {
        // 仅标题下流动霓虹线，无背景，避免遮挡面板切角边框
        ScreenUtils.flowingHLine(g, x + 4, y + h - 1, w - 8, accentColor, System.currentTimeMillis());
    }

    @Override
    public void drawPanelBorder(GuiGraphics g, int x, int y, int w, int h) {
        // 外发光（低 alpha，随流动色变化）
        int glow = ScreenUtils.withAlpha(ScreenUtils.flowingColor(borderColor), 40);
        g.fill(x - 1, y - 1, x + w + 1, y, glow);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, glow);
        g.fill(x - 1, y - 1, x, y + h + 1, glow);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, glow);
        // 主边框（右上/左下切角，边内空间流动渐变）
        ScreenUtils.drawChamferRect(g, x, y, w, h, 4, borderColor);
    }

    @Override
    public void drawSlot(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? slotHoverColor : slotColor);
        if (hovered) {
            ScreenUtils.drawChamferRect(g, x, y, w, h, 2, accentColor);
        } else {
            // 常驻淡描边（流动色）
            ScreenUtils.drawChamferRect(g, x, y, w, h, 2, ScreenUtils.withAlpha(accentColor, 55));
        }
    }

    @Override
    public void drawSelectedRow(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, selectedRowColor);
        g.fill(x, y, x + 2, y + h, ScreenUtils.flowingColor(accentColor));
    }

    @Override
    public void drawAttrCell(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean deleteMode) {
        int color = attrCellColor;
        if (deleteMode && hovered) color = attrDeleteHoverColor;
        else if (hovered) color = attrCellHoverColor;
        g.fill(x, y, x + w, y + h, color);
        if (hovered && !deleteMode) {
            g.fill(x, y, x + 1, y + h, ScreenUtils.flowingColor(accentColor));
        } else if (deleteMode && hovered) {
            g.fill(x, y, x + 1, y + h, deleteColor);
        }
    }

    @Override
    public void drawScrollbarTrack(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, scrollbarTrackColor);
    }

    @Override
    public void drawScrollbarThumb(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, ScreenUtils.lighten(scrollbarThumbColor, 0.2f), scrollbarThumbColor);
    }

    @Override
    public void drawDivider(GuiGraphics g, int x, int y, int w) {
        ScreenUtils.flowingHLine(g, x, y, w, ScreenUtils.withAlpha(dividerColor, 200), System.currentTimeMillis());
    }

    @Override
    public void drawOverlayBackground(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h,
                ScreenUtils.withAlpha(bgColor, 245),
                ScreenUtils.withAlpha(ScreenUtils.darken(bgColor, 0.1f), 245));
    }

    @Override
    public void drawOverlayBorder(GuiGraphics g, int x, int y, int w, int h) {
        ScreenUtils.drawChamferRect(g, x, y, w, h, 4, borderColor);
    }

    @Override
    public void drawTitleUnderline(GuiGraphics g, int x, int y, int w) {
        long time = System.currentTimeMillis();
        ScreenUtils.flowingHLine(g, x, y, w, accentColor, time);
        ScreenUtils.flowingHLine(g, x, y + 1, w, ScreenUtils.withAlpha(accentColor, 80), time);
    }

    @Override
    public void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int fillColor) {
        g.fill(x, y, x + w, y + h, 0xFF0E1524);
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        int fillWidth = (int) (w * p);
        if (fillWidth > 0) {
            g.fillGradient(x, y, x + fillWidth, y + h, ScreenUtils.lighten(fillColor, 0.3f), fillColor);
        }
        ScreenUtils.drawBorder(g, x, y, w, h, ScreenUtils.withAlpha(fillColor, 160));
    }

    @Override
    public int getAccentColor() { return accentColor; }

    @Override
    public int getTextColor() { return textColor; }

    @Override
    public int getValueColor() { return valueColor; }

    @Override
    public int getDeleteColor() { return deleteColor; }

    @Override
    public int getHintColor() { return hintColor; }

    @Override
    public int getDividerColor() { return dividerColor; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int bgColor = ThemeColors.BG_COLOR;
        private int borderColor = ThemeColors.BORDER_COLOR;
        private int slotColor = ThemeColors.SLOT_COLOR;
        private int slotHoverColor = ThemeColors.SLOT_HOVER_COLOR;
        private int selectedRowColor = ThemeColors.SELECTED_ROW_COLOR;
        private int attrCellColor = ThemeColors.ATTR_CELL_COLOR;
        private int attrCellHoverColor = ThemeColors.ATTR_CELL_HOVER;
        private int attrDeleteHoverColor = ThemeColors.ATTR_DELETE_HOVER;
        private int scrollbarTrackColor = ThemeColors.SCROLLBAR_TRACK_COLOR;
        private int scrollbarThumbColor = ThemeColors.SCROLLBAR_THUMB_COLOR;
        private int dividerColor = ThemeColors.DIVIDER_COLOR;
        private int accentColor = ThemeColors.ACCENT_COLOR;
        private int textColor = ThemeColors.TEXT_COLOR;
        private int valueColor = ThemeColors.VALUE_COLOR;
        private int deleteColor = ThemeColors.DELETE_COLOR;
        private int hintColor = ThemeColors.HINT_COLOR;

        public Builder bgColor(int v) { bgColor = v; return this; }
        public Builder borderColor(int v) { borderColor = v; return this; }
        public Builder slotColor(int v) { slotColor = v; return this; }
        public Builder slotHoverColor(int v) { slotHoverColor = v; return this; }
        public Builder selectedRowColor(int v) { selectedRowColor = v; return this; }
        public Builder attrCellColor(int v) { attrCellColor = v; return this; }
        public Builder attrCellHoverColor(int v) { attrCellHoverColor = v; return this; }
        public Builder attrDeleteHoverColor(int v) { attrDeleteHoverColor = v; return this; }
        public Builder scrollbarTrackColor(int v) { scrollbarTrackColor = v; return this; }
        public Builder scrollbarThumbColor(int v) { scrollbarThumbColor = v; return this; }
        public Builder dividerColor(int v) { dividerColor = v; return this; }
        public Builder accentColor(int v) { accentColor = v; return this; }
        public Builder textColor(int v) { textColor = v; return this; }
        public Builder valueColor(int v) { valueColor = v; return this; }
        public Builder deleteColor(int v) { deleteColor = v; return this; }
        public Builder hintColor(int v) { hintColor = v; return this; }

        public SolidUIRenderer build() { return new SolidUIRenderer(this); }
    }

    public static final SolidUIRenderer CONFIG = SolidUIRenderer.builder().build();

    public static final SolidUIRenderer PANEL = SolidUIRenderer.builder()
            .bgColor(ThemeColors.PANEL_BG_COLOR)
            .borderColor(ThemeColors.PANEL_BORDER_COLOR)
            .slotColor(ThemeColors.PANEL_SLOT_COLOR)
            .slotHoverColor(ThemeColors.PANEL_SLOT_HOVER_COLOR)
            .accentColor(ThemeColors.PANEL_ACCENT_COLOR)
            .scrollbarTrackColor(ThemeColors.PANEL_SCROLLBAR_TRACK)
            .scrollbarThumbColor(ThemeColors.PANEL_SCROLLBAR_THUMB)
            .dividerColor(ThemeColors.DIVIDER_COLOR)
            .build();

    public static final SolidUIRenderer UPGRADE = SolidUIRenderer.builder()
            .borderColor(ThemeColors.UPGRADE_BORDER_COLOR)
            .slotColor(ThemeColors.UPGRADE_SLOT_COLOR)
            .slotHoverColor(ThemeColors.UPGRADE_SLOT_HOVER_COLOR)
            .accentColor(ThemeColors.UPGRADE_ACCENT_COLOR)
            .build();
}
