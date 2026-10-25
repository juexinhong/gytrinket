package com.gytrinket.gytrinket.client.screen;


public interface UIRenderer {
    void drawPanelBackground(GuiCanvas g, int x, int y, int w, int h);

    void drawPanelBorder(GuiCanvas g, int x, int y, int w, int h);

    /** 面板顶部标题栏（略深底 + 底部霓虹线） */
    void drawPanelHeader(GuiCanvas g, int x, int y, int w, int h);

    void drawSlot(GuiCanvas g, int x, int y, int w, int h, boolean hovered);

    void drawSelectedRow(GuiCanvas g, int x, int y, int w, int h);

    void drawAttrCell(GuiCanvas g, int x, int y, int w, int h, boolean hovered, boolean deleteMode);

    void drawScrollbarTrack(GuiCanvas g, int x, int y, int w, int h);

    void drawScrollbarThumb(GuiCanvas g, int x, int y, int w, int h);

    void drawDivider(GuiCanvas g, int x, int y, int w);

    void drawOverlayBackground(GuiCanvas g, int x, int y, int w, int h);

    void drawOverlayBorder(GuiCanvas g, int x, int y, int w, int h);

    /** 标题下方霓虹下划线 */
    void drawTitleUnderline(GuiCanvas g, int x, int y, int w);

    /** 带描边的进度条 */
    void drawProgressBar(GuiCanvas g, int x, int y, int w, int h, float progress, int fillColor);

    int getAccentColor();

    int getTextColor();

    int getValueColor();

    int getDeleteColor();

    int getHintColor();

    int getDividerColor();
}

