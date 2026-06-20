package com.gytrinket.gytrinket.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public interface UIRenderer {
    void drawPanelBackground(GuiGraphics g, int x, int y, int w, int h);

    void drawPanelBorder(GuiGraphics g, int x, int y, int w, int h);

    void drawSlot(GuiGraphics g, int x, int y, int w, int h, boolean hovered);

    void drawSelectedRow(GuiGraphics g, int x, int y, int w, int h);

    void drawAttrCell(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean deleteMode);

    void drawScrollbarTrack(GuiGraphics g, int x, int y, int w, int h);

    void drawScrollbarThumb(GuiGraphics g, int x, int y, int w, int h);

    void drawDivider(GuiGraphics g, int x, int y, int w);

    void drawOverlayBackground(GuiGraphics g, int x, int y, int w, int h);

    void drawOverlayBorder(GuiGraphics g, int x, int y, int w, int h);

    int getAccentColor();

    int getTextColor();

    int getValueColor();

    int getDeleteColor();

    int getHintColor();

    int getDividerColor();
}
