package com.gy_mod.gy_trinket.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class UpgradeTargetScreen extends AbstractPanelScreen {

    private final ListTag upgradeTargets;
    private final ScrollBarComponent scrollBar = new ScrollBarComponent(2, 6);

    private int hoveredTargetIndex = -1;

    public UpgradeTargetScreen(Screen parentScreen, ListTag upgradeTargets) {
        super(Component.translatable("screen.gytrinket.upgrade_target"), parentScreen, SolidUIRenderer.UPGRADE);
        this.upgradeTargets = upgradeTargets != null ? upgradeTargets : new ListTag();
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(220, 200, 20, 40);

        int btnX = panelX + panelWidth / 2 - 40;
        int btnY = panelY + panelHeight + 5;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(btnX, btnY, 80, 16).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollBar.mouseScrolled(delta);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderPanelBackground(guiGraphics);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_target_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_target_hint").getString(),
                panelX + 8, panelY + 18, renderer.getHintColor());

        int contentTopY = panelY + 32;
        int contentBottomY = panelY + panelHeight - 5;
        int rowHeight = 28;
        int totalContentHeight = upgradeTargets.size() * rowHeight;
        int visibleHeight = contentBottomY - contentTopY;
        scrollBar.updateMaxScroll(totalContentHeight, visibleHeight);
        int scrollOffset = scrollBar.getScrollOffset();

        hoveredTargetIndex = -1;

        guiGraphics.enableScissor(panelX + 1, contentTopY, panelX + panelWidth - 1, contentBottomY);

        for (int i = 0; i < upgradeTargets.size(); i++) {
            CompoundTag targetTag = upgradeTargets.getCompound(i);
            String baseItemKey = targetTag.getString("baseItemKey");
            String upgradedItemKey = targetTag.getString("upgradedItemKey");

            Item baseItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(baseItemKey));
            Item upgradedItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(upgradedItemKey));
            if (baseItem == null || upgradedItem == null) continue;

            int rowY = contentTopY + i * rowHeight - scrollOffset;

            boolean hovered = mouseX >= panelX + 5 && mouseX < panelX + panelWidth - 5
                    && mouseY >= rowY && mouseY < rowY + rowHeight
                    && mouseY >= contentTopY && mouseY < contentBottomY;
            if (hovered) {
                hoveredTargetIndex = i;
            }
            renderer.drawSlot(guiGraphics, panelX + 5, rowY, panelWidth - 10, rowHeight, hovered);

            guiGraphics.renderItem(new ItemStack(baseItem), panelX + 10, rowY + 6);

            String baseName = new ItemStack(baseItem).getHoverName().getString();
            String upgradedName = new ItemStack(upgradedItem).getHoverName().getString();
            guiGraphics.drawString(font, baseName + " -> " + upgradedName, panelX + 28, rowY + 4, renderer.getTextColor());

            ListTag ingredients = targetTag.getList("ingredients", 10);
            int totalRequired = 0;
            int totalCollected = 0;
            for (int j = 0; j < ingredients.size(); j++) {
                CompoundTag ing = ingredients.getCompound(j);
                totalRequired += ing.getInt("required");
                totalCollected += ing.getInt("collected");
            }
            String progress = totalCollected + "/" + totalRequired;
            int progressColor = totalCollected >= totalRequired ? renderer.getValueColor() : 0xFFAAAAAA;
            guiGraphics.drawString(font, progress, panelX + panelWidth - 15 - font.width(progress), rowY + 4, progressColor);

            StringBuilder ingSummary = new StringBuilder();
            for (int j = 0; j < ingredients.size(); j++) {
                CompoundTag ing = ingredients.getCompound(j);
                String itemKey = ing.getString("itemKey");
                int required = ing.getInt("required");
                int collected = ing.getInt("collected");
                Item ingItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemKey));
                String ingName = ingItem != null ? new ItemStack(ingItem).getHoverName().getString() : itemKey;
                if (j > 0) ingSummary.append(" ");
                ingSummary.append(ingName).append(":").append(collected).append("/").append(required);
            }
            String summaryText = ingSummary.toString();
            if (font.width(summaryText) > panelWidth - 40) {
                summaryText = font.plainSubstrByWidth(summaryText, panelWidth - 45) + "...";
            }
            guiGraphics.drawString(font, summaryText, panelX + 28, rowY + 16, renderer.getHintColor());
        }

        guiGraphics.disableScissor();

        if (upgradeTargets.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_upgrade_targets").getString(),
                    panelX + 15, contentTopY, renderer.getHintColor());
        }

        int scrollBarX = panelX + panelWidth - 4;
        scrollBar.render(guiGraphics, renderer, scrollBarX, contentTopY, contentBottomY - contentTopY, visibleHeight, totalContentHeight);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int contentTopY = panelY + 32;
        int contentBottomY = panelY + panelHeight - 5;
        int rowHeight = 28;
        int totalContentHeight = upgradeTargets.size() * rowHeight;
        int visibleHeight = contentBottomY - contentTopY;
        int scrollBarX = panelX + panelWidth - 4;

        if (button == 0 && scrollBar.mouseClicked(mouseX, mouseY, scrollBarX, contentTopY, contentBottomY - contentTopY, visibleHeight, totalContentHeight)) {
            return true;
        }

        if (button == 0 && hoveredTargetIndex >= 0 && hoveredTargetIndex < upgradeTargets.size()) {
            CompoundTag selectedTarget = upgradeTargets.getCompound(hoveredTargetIndex);
            String baseItemKey = selectedTarget.getString("baseItemKey");
            String upgradedItemKey = selectedTarget.getString("upgradedItemKey");
            ListTag ingredients = selectedTarget.getList("ingredients", 10);
            Minecraft.getInstance().setScreen(new UpgradeSelectScreen(parentScreen, baseItemKey, upgradedItemKey, ingredients));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int contentTopY = panelY + 32;
        int contentBottomY = panelY + panelHeight - 5;
        int rowHeight = 28;
        int totalContentHeight = upgradeTargets.size() * rowHeight;
        int visibleHeight = contentBottomY - contentTopY;
        if (scrollBar.mouseDragged(mouseY, contentTopY, contentBottomY - contentTopY, visibleHeight, totalContentHeight)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBar.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
