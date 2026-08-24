package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.key.KeyInputHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractPanelScreen extends Screen {

    protected final UIRenderer renderer;
    protected final Screen parentScreen;
    protected int panelX;
    protected int panelY;
    protected int panelWidth;
    protected int panelHeight;

    protected AbstractPanelScreen(Component title, Screen parentScreen, UIRenderer renderer) {
        super(title);
        this.parentScreen = parentScreen;
        this.renderer = renderer;
    }

    public Screen getParentScreen() {
        return parentScreen;
    }

    protected void initPanelSize(int maxWidth, int maxHeight, int marginX, int marginY) {
        this.panelWidth = Math.min(maxWidth, this.width - marginX);
        this.panelHeight = Math.min(maxHeight, this.height - marginY);
        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;
    }

    protected void renderPanelBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderer.drawPanelBackground(g, panelX, panelY, panelWidth, panelHeight);
        renderer.drawPanelBorder(g, panelX, panelY, panelWidth, panelHeight);
    }

    /** 绘制无阴影文字（浅色面板上避免黑阴影与字体糊在一起） */
    protected void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyInputHandler.getAttributeKey().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        // 按 E（打开背包键）时退出面板：主面板回到游戏，子面板回到父面板
        if (Minecraft.getInstance().options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}
