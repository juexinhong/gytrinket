package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.key.KeyInputHandler;
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

    protected void renderPanelBackground(GuiGraphics g) {
        this.renderBackground(g);
        renderer.drawPanelBackground(g, panelX, panelY, panelWidth, panelHeight);
        renderer.drawPanelBorder(g, panelX, panelY, panelWidth, panelHeight);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyInputHandler.getAttributeKey().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            Minecraft.getInstance().setScreen(null);
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
